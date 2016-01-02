package com.reactific.slickery

import com.typesafe.config.{ConfigFactory, Config}
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver

/** A concrete instance of a database */
trait SlickeryDB[DRVR <: JdbcDriver] {
  def supportedDB : SupportedDB[DRVR]
  def url : String
  def dbName : String
  def schema : Option[String]
  def host : Option[String]
  def port : Option[String]
  def user : Option[String]
  def pass : Option[String]
}

object SlickeryDB {
//  def fromConfig[SUPDB](kind: SUPDB, path: String, config : Config = ConfigFactory.load() ) : SlickeryDB[SUPDB] = {
//    val dbConfig = DatabaseConfig.forConfig(path, config)
//  }
}
