package com.reactific.slickery

import com.reactific.slickery.testkit._

import scala.concurrent.ExecutionContext.Implicits.global

class SQLiteSchema(name : String) extends CommonTestSchema[SQLiteDriver](name, name, SQLite.makeDbConfigFor(name))

class SQLiteSpec extends SlickerySpecification with CommonTests {

  "SQLiteSpec" should {
    "support common extended types" in {
      val result = WithSQLiteSchema("SQLite_common")(new SQLiteSchema(_)) { schema : SQLiteSchema ⇒
        readAndWriteMappedTypes[SQLiteDriver,SQLiteSchema](schema)
      }
      result.isError must beTrue
      pending(": resolution of SQLite 'should not return a ResultSet' error")
    }
  }
}
