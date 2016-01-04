package com.reactific.slickery

import java.time.Duration

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import com.reactific.slickery.Storable._
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Specification
import play.api.libs.json.JsValue

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


case class SQLiteBean(oid : Option[OIDType], json : JsValue, duration: Duration) extends Storable

class SQLiteSchema(name : String) extends Schema[SQLiteDriver](name, SQLite, name, SQLite.makeDbConfigFor(name)) {

  import driver.api._
  import driver._

  class TestTable(tag: Tag) extends StorableRow[SQLiteBean](tag, "Test") {
    implicit lazy val durationMapper = driver.durationMapper
    implicit lazy val jsValueMapper = driver.jsValueMapper

    def json = column[JsValue]("json")
    def duration = column[Duration]("duration")

    def * = (oid.?, json, duration) <> ((SQLiteBean.apply _).tupled, SQLiteBean.unapply )
  }

  object Tests extends StorableQuery[SQLiteBean,TestTable](new TestTable(_))

  val schemas : Map[String, SchemaDescription] = Map("Test" → Tests.schema)
}

class SQLiteSpec extends Specification with LoggingHelper with FutureHelper {

  lazy val sqliteIsViable : Boolean = SQLite.testConnection

  def isViable[T](name: String)(fun : (SQLiteSchema) ⇒ T)(implicit evidence : AsResult[T]) : Result = {
    if (sqliteIsViable) {
      var maybeSchema : Option[SQLiteSchema] = None
      try {
        val sqlite = new SQLiteSchema(name)
        sqlite.driver.ensureDbExists(name, sqlite.db)
        maybeSchema = Some(sqlite)
        AsResult(fun(sqlite))
      } finally {
        maybeSchema match {
          case Some(schema) ⇒
            val future  = schema.driver.dropDatabase(name, schema.db)
            val result = Await.result(future, 5.seconds)
            schema.db.close()
            log.trace(s"Dropping SQLite DB $name returned $result")
            // FIXME: result must beEqualTo(true)
          case None ⇒
            log.warn(s"Failure during construction of SQLiteSchema($name)")
        }
      }
    }
    else
      skipped("SQLite service is not available")
  }

  "SQLiteSpec" should {
    "be viable" in isViable("viability") { schema : SQLiteSchema ⇒
      sqliteIsViable must beTrue
    }
  }
}
