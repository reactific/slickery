package com.reactific.slickery

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import org.specs2.mutable.Specification

class SQLiteSpec extends Specification with LoggingHelper with FutureHelper {

  lazy val sqliteIsViable : Boolean = SQLite.testConnection

  "SQLiteSpec" should {
    "be viable" in {
      sqliteIsViable must beTrue
    }
  }
}
