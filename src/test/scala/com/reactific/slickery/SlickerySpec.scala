package com.reactific.slickery

import java.io.File

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global

case class FakeSchema(name : String, config: Config) extends Schema(name,H2, name,config) {

  import driver.SchemaDescription
  import driver.api._

  override def schemas: Map[String,SchemaDescription] = ???
}

trait SlickeryTestHelpers extends LoggingHelper with FutureHelper {
  final val baseDir = "./target/testdb"

  def testDbConfig(name : String) : Config = {
    ConfigFactory.parseString(
      s"""$name {
         |  driver = "com.reactific.slickery.H2Driver$$"
         |  db {
         |    connectionPool = disabled
         |    driver = "org.h2.Driver"
         |    url = "jdbc:h2:$baseDir/$name"
         |  }
         |}""".stripMargin)
  }

  def testdb[S,T](name : String)(c: (String) => S)(f: (S) => T )(implicit evidence: AsResult[T]) = {
    try {
      AsResult(f(c(name)))
    } finally {
      for (x <- Seq(".mv.db", ".trace.db")) {
        val f = new File(baseDir, name + x)
        val result = f.delete()
        log.trace(s"Deleting ${f.getCanonicalPath} returned $result")
      }
    }
  }

  def jdbcProfile(name : String) = DatabaseConfig.forConfig[JdbcProfile](name, testDbConfig(name)).driver
}

/** Title Of Thing.
  *
  * Description of thing
  */
class SlickerySpec extends Specification with SlickeryTestHelpers {

}
