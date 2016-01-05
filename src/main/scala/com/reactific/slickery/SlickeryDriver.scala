package com.reactific.slickery

import java.sql.Timestamp
import java.time.{Duration, Instant}

import com.typesafe.config.{ConfigRenderOptions, ConfigFactory, Config}
import play.api.libs.json.{JsValue, Json}
import slick.driver.JdbcDriver

import scala.concurrent.{Future, ExecutionContext}
import scala.util.matching.Regex

trait SlickeryDriver extends JdbcDriver with SlickeryComponent { driver: JdbcDriver =>

  import driver.api._

  implicit lazy val instantMapper = MappedColumnType.base[Instant,Timestamp](
    { i => new Timestamp( i.toEpochMilli ) },
    { t => Instant.ofEpochMilli(t.getTime) }
  )

  implicit lazy val regexMapper = MappedColumnType.base[Regex, String] (
    { r => r.pattern.pattern() },
    { s => new Regex(s) }
  )

  implicit lazy val durationMapper = MappedColumnType.base[Duration,Long] (
    { d => d.toMillis },
    { l => Duration.ofMillis(l) }
  )

  implicit lazy val symbolMapper = MappedColumnType.base[Symbol,String] (
    { s => s.name},
    { s => Symbol(s) }
  )

  implicit lazy val jsValueMapper = MappedColumnType.base[JsValue,String] (
    { jso => Json.stringify(jso) },
    { str => Json.parse(str)
    }
  )

  implicit lazy val configMapper = MappedColumnType.base[Config,String] (
    { conf ⇒ conf.root.render(ConfigRenderOptions.concise())},
    { str => ConfigFactory.parseString(str) }
  )

  def ensureDbExists(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean]

  def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean]

  def makeSchema(schemaName: String) : DBIO[Int]

}
