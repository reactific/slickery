package com.reactific.slickery

import java.io.File

import com.reactific.helpers.LoggingHelper
import com.typesafe.config.{ConfigFactory, Config}
import org.h2.jdbc.JdbcSQLException
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.lifted.ProvenShape

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

/** Test cases for components */
class SchemaSpec extends Specification with LoggingHelper {

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

  def dbConfig(name : String) : DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig[JdbcProfile](name, testDbConfig(name))
  }

  case class Foo(id: Int, name: String)

  case class TestSchema(name : String) extends Schema("test", dbConfig(name)) {

    import dbConfig.driver.api._

    class TestTable(tag: Tag) extends BasicTable[Foo](tag, "test") {
      /** The ID column, which is the primary key, and auto incremented */
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      /** The name column */
      def name = column[String]("name")

      override def * : ProvenShape[Foo] = {
        (id, name) <> ((Foo.apply _).tupled, Foo.unapply _ )
      }
    }

    object foos extends StandardTable[Foo,Int,TestTable]( new TestTable(_))  {
      def create(entity: Foo) : Future[Int] = db.run { this.insertOrUpdate(entity) }
      def retrieve(id : Int)  : Future[Option[Foo]] = db.run { this.findBy(_.id).applied(id).result.headOption }
      def update(entity: Foo) : Future[Int] = db.run { this.insertOrUpdate(entity) }
      def delete(id: Int)     : Future[Int] = db.run { this.findBy(_.id).applied(id).delete }
      val findByName = this.findBy(_.name)
    }

    def tables : Seq[StandardTable[_,_,_]] = Seq(foos)
    def schemas = foos.schema
  }

  def testdb(name : String)(f : (TestSchema) => Result ) = {
    try {
      val schema = TestSchema(name)
      f(schema)
    } finally {
      for (x <- Seq(".mv.db", ".trace.db")) {
        val f = new File(baseDir, name + x)
        val result = f.delete()
        log.debug(s"Deleting ${f.getCanonicalPath} returned $result")
      }
    }
  }

  "Component" should {
    "fail when there's no schema" in testdb("no_schema") { schema : TestSchema =>
      val future = Await.ready(schema.foos.retrieve(0), 5.second)
      future.isCompleted must beTrue
      future.value match {
        case Some(Failure(x: JdbcSQLException)) if x.getMessage.contains("not found") => success
        case Some(Failure(x: JdbcSQLException)) => failure("wrong exception")
        case _ => failure("Should fail")
      }
    }

    "succeed when there is a schema" in testdb("with_schema") { schema : TestSchema =>
      val create_future = schema.create()
      Await.result(create_future, 5.seconds)
      val retrieve_future = schema.foos.retrieve(0)
      Await.result(retrieve_future, 5.second) match {
        case Some(x: Foo) => failure("There should be no value")
        case None => success
        case _ => failure("Should return None")
      }
    }
  }
}
