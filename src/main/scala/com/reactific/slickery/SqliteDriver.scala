package com.reactific.slickery

import slick.driver.{SQLiteDriver ⇒ SlickSQLiteDriver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}

import java.io.File

class SQLiteDriver extends SlickSQLiteDriver with SlickeryDriver { driver : SlickSQLiteDriver ⇒

  import driver.api._

  override def createDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    Future {
      import sys.process._
      val cmd = Seq("sqlite3", "-cmd", ".database", dbName)
      val processIO = scala.sys.process.BasicIO(false,{ (String) ⇒ () }, None)
      val process = cmd.run(processIO)
      val exitVal = process.exitValue()
      log.debug(s"${cmd.mkString(" ")} returned $exitVal")
      exitVal == 0
    }
  }

  override def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    Future {
      val file = new File("./" + dbName)
      file.delete()
    }
  }

  def createSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    sqlu"SELECT 1; ".map { i ⇒ () }
  }

  def dropSchema(schemaName: String)(implicit ec: ExecutionContext) : DBIO[Unit] = {
    sqlu"SELECT 1; ".map { i ⇒ () }
  }

}


object SQLiteDriver extends SQLiteDriver
