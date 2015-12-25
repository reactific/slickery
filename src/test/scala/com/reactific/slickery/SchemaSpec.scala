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

import java.time.Instant

import com.typesafe.config.{ConfigFactory, Config}
import org.h2.jdbc.JdbcSQLException
import org.specs2.matcher.MatchResult
import play.api.libs.json.{JsValue, JsObject}
import slick.dbio
import slick.lifted.ProvenShape

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.matching.Regex

/** Test cases for components */
class SchemaSpec extends SlickerySpec {

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

    "validate empty database" in testdb("validate_empty")(name => new TestSchema(name)) { schema: TestSchema =>
      val future = schema.validate()
      Await.result(future, 5.seconds).isEmpty must beTrue
    }


    "throw on bad db config" in {
      def badDbConfig(name : String) : Config = {
        ConfigFactory.parseString(
          s"""$name {
             |  driver = "slick.driver.FahrenVergnugen$$"
             |  db {
             |    connectionPool = disabled
             |    driver = "org.fv.Driver"
             |    url = "jdbc:fv:$baseDir/$name"
             |  }
             |}""".stripMargin)
      }
      val schema = FakeSchema("junk",badDbConfig("junk"))
      schema.dbConfig.driverIsObject must throwA[slick.SlickException]
    }

    "throw on unsupported driver" in {
      def testDbConfig(name : String) : Config = {
        ConfigFactory.parseString(
          s"""$name {
             |  driver = "slick.driver.DerbyDriver$$"
             |  db {
             |    connectionPool = disabled
             |    driver = "org.apache.derby.jdbc.EmbeddedDriver"
             |    url = "jdbc:derby:$baseDir/$name"
             |  }
             |}""".stripMargin)
      }
      val schema = FakeSchema("nodriver",testDbConfig("nodriver"))
      val future = schema.create()
      future.isCompleted must beTrue
      Await.result(future, 1.seconds) must throwA[java.sql.SQLException]
    }

    "validate non-empty database" in testdb("validate_nonempty")(name => new TestSchema(name)) { schema: TestSchema =>
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val metatables = Await.result(schema.metaTables(),5.seconds)
      val test_exists = metatables.exists {
        table => table.name.name == "test"
      }
      test_exists must beTrue
      val future = schema.validate()
      Await.result(future, 5.seconds).isEmpty must beTrue
    }

    "fail when there's no schema" in testdb("no_schema")(name => new TestSchema(name)) { schema : TestSchema =>
      val future = Await.ready(schema.foos.retrieve(0), 5.second)
      future.isCompleted must beTrue
      future.value match {
        case Some(Failure(x: JdbcSQLException)) if x.getMessage.contains("not found") => success
        case Some(Failure(x: JdbcSQLException)) => failure("wrong exception")
        case _ => failure("Should fail")
      }
    }

