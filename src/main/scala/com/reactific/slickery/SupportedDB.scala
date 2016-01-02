package com.reactific.slickery

import com.typesafe.config.{ConfigFactory, Config}
import slick.backend.DatabaseConfig
import slick.driver._

import scala.reflect.ClassTag

sealed trait SupportedDB[DriverType <:JdbcDriver] {
  def driverClassName : String
  def slickDriver : String
  def urlPrefix : String
  def driver : DriverType
  def config_name : String
  def dbConfig(config: Config = ConfigFactory.load())(implicit ct : ClassTag[DriverType]) : DatabaseConfig[DriverType] = {
    DatabaseConfig.forConfig[DriverType](config_name, config)
  }
  val driverClass = Class.forName(driverClassName)
}

class H2_SupportedDB extends SupportedDB[H2Driver] {
  def driverClassName = "org.h2.Driver"
  val slickDriver = "com.reactific.slickery.H2Driver$"
  val urlPrefix = "jdbc:h2"
  val driver = H2Driver
  val config_name = "h2"
}
case object H2 extends H2_SupportedDB

class MySQL_SupportedDB extends SupportedDB[MySQLDriver] {
  def driverClassName = "com.mysql.jdbc.Driver"
  val slickDriver = "com.reactific.slickery.MySQLDriver$"
  val urlPrefix = "jdbc:mysql"
  val driver = MySQLDriver
  val config_name = "mysql"
}
case object MySQL extends MySQL_SupportedDB

class SQLite_SupportedDB extends SupportedDB[SQLiteDriver] {
  def driverClassName = "org.sqlite.JDBC"
  val slickDriver = "com.reactific.slickery.SQLiteDriver$"
  val urlPrefix = "jdbc:sqllite"
  val driver = SQLiteDriver
  val config_name = "sqlite"
}
case object SQLite extends SQLite_SupportedDB

class PostgresQL_SupportedDB extends SupportedDB[PostgresDriver] {
  def driverClassName = "org.postgresql.Driver"
  val slickDriver = "com.reactific.slickery.PostgresDriver$"
  val urlPrefix = "jdbc:postgresql"
  val driver = PostgresDriver
  val config_name = "postgresql"
}
case object PostgresQL extends PostgresQL_SupportedDB

object SupportedDB {
  def all  = Seq( H2, MySQL, SQLite, PostgresQL)

  def fromConfig(configPath : String, config: Config) : Option[SupportedDB[_]] = {
    forDriverName(config.getString(configPath + ".driver"))
  }

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
