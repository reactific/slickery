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

import com.reactific.helpers.{FutureHelper, TryWith, LoggingHelper}
import com.reactific.slickery.Storable.OIDType
import com.typesafe.config.{ConfigFactory, Config}
import org.h2.jdbc.JdbcSQLException
import play.api.libs.json.{JsValue, JsObject}
import slick.dbio
import slick.jdbc.meta.MTable
import slick.lifted.ProvenShape

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.util.matching.Regex

object SchemaSpecHelper extends SlickeryTestHelpers {

}
case class Foo(name: String, oid: Option[OIDType] = None, description: String = "", created : Instant = Instant.EPOCH,
    modified : Instant = Instant.EPOCH, expiresAt : Instant = Instant.EPOCH) extends Slickery with Expirable

case class TestSchema(name : String) extends Schema("testSchema", H2, name, SchemaSpecHelper.testDbConfig(name)) {

  import driver.api._

  class TestTableRow(tag: Tag) extends SlickeryRow[Foo](tag, "testTable") {
    def expiresAt = column[Instant]("exipiresAt")
    override def * : ProvenShape[Foo] = {
      (name, oid.?, description, created, modified, expiresAt) <> ((Foo.apply _).tupled, Foo.unapply )
    }
  }

  object foos extends SlickeryQuery[Foo,TestTableRow]( new TestTableRow(_))  {
    val findByName = this.findBy(_.name)
  }
  def schemas = Map("testTable" → foos.schema)
}

case class TestUsable(oid : Option[Long], created : Instant = Instant.EPOCH, modified: Instant = Instant.EPOCH,
                      name: String = "", description: String = "") extends Slickery

case class CorrelationSchema(name : String) extends Schema(name, H2, name, SchemaSpecHelper.testDbConfig(name)) {
  import driver.api._
  case class ARow(tag:Tag) extends SlickeryRow[TestUsable](tag, "As") {
    def * = (oid.?,created,modified,name,description) <> (TestUsable.tupled, TestUsable.unapply)
  }
  case class BRow(tag:Tag) extends SlickeryRow[TestUsable](tag, "Bs") {
    def * = (oid.?,created,modified,name,description) <> (TestUsable.tupled, TestUsable.unapply)
  }
  object As extends SlickeryQuery[TestUsable, ARow]( new ARow(_))
  object Bs extends SlickeryQuery[TestUsable, BRow]( new BRow(_))
  class A2BRow(tag : Tag) extends ManyToManyRow[TestUsable,ARow,TestUsable,BRow](tag, "A2B", "A", As, "B", Bs)
  object A2B extends ManyToManyQuery[TestUsable,ARow,TestUsable,BRow,A2BRow]( new A2BRow(_))
  override def schemas = Map( "As" → As.schema, "Bs" → Bs.schema, "A2B" -> A2B.schema)
}

case class TestExpirableUsable(oid : Option[Long], created : Instant = Instant.EPOCH, modified: Instant = Instant.EPOCH,
    expiresAt : Instant = Instant.EPOCH, name: String = "", description: String = "") extends Slickery with Expirable

class TraitsSchema(name : String) extends Schema("test", H2, name, SchemaSpecHelper.testDbConfig(name)) {
  import driver.api._
  class TraitsRow(tag : Tag) extends ExpirableSlickeryRow[TestExpirableUsable](tag, "TraitsRow") {
    def * = (oid.?,created,modified,expiresAt,name,description) <> (TestExpirableUsable.tupled, TestExpirableUsable.unapply)
  }
  class TraitsQuery extends ExpirableSlickeryQuery[TestExpirableUsable, TraitsRow](new TraitsRow(_))
  object testInfos extends TraitsQuery
  def schemas = Map("TraitsRow" → testInfos.schema)
}

case class MappingsT(
    oid: Option[Long] = None, r: Regex, i: Instant, d: java.time.Duration, s: Symbol, jso: JsValue) extends Storable

case class MapperSchema(name : String) extends Schema("mapper", H2, name, SchemaSpecHelper.testDbConfig(name)) {
  import this.driver.api._
  implicit val regexMapper = driver.regexMapper
  implicit val durationMapper = driver.durationMapper
  implicit val symbolMapper = driver.symbolMapper
  implicit val jsValueMapper = driver.jsValueMapper
  class MappingsRow(tag:Tag) extends StorableRow[MappingsT](tag, "Mappings") {
    def r = column[Regex](nm("r"))
    def i = column[Instant](nm("i"))
    def d = column[java.time.Duration](nm("d"))
    def s = column[Symbol](nm("s"))
    def jso = column[JsValue](nm("jso"))
    def * = (oid.?,r,i,d,s,jso) <> (MappingsT.tupled, MappingsT.unapply)
  }
  object Mappings extends StorableQuery[MappingsT, MappingsRow](new MappingsRow(_))
  def schemas = Map("Mappings" → Mappings.schema)
}


