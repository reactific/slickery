package com.reactific.slickery

import java.time.Instant

import com.reactific.helpers.FutureHelper
import com.reactific.slickery.Storable.OIDType
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.SpecificationLike
import org.specs2.execute.{AsResult, Result}

import play.api.libs.json.{JsValue, Json}

import slick.profile.SqlProfile.ColumnOption.Nullable

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.matching.Regex

case class MappedTypesBean(
  instant : Instant = Instant.now,
  regex : Regex = "foo".r,
  duration : Duration = 5000.milliseconds,
  symbol : Symbol = 'symbol,
  jsValue : JsValue = Json.obj("one" → 1, "two" → 2.0, "three" → "drei"),
  config : Config = ConfigFactory.parseString("{ a : \"b\" }"),
  oid : Option[OIDType] = None
) extends Storable

abstract class CommonTestSchema[D <: SlickeryDriver](
    schemaNamePrototype: String,
    configPath : String,
    config : Config = ConfigFactory.load)
    (implicit ec: ExecutionContext, classTag : ClassTag[D])
  extends Schema[D](schemaNamePrototype, configPath, config)(ec,classTag) {

  import driver.api._
  import driver._

  override implicit lazy val instantMapper = driver.instantMapper
  override implicit lazy val regexMapper = driver.regexMapper
  override implicit lazy val durationMapper = driver.durationMapper
  override implicit lazy val symbolMapper = driver.symbolMapper
  override implicit lazy val jsValueMapper = driver.jsValueMapper
  override implicit lazy val configMapper = driver.configMapper

  class MappedTypesRow(tag : Tag) extends StorableRow[MappedTypesBean](tag,"mappings") {
    def instant = column[Instant](nm("instant"), Nullable)
    def regex = column[Regex](nm("regex"),Nullable)
    def duration = column[Duration]("duration")
    def symbol = column[Symbol]("symbol")
    def json = column[JsValue]("json")
    def config = column[Config]("config")
    def * = (instant, regex, duration, symbol, json, config, oid.?) <>
      ((MappedTypesBean.apply _).tupled, MappedTypesBean.unapply )
  }

  object MappedTypesQuery extends StorableQuery[MappedTypesBean,MappedTypesRow](new MappedTypesRow(_))

  def schemas : Map[String, SchemaDescription] = Map("mappings" → MappedTypesQuery.schema)
}

trait CommonTests extends SpecificationLike with FutureHelper {

  def readAndWriteMappedTypes[D <: SlickeryDriver, S <: CommonTestSchema[D]](schema : S)(implicit ec: ExecutionContext) : Result = {
    val q = schema.MappedTypesQuery
    val obj = MappedTypesBean()
    val future = q.runCreate(obj).flatMap { oid ⇒
      q.runRetrieve(oid).map {
        case Some(mtb) ⇒
          val obj2 = obj.copy(oid = Some(oid))
          obj2.instant must beEqualTo(obj.instant)
          obj2.regex must beEqualTo(obj.regex)
          obj2.duration must beEqualTo(obj.duration)
          obj2.symbol must beEqualTo(obj.symbol)
          obj2.jsValue must beEqualTo(obj.jsValue)
          obj2.config must beEqualTo(obj.config)
        case None ⇒
          throw new Exception("creating or retrieving MappedTypesBean failed")
      }
    }
    AsResult(Await.result(future,timeout))
  }
}
