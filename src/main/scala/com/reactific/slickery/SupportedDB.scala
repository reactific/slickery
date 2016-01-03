package com.reactific.slickery

import java.sql.DriverManager

import com.typesafe.config.{ConfigFactory, Config}
import slick.backend.DatabaseConfig
import slick.driver._

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

sealed trait SupportedDB[DriverType <:JdbcDriver] extends SlickeryComponent {
  def jdbcDriverClass: String

  def slickDriver: String

  def urlPrefix: String

  def driver: DriverType

  def config_name: String

  def driverClass = Class.forName(jdbcDriverClass)

  def defaultPort : Int

  def connectionTestUrl: String

  def kindName: String = getClass.getSimpleName.replaceAll("[$]", "")

  def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort) : String = {
    s"$urlPrefix://$host:$port/$dbName?useSSL=false"
  }

  def makeDbConfigFor(dbName: String, host : String = "localhost", port : Int = defaultPort ) : Config = {
    ConfigFactory.parseString(
      s"""$dbName {
         |  driver = "$slickDriver"
         |  db {
         |    connectionPool = disabled
         |    driver = "$jdbcDriverClass"
         |    url = "${makeConnectionUrl(dbName, host, port)}"
         |  }
         |}""".stripMargin)
  }

  def testConnection: Boolean = Try {
    val clazz = driverClass
    DriverManager.getConnection(connectionTestUrl)
  } match {
    case Success(conn) ⇒
      log.warn(s"$kindName is viable")
      true
    case Failure(xcptn) ⇒
      log.warn(s"$kindName is not viable: ${xcptn.getClass.getSimpleName + ": " + xcptn.getMessage}")
      false
  }
}

class H2_SupportedDB extends SupportedDB[H2Driver] {
  def jdbcDriverClass = "org.h2.Driver"
  val slickDriver = "com.reactific.slickery.H2Driver$"
  val urlPrefix = "jdbc:h2"
  val driver = H2Driver
  val config_name = "h2"
  val connectionTestUrl = "jdbc:h2:mem:test"
  val defaultPort = 0
}
case object H2 extends H2_SupportedDB

class MySQL_SupportedDB extends SupportedDB[MySQLDriver] {
  def jdbcDriverClass = "com.mysql.jdbc.Driver"
  val slickDriver = "com.reactific.slickery.MySQLDriver$"
  val urlPrefix = "jdbc:mysql"
  val driver = MySQLDriver
  val config_name = "mysql"
  val connectionTestUrl = "jdbc:mysql://localhost:3306/?useSSL=false"
  val defaultPort = 3306
  override def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort) : String = {
    super.makeConnectionUrl(dbName, host, port) + "?useSSL=false"
  }
}
case object MySQL extends MySQL_SupportedDB

class SQLite_SupportedDB extends SupportedDB[SQLiteDriver] {
  def jdbcDriverClass = "org.sqlite.JDBC"
  val slickDriver = "com.reactific.slickery.SQLiteDriver$"
  val urlPrefix = "jdbc:sqllite"
  val driver = SQLiteDriver
  val config_name = "sqlite"
  val connectionTestUrl = "jdbc:sqlite:test"
  val defaultPort = 0
}
case object SQLite extends SQLite_SupportedDB

class PostgresQL_SupportedDB extends SupportedDB[PostgresDriver] {
  def jdbcDriverClass = "org.postgresql.Driver"
  val slickDriver = "com.reactific.slickery.PostgresDriver$"
  val urlPrefix = "jdbc:postgresql"
  val driver = PostgresDriver
  val config_name = "postgresql"
  val connectionTestUrl = "jdbc:postgresql://localhost:5432/test"
  val defaultPort = 5432
}
case object PostgresQL extends PostgresQL_SupportedDB

object SupportedDB {
  def all  = Seq( H2, MySQL, SQLite, PostgresQL)

  def forDriverName(driverName: String) : Option[SupportedDB[_]] = {
    for (sdb <- all) {
      if (sdb.slickDriver.startsWith(driverName))
        return Some(sdb)
    }
    None
  }

  def forJDBCUrl(url: String) : Option[SupportedDB[_]] = {
    for (sdb <- all) {
      if (url.startsWith(sdb.urlPrefix))
        return Some(sdb)
    }
    None
  }
}