/** Test cases for components */
class SchemaSpec extends SlickerySpec with FutureHelper {
  LoggingHelper.setToInfo("slick.*")
  // LoggingHelper.setToDebug("slick.jdbc.*")

  "Schema" should {
    "throw on bad db config" in {
      def badDbConfig(name: String): Config = {
        ConfigFactory.parseString(
          s"""$name {
             |  driver = "slick.driver.Fahrvergnügen$$"
             |  db {
             |    connectionPool = disabled
             |    driver = "org.fv.Driver"
             |    url = "jdbc:fv:$baseDir/$name"
             |  }
             |}""".stripMargin
        )
      }
      FakeSchema("junk", badDbConfig("junk")) must throwA[slick.SlickException]
    }

    "throw on unsupported driver" in {
      def testDbConfig(name: String): Config = {
        ConfigFactory.parseString(
          s"""$name {
             |  driver = "slick.driver.DerbyDriver$$"
             |  db {
             |    connectionPool = disabled
             |    driver = "org.apache.derby.jdbc.EmbeddedDriver"
             |    url = "jdbc:derby:$baseDir/$name"
             |  }
             |}""".stripMargin
        )
      }
      FakeSchema("nodriver", testDbConfig("nodriver")) must throwA[slick.SlickException]
    }
    "validate empty database generates error" in
      testdb("validate_empty")(name => new TestSchema(name)) { schema: TestSchema =>
        val future = schema.validate().map { throwables ⇒
          throwables.isEmpty must beFalse
          throwables.size must beEqualTo(1)
          throwables.head.isInstanceOf[SlickeryException] must beTrue
          throwables.head.asInstanceOf[SlickeryException].getMessage.contains("is missing") must beTrue
        }
        Await.result(future, 5.seconds)
      }

    "validate non-empty database" in testdb("validate_nonempty")(name => new TestSchema(name)) { schema: TestSchema =>
      val future = schema.create().flatMap { u ⇒
        schema.schemaNames().flatMap { schemaNames ⇒
          schemaNames.contains(schema.schemaName) must beTrue
          schema.metaTables().flatMap { metatables ⇒
            log.info(s"Found ${metatables.size} tables: $metatables")
            metatables.exists { table =>
              table.name.name == "testTable"
            } must beTrue
            schema.validate.map { throwables ⇒
              if (throwables.isEmpty)
                success("Schema matches")
              else
                failure(s"Schema doesn't match: $throwables")
            } recover {
              case x: Throwable ⇒
                failure(s"validate failed: ${x.getMessage}")
            }
          } recover {
            case x: Throwable ⇒
              failure(s"metaTables failed: ${x.getMessage}")
          }
        } recover {
          case x: Throwable ⇒
            failure(s"schema names failed: ${x.getMessage}")
        }
      } recover {
        case x: Throwable ⇒
          failure(s"schema creation failed: ${x.getMessage}")
      }
      Await.result(future, 5.seconds)
    }

    "fail when there's no schema" in testdb("no_schema")(name => new TestSchema(name)) { schema: TestSchema =>
      val future = Await.ready(schema.foos.runRetrieve(0), 5.second)
      future.isCompleted must beTrue
      future.value match {
        case Some(Failure(x: JdbcSQLException)) if x.getMessage.contains("not found") => success
        case Some(Failure(x: JdbcSQLException)) => failure("wrong exception")
        case _ => failure("Should fail")
      }
    }

    "supports dropping schema" in testdb("drop_schema")(name => new TestSchema(name)) { schema: TestSchema =>
      val future = schema.create().flatMap { u ⇒
        schema.drop().flatMap { u ⇒
          schema.metaTables().map { metatables: Seq[MTable] ⇒
            metatables.exists { table =>
              table.name.name == "testTable"
            } must beFalse
          }
        }
      }
      Await.result(future, 5.seconds).toResult
    }

    "succeed when there is a schema" in testdb("with_schema")(name => new TestSchema(name)) { schema: TestSchema =>
      val future = schema.create().flatMap { u ⇒
        schema.foos.runRetrieve(0).map {
          case Some(x: Foo) => failure("There should be no value")
          case None => success
          case _ => failure("Should return None")
        }
      }
      Await.result(future, 5.seconds)
    }

    "allow table traits to be combined" in testdb("combine_traits")(name => new TraitsSchema(name)) {
      schema: TraitsSchema =>
        import schema.db
        import schema.driver.api._
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val res = db.run {
          schema.testInfos.byId(0L).map { testInfo => testInfo must beNone }
          schema.testInfos.byName("").map { testInfo => testInfo.nonEmpty must beTrue }
          schema.testInfos.byDescription("").map { testInfo => testInfo.nonEmpty must beTrue }
          schema.testInfos.modifiedSince(Instant.now()).map { s => s.isEmpty must beTrue }
          schema.testInfos.createdSince(Instant.now()).map { s => s.isEmpty must beTrue }
          schema.testInfos.expiredSince(Instant.now()).map { s ⇒ s.isEmpty must beTrue }
        }
        Await.result(res, 5.seconds)
    }

    "support CRUD operations" in testdb("crud_operations")(name => new TraitsSchema(name)) { schema: TraitsSchema =>
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val t1 = TestExpirableUsable(None, name = "one")
      val id = Await.result(schema.testInfos.runCreate(t1), 5.seconds)
      id must beEqualTo(1)
      val t2 = TestExpirableUsable(Some(id), name = "two", description = "foo")
      Await.result(schema.testInfos.runUpdate(t2).map { count => count must beEqualTo(1) }, 5.seconds)
      Await.result(
        schema.testInfos.runRetrieve(id).map {
          case None =>
            failure("not found");
          case Some(tu: TestExpirableUsable) =>
            asResult(tu.description must beEqualTo("foo"))
        }, 5.seconds
      )
      Await.result(schema.testInfos.runDelete(id).map { count => count must beEqualTo(1) }, 5.seconds)
    }

    "provide correlation tables" in testdb("correlation")(name => new CorrelationSchema(name)) {
      schema: CorrelationSchema =>
        import schema.db
        val create_future = schema.create()
        Await.result(create_future, 5.seconds)
        val t1 = TestUsable(Some(1), name = "one")
        val t2 = TestUsable(Some(2), name = "two")
        val t3 = TestUsable(Some(3), name = "three")
        val res = db.run {
          dbio.DBIO.seq(
            schema.As.create(t1),
            schema.As.create(t2),
            schema.As.create(t3),
            schema.Bs.create(t1),
            schema.Bs.create(t2),
            schema.Bs.create(t3),
            schema.A2B.associate(t1, t2),
            schema.A2B.associate(t1, t3),
            schema.A2B.associate(t2, t3)
          ).flatMap { u: Unit =>
            schema.A2B.findAssociatedB(1).map { s: Seq[Long] => asResult(s must beEqualTo(Vector(2, 3))) }
            schema.A2B.findAssociatedB(t1).map { s: Seq[Long] => asResult(s must beEqualTo(Vector(2, 3))) }
            schema.A2B.findAssociatedA(3).map { s: Seq[Long] => asResult(s must beEqualTo(Vector(1, 2))) }
            schema.A2B.findAssociatedA(t3).map { s: Seq[Long] => asResult(s must beEqualTo(Vector(1, 2))) }
          }
        }
        Await.result(res, 5.seconds)
        val res2 = db.run {
          schema.As.delete(t1.getId).flatMap { n =>
            n must beEqualTo(1)
            schema.A2B.findAssociatedB(1).map { s: Seq[Long] => asResult(s must beEqualTo(Vector.empty[Long])) }
          }
        }
        Await.result(res2, 5.seconds)
        val res3 = db.run {
          schema.A2B.findAssociatedB(2).map { s: Seq[Long] => asResult(s must beEqualTo(Vector(3))) }
        }
        Await.result(res3, 5.seconds)
    }

    "support common column mappers" in testdb("mapper")(name => MapperSchema(name)) { schema: MapperSchema =>
      import schema.db
      val emptyJsObject = JsObject(Map.empty[String, JsValue])
      val now = Instant.now()
      Await.result(schema.create, 5.seconds)
      val value = MappingsT(None, "foo".r, now, java.time.Duration.ofDays(1), 'Symbol, emptyJsObject)
      val id = Await.result(db.run {schema.Mappings.create(value)}, 5.seconds)
      id must beEqualTo(1)
      val obj = Await.result(db.run {schema.Mappings.retrieve(1)}, 5.seconds)
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
