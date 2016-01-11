package com.reactific.slickery

import com.reactific.slickery.testkit._

import scala.concurrent.ExecutionContext.Implicits.global

class SQLiteSchema(name : String)
  extends CommonTestSchema[SQLiteDriver](name, name, SQLite.makeDbConfigFor(name, disableConnectionPool = true))

class SQLiteSpec extends SlickerySpecification with CommonTests {

  "SQLiteSpec" should {
    "support common extended types" in {
      WithSQLiteSchema("SQLite_common")(new SQLiteSchema(_)) { schema : SQLiteSchema ⇒
        readAndWriteMappedTypes[SQLiteDriver,SQLiteSchema](schema)
      } must throwA[java.sql.SQLException]
      pending(": resolution of SQLite 'should not return a ResultSet' error")
    }
  }
}
