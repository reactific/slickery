package com.reactific.slickery

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import com.reactific.slickery.Storable.OIDType
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Specification
import play.api.libs.json.JsValue

import java.time.Duration

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


case class MySQLBean(oid : Option[OIDType], json : JsValue, duration: Duration) extends Storable

class MySQLSchema(name : String) extends Schema[MySQLDriver](name, name, MySQL.makeDbConfigFor(name)) {

  import driver.api._
  import driver._

  class TestTable(tag: Tag) extends StorableRow[MySQLBean](tag, "Test") {
    implicit lazy val durationMapper = driver.durationMapper
    implicit lazy val jsValueMapper = driver.jsValueMapper

    def json = column[JsValue]("json")
    def duration = column[Duration]("duration")

    def * = (oid.?, json, duration) <> ((MySQLBean.apply _).tupled, MySQLBean.unapply )
  }

  object Tests extends StorableQuery[MySQLBean,TestTable](new TestTable(_))

  val schemas : Map[String, SchemaDescription] = Map("Test" → Tests.schema)

}


class MySQLSpec extends Specification with LoggingHelper with FutureHelper {

  lazy val mySqlIsViable : Boolean = MySQL.testConnection

  def isViable[T](name: String)(fun : (MySQLSchema) ⇒ T)(implicit evidence : AsResult[T]) : Result = {
    if (mySqlIsViable) {
      var pgs : Option[MySQLSchema] = None
      try {
        pgs = Some(new MySQLSchema(name))
        AsResult(fun(pgs.get))
      } finally {
        pgs match {
          case Some(schema) ⇒
            import schema.driver.api._
            val future = schema.db.run { sqlu"""DROP DATABASE IF EXISTS "#$name"""" }
            val result = Await.result(future, 5.seconds)
            schema.db.close()
            log.trace(s"Dropping MySQL DB $name returned $result")
            result must beEqualTo(0)
          case None ⇒
            log.warn(s"Failure during construction of MySQLSchema($name)")
        }
      }
    }
    else
      skipped("MySQL service is not available")
  }


  "MySQLSpec" should {
    "be viable .. or not" in {
      mySqlIsViable
      success
    }
    "ensure test db exists" in {
      pending(": permissions issue")
      /* FIXME:      isViable("ensure_test_db"){ schema : MySQLSchema ⇒
        {
        val future = schema.driver.ensureDbExists("ensure_test_db", schema.db).map { result ⇒
          result must beTrue
        }
        Await.result(future, 5.seconds)
      } */
    }
  }
}
