package com.reactific.slickery

import java.util.concurrent.TimeUnit

import com.github.tminglei.slickpg.{LTree, `[_,_]`, Range}
import com.reactific.helpers.{FutureHelper, LoggingHelper}
import com.reactific.slickery.Storable.OIDType
import com.typesafe.config.{ConfigFactory, Config}
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory

import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Specification
import play.api.libs.json.{Json, JsValue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await

case class PostgresBean(oid : Option[OIDType], range : Range[Int],
  text: String, props: Map[String,String],
  tags : List[String], json : JsValue, ltree : LTree) extends Storable


object PostgresSchema {
  def getDbConf(name : String) : Config = {
    ConfigFactory.parseString(
      s"""$name {
         |  driver = "com.reactific.slickery.PostgresDriver$$"
         |  db {
         |    connectionPool = disabled
         |    driver = "org.postgresql.Driver"
         |    url = "jdbc:postgresql://localhost:5432/test"
         |  }
         |}""".stripMargin)
  }
}

case class PostgresSchema(name: String) extends Schema[PostgresDriver](name, name, PostgresSchema.getDbConf(name)) {

  import slick.profile.SqlProfile.ColumnOption.SqlType
  import driver.api._
  import driver._

  implicit val simpleIntRangeTypeMapper = driver.api.simpleIntRangeTypeMapper
  implicit val simpleHStoreTypeMapper = driver.api.simpleHStoreTypeMapper
  implicit val simpleLTreeTypeMapper = driver.api.simpleLTreeTypeMapper

  class TestTable(tag: Tag) extends StorableRow[PostgresBean](tag, "Test") {
    def range = column[Range[Int]]("range", O.Default(Range[Int](1,9,`[_,_]`)))
    def text = column[String]("text", SqlType("varchar(4000)"))
    def props = column[Map[String,String]]("props_hstore")
    def tags = column[List[String]]("tags_arr")
    def json = column[JsValue]("json")
    def ltree = column[LTree]("ltree")

    def * = (oid.?, range, text, props, tags, json, ltree) <>
      ((PostgresBean.apply _).tupled, PostgresBean.unapply )
  }

  object Tests extends StorableQuery[PostgresBean,TestTable](new TestTable(_))

  val schemas : Map[String, SchemaDescription] = Map("Test" → Tests.schema)

}


/** Test Case For Postgres Specific Features */
class PostgresSpec extends Specification with LoggingHelper with FutureHelper {

  lazy val postgresIsViable : Boolean = PostgresQL.testConnection

  def postgresViable[T](name: String)(fun : (PostgresSchema) ⇒ T)(implicit evidence : AsResult[T]) : Result = {
    if (postgresIsViable) {
      var pgs : Option[PostgresSchema] = None
      try {
        pgs = Some(new PostgresSchema(name))
        AsResult(fun(pgs.get))
      } finally {
        pgs match {
          case Some(schema) ⇒
            import schema.driver.api._
            val future = schema.db.run { sqlu"""DROP DATABASE IF EXISTS "#$name"""" }
            val result = Await.result(future, 5.seconds)
            schema.db.close()
            log.trace(s"Dropping Postgresql DB $name returned $result")
            result must beEqualTo(0)
          case None ⇒
            log.warn(s"Failure during construction of PostgresSchema($name)")
        }
      }
    }
    else
      skipped("Postgresql service is not available")
  }


  "PostgresQL" should {
    "be viable .. or not" in {
      postgresIsViable
      success
    }
    "handle extension types" in postgresViable("handle_extension_types") { schema : PostgresSchema =>
      val future = schema.driver.ensureDbExists(schema.name, schema.db).flatMap { bool ⇒
        val range = Range[Int](2, 7)
        val coords = PackedCoordinateSequenceFactory.DOUBLE_FACTORY.create(Array[Double](0.0, 1.0, 2.0), 3)
        val geofactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED))
        val point = new Point(coords, geofactory)
        val data = PostgresBean(None, range, "this is some text", Map("foo" → "bar"),
          List("scala", "java"), Json.parse( """ { "answer" : 42 } """), LTree("one.two.three"))
        schema.create().flatMap { u ⇒
          schema.Tests.runCreate(data).flatMap { oid ⇒
            schema.Tests.runRetrieve(oid).map {
              case Some(entity) ⇒
                val clone = data.copy(oid = Some(oid))
                AsResult(clone must beEqualTo(entity))
              case None ⇒
                failure("retrieve of just created object failed")
            }
          }
        }
      }
      Await.result(future, Duration(5,TimeUnit.SECONDS))
    }
  }
}