    "supports dropping schema" in testdb("drop_schema")(name => new TestSchema(name)) { schema : TestSchema =>
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val drop_future = schema.drop()
      Await.result(drop_future, 5.seconds)
      val metatables = Await.result(schema.metaTables(), 5.seconds)
      val test_exists = metatables.exists { table => table.name.name == "test" }
      test_exists must beFalse
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

    case class TestUsable(oid : Option[Long], created : Option[Instant] = None, modified: Option[Instant] = None,
      expired: Option[Instant] = None, name: String = "", description: String = "") extends Useable {
    }

    case class TraitsSchema(name : String) extends Schema("test", name, testDbConfig(name)) {
      import dbConfig.driver.api._
      class TraitsRow(tag : Tag) extends UseableRow[TestUsable](tag, "TestInfo"){
        def * = (id.?,created,modified,expired,name,description) <> (TestUsable.tupled, TestUsable.unapply)
      }
      object testInfos extends UseableQuery[TestUsable, TraitsRow]( new TraitsRow(_))
      def schemas = testInfos.schema
    }

    "allow table traits to be combined" in testdb("combine_traits")(name => new TraitsSchema(name)) {
      schema: TraitsSchema =>
        import schema.dbConfig.db
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val res = db.run {
          schema.testInfos.byId(0L).map { testInfo => testInfo must beNone }
          schema.testInfos.byName("").map { testInfo => testInfo.nonEmpty must beTrue }
          schema.testInfos.byDescription("").map { testInfo => testInfo.nonEmpty must beTrue}
          schema.testInfos.expiredSince(Instant.now()).map { s => s.isEmpty must beTrue }
          schema.testInfos.modifiedSince(Instant.now()).map { s => s.isEmpty must beTrue }
          schema.testInfos.createdSince(Instant.now()).map { s => s.isEmpty must beTrue }
        } map {
          r : MatchResult[Boolean] => asResult(r)
        }
        Await.result(res, 5.seconds)
    }

    "support CRUD operations" in testdb("crud_operations")(name => new TraitsSchema(name)) { schema: TraitsSchema =>
      import schema.dbConfig.db
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val t1 = TestUsable(None, name="one")
      val id = Await.result( db.run {schema.testInfos.create(t1) }, 5.seconds)
      id must beEqualTo(1)
      Await.result(db.run {
        val t2 = TestUsable(Some(id), name="two", description="foo")
        schema.testInfos.update(t2).map { count => count must beEqualTo(1) }
      }, 5.seconds)
      Await.result(db.run {
        schema.testInfos.retrieve(id)
      } map { tu => tu match {
        case None => failure("not found");
        case Some(tu: TestUsable) => asResult(tu.description must beEqualTo("foo"))
      }}, 5.seconds)
      Await.result(db.run {
        schema.testInfos.delete(id)
      } map { count => count must beEqualTo(1) }, 5.seconds)
    }

    case class CorrelationSchema(name : String) extends Schema("test", name, testDbConfig(name)) {
      import dbConfig.driver.api._
      class ARow(tag:Tag) extends UseableRow[TestUsable](tag, "As") {
        def * = (id.?,created,modified,expired,name,description) <> (TestUsable.tupled, TestUsable.unapply)
      }
      class BRow(tag:Tag) extends UseableRow[TestUsable](tag, "Bs") {
        def * = (id.?,created,modified,expired,name,description) <> (TestUsable.tupled, TestUsable.unapply)
      }
      object As extends UseableQuery[TestUsable,ARow]( new ARow(_))
      object Bs extends UseableQuery[TestUsable,BRow]( new BRow(_))
      class A2BRow(tag : Tag) extends ManyToManyRow[TestUsable,ARow,TestUsable,BRow](tag, "A2B", "A", As, "B", Bs)
      object A2B extends ManyToManyQuery[TestUsable,ARow,TestUsable,BRow,A2BRow]( new A2BRow(_))
      def schemas = As.schema ++ Bs.schema ++ A2B.schema
    }

    "provide correlation tables" in testdb("correlation")(name => new CorrelationSchema(name)) {
      schema : CorrelationSchema =>
        import schema.dbConfig.db
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val t1 = TestUsable(Some(1), name="one")
        val t2 = TestUsable(Some(2), name="two")
        val t3 = TestUsable(Some(3), name="three")
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
            schema.A2B.findAssociatedB(t1).map { s : Seq[Long] => asResult( s must beEqualTo( Vector(2,3) ) ) }
            schema.A2B.findAssociatedA(3).map { s : Seq[Long] => asResult( s must beEqualTo( Vector(1,2) ) ) }
            schema.A2B.findAssociatedA(t3).map { s : Seq[Long] => asResult( s must beEqualTo( Vector(1,2) ) ) }
          }
        }
        Await.result(res,5.seconds)
        val res2 = db. run {
          schema.As.delete(t1.getId).flatMap { n =>
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

    case class MappingsT(
      oid: Option[Long] = None, r: Regex, i: Instant, d: java.time.Duration, s: Symbol, jso: JsObject) extends Storable

    case class MapperSchema(name : String) extends Schema("mapper", name, testDbConfig(name)) {
      import dbConfig.driver.api._
      class MappingsRow(tag:Tag) extends StorableRow[MappingsT](tag, "Mappings") {
        def r = column[Regex](nm("r"))
        def i = column[Instant](nm("i"))
        def d = column[java.time.Duration](nm("d"))
        def s = column[Symbol](nm("s"))
        def jso = column[JsObject](nm("jso"))
        def * = (id.?,r,i,d,s,jso) <> (MappingsT.tupled, MappingsT.unapply)
      }
      object Mappings extends StorableQuery[MappingsT, MappingsRow](new MappingsRow(_))
      def schemas = Mappings.schema
    }

    "support common column mappers" in testdb("mapper")(name => MapperSchema(name)){ schema: MapperSchema =>
      import schema.dbConfig.db
      val emptyJsObject = JsObject(Map.empty[String,JsValue])
      val now = Instant.now()
      Await.result(schema.create,5.seconds)
      val value = MappingsT(None, "foo".r, now, java.time.Duration.ofDays(1), 'Symbol, emptyJsObject )

      val id = Await.result( db.run {schema.Mappings.create(value) }, 5.seconds)
      id must beEqualTo(1)
      val obj = Await.result( db.run { schema.Mappings.retrieve(1) }, 5.seconds)
      obj.isDefined must beTrue
      val mt = obj.get
      mt.oid must beEqualTo(Some(1))
      mt.r.pattern.pattern() must beEqualTo("foo".r.pattern.pattern())
      mt.i must beEqualTo(now)
      mt.d must beEqualTo(java.time.Duration.ofDays(1))
      mt.s must beEqualTo('Symbol)
      mt.jso must beEqualTo(emptyJsObject)
    }
  }
}
