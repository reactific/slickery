/**********************************************************************************************************************
 *                                                                                                                    *
 * Copyright (c) 2013, Reactific Software LLC. All Rights Reserved.                                                   *
 *                                                                                                                    *
 * Scrupal is free software: you can redistribute it and/or modify it under the terms                                 *
 * of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License,   *
 * or (at your option) any later version.                                                                             *
 *                                                                                                                    *
 * Scrupal is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more      *
 * details.                                                                                                           *
 *                                                                                                                    *
 * You should have received a copy of the GNU General Public License along with Scrupal. If not, see either:          *
 * http://www.gnu.org/licenses or http://opensource.org/licenses/GPL-3.0.                                             *
 **********************************************************************************************************************/
package com.reactific.slickery

import java.io.File
import java.time.Instant

import com.reactific.helpers.LoggingHelper
import com.typesafe.config.{ConfigFactory, Config}
import org.h2.jdbc.JdbcSQLException
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import slick.dbio
import slick.lifted.ProvenShape

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

/** Test cases for components */
class SchemaSpec extends Specification with LoggingHelper {

  final val baseDir = "./target/testdb"

  def testDbConfig(name : String) : Config = {
    ConfigFactory.parseString(
      s"""$name {
         |  driver = "slick.driver.H2Driver$$"
         |  db {
         |    connectionPool = disabled
         |    driver = "org.h2.Driver"
         |    url = "jdbc:h2:$baseDir/$name"
         |  }
         |}""".stripMargin)
  }

  def testdb[S](name : String)(c: (String) => S)(f: (S) => Result ) = {
    try {
      f(c(name))
    } finally {
      for (x <- Seq(".mv.db", ".trace.db")) {
        val f = new File(baseDir, name + x)
        val result = f.delete()
        log.debug(s"Deleting ${f.getCanonicalPath} returned $result")
      }
    }
  }



  case class Foo(id: Int, name: String)

  case class TestSchema(name : String) extends Schema("test", name, testDbConfig(name)) {

    import dbConfig.db
    import dbConfig.driver.api._

    class TestTableRow(tag: Tag) extends TableRow[Foo](tag, "test") {
      /** The ID column, which is the primary key, and auto incremented */
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      /** The name column */
      def name = column[String]("name")

      override def * : ProvenShape[Foo] = {
        (id, name) <> ((Foo.apply _).tupled, Foo.unapply )
      }
    }

    object foos extends TableQuery[TestTableRow]( new TestTableRow(_))  {
      def create(entity: Foo) : Future[Int] = db.run { this.insertOrUpdate(entity) }
      def retrieve(id : Int)  : Future[Option[Foo]] = db.run { this.findBy(_.id).applied(id).result.headOption }
      def update(entity: Foo) : Future[Int] = db.run { this.insertOrUpdate(entity) }
      def delete(id: Int)     : Future[Int] = db.run { this.findBy(_.id).applied(id).delete }
      val findByName = this.findBy(_.name)
    }
    def schemas = foos.schema
  }

  "Component" should {
    "fail when there's no schema" in testdb("no_schema")(name => new TestSchema(name)) { schema : TestSchema =>
      val future = Await.ready(schema.foos.retrieve(0), 5.second)
      future.isCompleted must beTrue
      future.value match {
        case Some(Failure(x: JdbcSQLException)) if x.getMessage.contains("not found") => success
        case Some(Failure(x: JdbcSQLException)) => failure("wrong exception")
        case _ => failure("Should fail")
      }
    }

    "succeed when there is a schema" in testdb("with_schema")(name => new TestSchema(name)) { schema : TestSchema =>
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val retrieve_future = schema.foos.retrieve(0)
      Await.result(retrieve_future, 5.second) match {
        case Some(x: Foo) => failure("There should be no value")
        case None => success
        case _ => failure("Should return None")
      }
    }

    case class TestUseable(id : Long, created : Option[Instant] = None, modified: Option[Instant] = None,
      expired: Option[Instant] = None, name: String = "", description: String = "") extends Useable {
    }

    case class TraitsSchema(name : String) extends Schema("test", name, testDbConfig(name)) {
      import dbConfig.driver.api._
      class TraitsRow(tag : Tag) extends UseableRow[TestUseable](tag, "TestInfo"){
        def * = (id,created,modified,expired,name,description) <> (TestUseable.tupled, TestUseable.unapply)
      }
      object testInfos extends UseableQuery[TestUseable, TraitsRow]( new TraitsRow(_))
      def schemas = testInfos.schema
    }

    "allow table traits to be combined" in testdb("combine_traits")(name => new TraitsSchema(name)) {
      schema: TraitsSchema =>
        import schema.dbConfig.db
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val res = db.run { schema.testInfos.byId(0L) } map {
          testInfo : Option[TestUseable] =>
            asResult(testInfo must beNone)
        }
        Await.result(res, 5.seconds)
    }

    case class CorrelationSchema(name : String) extends Schema("test", name, testDbConfig(name)) {
      import dbConfig.driver.api._
      class ARow(tag:Tag) extends UseableRow[TestUseable](tag, "As") {
        def * = (id,created,modified,expired,name,description) <> (TestUseable.tupled, TestUseable.unapply)
      }
      class BRow(tag:Tag) extends UseableRow[TestUseable](tag, "Bs") {
        def * = (id,created,modified,expired,name,description) <> (TestUseable.tupled, TestUseable.unapply)
      }
      object As extends UseableQuery[TestUseable,ARow]( new ARow(_))
      object Bs extends UseableQuery[TestUseable,BRow]( new BRow(_))
      class A2BRow(tag : Tag) extends ManyToManyRow[TestUseable,ARow,TestUseable,BRow](tag, "A2B", "A", As, "B", Bs)
      object A2B extends ManyToManyQuery[TestUseable,ARow,TestUseable,BRow,A2BRow]( new A2BRow(_))
      def schemas = As.schema ++ Bs.schema ++ A2B.schema
    }

    "provide correlation tables" in testdb("correlation")(name => new CorrelationSchema(name)) {
      schema : CorrelationSchema =>
        import schema.dbConfig.db
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val t1 = TestUseable(1,name="one"); val t2 = TestUseable(2,name="two"); val t3 = TestUseable(3,name="three")
        val res = db.run {
          dbio.DBIO.seq(
            schema.As.create(t1),
            schema.As.create(t2),
            schema.As.create(t3),
            schema.Bs.create(t1),
            schema.Bs.create(t2),
            schema.Bs.create(t3),
            schema.A2B.associate(t1,t2),
            schema.A2B.associate(t1,t3),
            schema.A2B.associate(t2,t3)
          ).flatMap { u : Unit =>
            schema.A2B.findAssociatedB(1).map { s : Seq[Long] => asResult( s must beEqualTo( Vector(2,3) ) ) }
          }
        }
        Await.result(res,5.seconds)
        val res2 = db. run {
          schema.As.delete(t1.id).flatMap { n =>
            n must beEqualTo(1)
            schema.A2B.findAssociatedB(1).map { s : Seq[Long] => asResult ( s must beEqualTo( Vector.empty[Long] )) }
          }
        }
        Await.result(res2, 5.seconds)
        val res3 = db.run {
          schema.A2B.findAssociatedB(2).map { s : Seq[Long] => asResult ( s must beEqualTo( Vector(3) ) ) }
        }
        Await.result(res3, 5.seconds)
    }
  }
}
