package com.reactific.slickery

import java.io.File

import com.reactific.helpers.LoggingHelper
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global

/** Title Of Thing.
  *
  * Description of thing
  */
class SlickerySpec extends Specification with LoggingHelper {

  final val baseDir = "./target/testdb"

  def testDbConfig(name : String) : Config = {
    ConfigFactory.parseString(
      s"""$name {
         |  driver = "slick.driver.H2Driver$$"
         |  db {
         |    connectionPool = disabled
         |    driver = "org.h2.Driver"
         |    url = "jdbc:h2:$baseDir/$name"
         |  }
         |}""".stripMargin)
  }

  def testdb[S](name : String)(c: (String) => S)(f: (S) => Result ) = {
    try {
      f(c(name))
    } finally {
      for (x <- Seq(".mv.db", ".trace.db")) {
        val f = new File(baseDir, name + x)
        val result = f.delete()
        log.debug(s"Deleting ${f.getCanonicalPath} returned $result")
      }
    }
  }

  def jdbcProfile(name : String) = DatabaseConfig.forConfig[JdbcProfile](name, testDbConfig(name)).driver

  case class FakeSchema(name : String, config: Config) extends Schema(name,name,config) {

    import dbConfig.driver._

    override def schemas: SchemaDescription = ???
  }


}
