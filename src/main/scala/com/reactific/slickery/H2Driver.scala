package com.reactific.slickery

import java.io.File

import slick.driver.{H2Driver ⇒ SlickH2Driver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}

trait H2Driver extends SlickH2Driver with SlickeryDriver { driver : JdbcDriver ⇒

  import driver.api._

  override def createDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"" } map { count ⇒ true }
  }

  override def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    Future {
      val results = for (x <- Seq(".h2.db", ".lock.db", ".mv.db", ".trace.db")) yield {
        val f = new File(dbName + x)
        val result = f.delete()
        if (!result)
          log.debug(s"Deleting ${f.getCanonicalPath} failed")
        result
      }
      !results.contains(false)
    }
  }

  def createSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    val statement = s"""CREATE SCHEMA IF NOT EXISTS "$schemaName";"""
    sqlu"#$statement".map { i ⇒ () }
  }

  def dropSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    val statement = s"""DROP SCHEMA IF EXISTS "$schemaName";"""
    sqlu"#$statement".map { i ⇒ () }
  }

}

object H2Driver extends H2Driver
