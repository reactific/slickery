package com.reactific.slickery.testkit

import com.reactific.helpers.LoggingHelper

import scala.concurrent.ExecutionContext.Implicits.global

class SlickerySpecificationSpec extends SlickerySpecification {

  "SlickerySpecification" should {
    LoggingHelper.setToWarn("com.zaxxer.hikari.*")
    LoggingHelper.setToDebug("org.sqlite.*")
    LoggingHelper.setToDebug("com.mysql.jdbc.*")
    LoggingHelper.setToDebug("org.postgresql.*")

    "implement WithSchema for H2" in {
      WithH2Schema("WithH2Schema")(n ⇒ FakeH2Schema(n)) { schema ⇒
        success
      }
    }
    "implement WithSchema for PostgresQL" in {
      WithPostgresSchema("WithPGSchema")(n ⇒ FakePGSchema(n)) { schema ⇒
        success
      }
    }
    "implement WithSchema for SQLite" in {
      val result = WithSQLiteSchema("WithSQLiteSchema")(n ⇒ FakeSQLiteSchema(n)) { schema ⇒
        success
      }
      result.isError must beTrue
      pending("resolution of SQLite issue with returning a result set from an update")
    }
    "implement WithSchema for MySQL" in {
      val result = WithMySQLSchema("WithMySQLSchema")(n ⇒ FakeMySQLSchema(n)) { schema ⇒
        success
      }
      result.isError must beTrue
      pending("resolution of MySQL issue with access denied")
    }
  }
}

