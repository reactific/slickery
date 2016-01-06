package main.com.reactific.slickery.testkit

import java.io.File

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import com.reactific.helpers.testkit.HelperSpecification
import com.reactific.slickery.{SlickeryDriver, Schema}
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.execute.{Result, AsResult}
import org.specs2.specification.Fixture
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

class SlickerySpec {

}

trait SlickeryTestHelpers extends LoggingHelper with FutureHelper {
}

/** Title Of Thing.
  *
  * Description of thing
  */
class SlickerySpecification extends HelperSpecification with SlickeryTestHelpers {
  def slickeryTestDir = "./target/slickeryDB"

  def slickeryDbConfig(name : String) : Config = {

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

  def WithSchema[D <: SlickeryDriver, S <: Schema[D], R]
    (create: ⇒ S)(f : S ⇒ R)(implicit evidence : AsResult[R]) : Result = {
    val fixture = create
    try {
      import fixture.driver
      driver.ensureDbExists()
    } finally {

    }
  }
  class SchemaFixture[T <: CoreSchema[_]](create: ⇒ T) extends Fixture[T] {
    def apply[R](f : T ⇒ R)(implicit evidence : AsResult[R]) : Result = {
      val fixture = create
      try {
        AsResult(f(fixture))
      } finally {
        fixture.drop()
      }
    }
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
