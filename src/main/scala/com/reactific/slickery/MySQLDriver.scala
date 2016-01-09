package com.reactific.slickery

import slick.driver.{MySQLDriver ⇒ SlickMySQLDriver, JdbcDriver}

import scala.concurrent.{Future, ExecutionContext}

class MySQLDriver extends SlickMySQLDriver with SlickeryDriver { driver : SlickeryDriver ⇒

  import driver.api._

  override def createDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"""CREATE DATABASE IF NOT EXISTS "#$dbName"""" }.map { count ⇒ true }
  }

  override def dropDatabase(dbName : String, db : Database)(implicit ec: ExecutionContext) : Future[Boolean] = {
    db.run { sqlu"""DROP DATABASE IF EXISTS "#$dbName"""" }.map { count ⇒ true }
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

object MySQLDriver extends MySQLDriver
