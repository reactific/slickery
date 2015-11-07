package com.reactific.slickery

import slick.dbio.DBIO
import slick.driver.JdbcProfile

sealed trait SupportedDatabase {
  def driver : String
  def slickDriver : String
  def urlPrefix : String
  def makeSchema(drvr : JdbcProfile, schemaName: String) : DBIO[Vector[Int]] = {
    import drvr.api._
    sql"".as[Int]
  }
}

case object H2 extends SupportedDatabase {
  val driver = "org.h2.Driver"
  val slickDriver = "slick.driver.H2Driver$"
  val urlPrefix = "jdbc:h2"
  override def makeSchema(drvr : JdbcProfile, schemaName: String) : DBIO[Vector[Int]] = {
    import drvr.api._
    sql"""CREATE SCHEMA IF NOT EXISTS "#${schemaName}"""".as[Int]
  }
}

case object MySQL extends SupportedDatabase {
  val driver = "com.mysql.jdbc.Driver"
  val slickDriver = "slick.driver.MySQLDriver$"
  val urlPrefix = "jdbc:mysql"
  override def makeSchema(drvr : JdbcProfile, schemaName: String) : DBIO[Vector[Int]] = {
    import drvr.api._
    sql"""CREATE SCHEMA IF NOT EXISTS "#${schemaName}"""".as[Int]
  }
}

case object SQLite extends SupportedDatabase {
  val driver = "org.sqlite.JDBC"
  val slickDriver = "slick.driver.SQLiteDriver$"
  val urlPrefix = "jdbc:sqllite"
}

case object PostgresQL extends SupportedDatabase {
  val driver = "org.postgresql.Driver"
  val slickDriver = "slick.driver.PostgresDriver$"
  val urlPrefix = "jdbc:postgresql"
}

object SupportedDatabase {
  def all  = Seq( H2, MySQL, SQLite, PostgresQL)

  def forDriverName(driverName: String) : Option[SupportedDatabase] = {
    for (sdb <- all) {
      if (sdb.slickDriver.startsWith(driverName))
        return Some(sdb)
    }
    None
  }

  def forJDBCUrl(url: String) : Option[SupportedDatabase] = {
    for (sdb <- all) {
      if (url.startsWith(sdb.urlPrefix))
        return Some(sdb)
    }
    None
  }
}
