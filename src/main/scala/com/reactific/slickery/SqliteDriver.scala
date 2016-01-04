package com.reactific.slickery

import slick.driver.{SQLiteDriver ⇒ SlickSQLiteDriver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}

import java.io.File

class SQLiteDriver extends SlickSQLiteDriver with SlickeryExtensions { driver : JdbcDriver ⇒

  import driver.api._

  override def ensureDbExists(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    Future {
      import sys.process._
      val cmd = Seq("sqlite3", "-cmd", ".database", dbName)
      val processIO = scala.sys.process.BasicIO(false,{ (String) ⇒ () }, None)
      val process = cmd.run(processIO)
      process.exitValue() == 0
    }
  }

  override def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    Future {
      val file = new File("./" + dbName)
      file.delete()
    }
  }

  def makeSchema(schemaName: String) : DBIO[Int] = {
    sqlu"CREATE SCHEMA IF NOT EXISTS #$schemaName"
  }

}


object SQLiteDriver extends SQLiteDriver
