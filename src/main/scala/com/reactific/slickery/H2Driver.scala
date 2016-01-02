package com.reactific.slickery

import slick.driver.{H2Driver ⇒ SlickH2Driver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}

trait H2Driver extends SlickH2Driver with SlickeryExtensions { driver : JdbcDriver ⇒

  import driver.api._

  override def ensureDbExists(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"" } map { count ⇒ true }
  }

  def makeSchema(schemaName: String) : DBIO[Int] = {
    val statement = s"""CREATE SCHEMA IF NOT EXISTS "$schemaName";"""
    sqlu"#$statement"
  }

}

object H2Driver extends H2Driver
