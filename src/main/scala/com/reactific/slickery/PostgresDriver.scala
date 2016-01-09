package com.reactific.slickery

import com.github.tminglei.slickpg._
import play.api.libs.json.{Json, JsValue}
import slick.driver.JdbcDriver

import scala.concurrent.{Future, ExecutionContext}

/** Title Of Thing.
  *
  * Description of thing
  */
trait PostgresDriver extends ExPostgresDriver with SlickeryDriver
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

  override def createDatabase(dbName : String, ignore : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    val db = slick.jdbc.JdbcBackend.Database.forURL(PostgresQL.connectionTestUrl)
    try {
      val d = driver
      import d.api._
      val statement =
        s"""DO
        |$$do$$
        |BEGIN
        |CREATE EXTENSION IF NOT EXISTS dblink;
        |IF EXISTS (SELECT 1 FROM pg_database WHERE datname = '$dbName') THEN
        |   RAISE NOTICE 'Database already exists';
        |ELSE
        |   PERFORM dblink_exec('hostaddr=127.0.0.1 port=5432 dbname=postgres', 'CREATE DATABASE "$dbName"');
        |END IF;
        |END
        |$$do$$""".stripMargin
      //
      db.run(sqlu"#$statement").map { count ⇒ true } recover {
        case xcptn: org.postgresql.util.PSQLException if xcptn.getMessage.contains("already exists") ⇒
          true
        case xcptn: Throwable ⇒
          throw xcptn
      }
    } finally {
      db.close
    }
  }

  override def dropDatabase(dbName : String, ignore : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    val db = slick.jdbc.JdbcBackend.Database.forURL(PostgresQL.connectionTestUrl)
    try {
      val d = driver
      import d.api._
      val statement = s"""DROP DATABASE IF EXISTS "$dbName";"""
      db.run { sqlu"#$statement" }.map { count ⇒ true }
    } finally {
      db.close
    }
  }



  def createSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    log.debug(s"Creating Postgres Schema $schemaName")
    val sql = s"""CREATE SCHEMA IF NOT EXISTS "$schemaName";
                 |CREATE EXTENSION IF NOT EXISTS dblink;
                 |CREATE EXTENSION IF NOT EXISTS hstore;
                 |CREATE EXTENSION IF NOT EXISTS ltree ;""".stripMargin
    sqlu"#$sql".map { i ⇒ () }
  }

  def dropSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    val statement = s"""DROP SCHEMA IF EXISTS "$schemaName";"""
    sqlu"#$statement".map { i ⇒ () }
  }

}

object PostgresDriver extends PostgresDriver
