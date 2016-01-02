package com.reactific.slickery

import slick.driver.{SQLiteDriver ⇒ SlickSQLiteDriver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}


class SQLiteDriver extends SlickSQLiteDriver with SlickeryExtensions { driver : JdbcDriver ⇒

  import driver.api._

  override def ensureDbExists(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"CREATE DATABASE IF NOT EXISTS #$dbName" }.map { count ⇒ true }
  }

  def makeSchema(schemaName: String) : DBIO[Int] = {
    sqlu"CREATE SCHEMA IF NOT EXISTS #$schemaName"
  }

}


object SQLiteDriver extends SQLiteDriver
