package com.reactific.slickery

import java.util
import java.util.concurrent.TimeUnit

import com.github.tminglei.slickpg.{LTree, `[_,_]`, Range}

import com.reactific.slickery.Storable.OIDType
import com.reactific.slickery.testkit.SlickerySpecification

import com.typesafe.config.{ConfigFactory, Config}

import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory

import org.specs2.execute.{Result, AsResult}

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

case class PostgresSchema(name: String) extends CommonTestSchema[PostgresDriver](name, name, PostgresQL.makeDbConfigFor(name)) {

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

  override def schemas : Map[String, SchemaDescription] = super.schemas ++ Map("Test" → Tests.schema)

}


/** Test Case For Postgres Specific Features */
class PostgresSpec extends SlickerySpecification with CommonTests {

  sequential

  "PostgresQL" should {
    "handle common extension types" in {
      WithPostgresSchema("Postgres_common_types")(new PostgresSchema(_)) { schema: PostgresSchema ⇒
        readAndWriteMappedTypes[PostgresDriver, PostgresSchema](schema)
      } must throwA[org.postgresql.util.PSQLException]
      pending("resolution of 'being accessed by other users' error on database drop")
    }

    "handle pg-specific types" in {
      WithPostgresSchema("Postgres_extensions")(n ⇒ new PostgresSchema(n)) { schema: PostgresSchema =>
        val range = Range[Int](2, 7)
        val coords = PackedCoordinateSequenceFactory.DOUBLE_FACTORY.create(Array[Double](0.0, 1.0, 2.0), 3)
        val geofactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED))
        val point = new Point(coords, geofactory)
        val data = PostgresBean(
          None, range, "this is some text", Map("foo" → "bar"),
          List("scala", "java"), Json.parse( """ { "answer" : 42 } """), LTree("one.two.three")
        )
        val future = schema.Tests.runCreate(data).flatMap { oid ⇒
          schema.Tests.runRetrieve(oid).map {
            case Some(entity) ⇒
              val data2 = data.copy(oid = Some(oid))
              data2.range must beEqualTo(data.range)
              data2.text must beEqualTo(data.text)
              data2.props must beEqualTo(data.props)
              data2.json must beEqualTo(data.json)
              AsResult(data2.ltree must beEqualTo(data.ltree))
            case None ⇒
              failure("retrieve of just created object failed")
          }
        }
        Await.result(future, Duration(5, TimeUnit.SECONDS))
      } must throwA[org.postgresql.util.PSQLException]
      pending("resolution of 'being accessed by other users' error on database drop")
    }
  }
}
