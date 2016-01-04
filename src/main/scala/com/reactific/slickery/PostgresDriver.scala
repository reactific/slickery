package com.reactific.slickery

import com.github.tminglei.slickpg._
import play.api.libs.json.{Json, JsValue}
import slick.driver.JdbcDriver

import scala.concurrent.{Future, ExecutionContext}

/** Title Of Thing.
  *
  * Description of thing
  */
trait PostgresDriver extends ExPostgresDriver with SlickeryExtensions
  with PgArraySupport
  with PgDateSupport
  with PgRangeSupport
  with PgHStoreSupport
  with PgPlayJsonSupport
  with PgSearchSupport
  with PgPostGISSupport
  with PgNetSupport
  with PgLTreeSupport { driver: JdbcDriver =>

  import driver.api._

  def pgjson = "jsonb" // jsonb support is in postgres 9.4.0 onward; for 9.3.x use "json"

  override val api = SlickeryPostgresAPI

  object SlickeryPostgresAPI extends API
    with ArrayImplicits
    with DateTimeImplicits
    with JsonImplicits
    with NetImplicits
    with LTreeImplicits
    with SimpleLTreePlainImplicits
    with RangeImplicits
    with SimpleRangePlainImplicits
    with HStoreImplicits
    with SimpleHStorePlainImplicits
    with PostGISImplicits
    with PostGISPlainImplicits
    with SearchImplicits
    with SearchAssistants
     {
    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
    implicit val playJsonArrayTypeMapper =
      new AdvancedArrayJdbcType[JsValue](pgjson,
        (s) => utils.SimpleArrayUtils.fromString[JsValue](Json.parse)(s).orNull,
        (v) => utils.SimpleArrayUtils.mkString[JsValue](_.toString())(v)
      ).to(_.toList)
  }

  override def ensureDbExists(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    val statement = s"""CREATE DATABASE "$dbName"; """
    db.run(sqlu"#$statement").map { count ⇒ true } recover {
      case xcptn: org.postgresql.util.PSQLException ⇒
        if (xcptn.getMessage.contains("already exists"))
          true
        else
          throw xcptn
      case xcptn: Throwable ⇒
        throw xcptn
    }
  }

  override def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"""DROP DATABASE IF EXISTS "#$dbName"""" }.map { count ⇒ true }
  }



  def makeSchema(schemaName: String) : DBIO[Int] = {
    log.debug(s"Creating Postgres Schema $schemaName")
    val sql = s"""CREATE SCHEMA IF NOT EXISTS "$schemaName";
                 |CREATE EXTENSION IF NOT EXISTS hstore;
                 |CREATE EXTENSION IF NOT EXISTS ltree ;""".stripMargin
    sqlu"#$sql"
  }

}

object PostgresDriver extends PostgresDriver
