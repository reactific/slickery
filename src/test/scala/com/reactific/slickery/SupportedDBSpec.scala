package com.reactific.slickery

import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.mutable.Specification

class SupportedDBSpec extends Specification {

  "SupportedDB" should {
    "find H2" in {
      SupportedDB.forDriverName("com.reactific.slickery.H2Driver") must beEqualTo(Some(H2))
      SupportedDB.forJDBCUrl("jdbc:h2") must beEqualTo(Some(H2))
      SupportedDB.forConfig("foo", H2.makeDbConfigFor("foo")) must beEqualTo(Some(H2))
    }
    "find MySQL" in {
      SupportedDB.forDriverName("com.reactific.slickery.MySQLDriver") must beEqualTo(Some(MySQL))
      SupportedDB.forJDBCUrl("jdbc:mysql") must beEqualTo(Some(MySQL))
      SupportedDB.forConfig("foo", MySQL.makeDbConfigFor("foo")) must beEqualTo(Some(MySQL))
    }
    "find SQLite" in {
      SupportedDB.forDriverName("com.reactific.slickery.SQLiteDriver") must beEqualTo(Some(SQLite))
      SupportedDB.forJDBCUrl("jdbc:sqlite") must beEqualTo(Some(SQLite))
      SupportedDB.forConfig("foo", SQLite.makeDbConfigFor("foo")) must beEqualTo(Some(SQLite))
    }
    "find Postgres" in {
      SupportedDB.forDriverName("com.reactific.slickery.PostgresDriver") must beEqualTo(Some(PostgresQL))
      SupportedDB.forJDBCUrl("jdbc:postgresql:") must beEqualTo(Some(PostgresQL))
      SupportedDB.forConfig("foo", PostgresQL.makeDbConfigFor("foo")) must beEqualTo(Some(PostgresQL))
    }
    "not find non-existent db" in {
      SupportedDB.forDriverName("foo") must beEqualTo(None)
      SupportedDB.forJDBCUrl("jdbc:db2") must beEqualTo(None)
      SupportedDB.forConfig("bar", H2.makeDbConfigFor("foo")) must beEqualTo(None)
      SupportedDB.forConfig("foo", ConfigFactory.parseString("{}")) must beEqualTo(None)
      SupportedDB.forConfig("foo", ConfigFactory.parseString(
        """{ "foo" : { "driver" : "nada", "db" : { "url" : "", "driver" : ""}}}""")) must beEqualTo(None)
      SupportedDB.forConfig("foo", ConfigFactory.parseString(
        """{ "foo" : { "driver" : "com.reactific.slickery.H2Driver$", db : {}}}""")) must beEqualTo(None)
    }
    "provide makeSchema for each" in {
      H2.driver.makeSchema("h2-makeSchema")
      MySQL.driver.makeSchema("mysql-makeSchema")
      SQLite.driver.makeSchema("sqlite-makeSchema")
      PostgresQL.driver.makeSchema("postgres-makeSchema")
      success
    }
  }

  "H2" should {
    "have specific constant values" in {
      H2.jdbcDriverClassName must beEqualTo("org.h2.Driver")
      H2.slickDriver must beEqualTo("com.reactific.slickery.H2Driver$")
      H2.urlPrefix must beEqualTo("jdbc:h2")
      H2.driver must beEqualTo(H2Driver)
      H2.config_name must beEqualTo("h2")
      H2.connectionTestUrl must beEqualTo("jdbc:h2:mem:test")
      H2.defaultPort must beEqualTo(0)
    }
    "fill in the db config correctly" in {
      val expected = ConfigFactory.parseString(
        s"""foo {
            |  driver = "com.reactific.slickery.H2Driver$$"
            |  db {
            |    driver = "org.h2.Driver"
            |    url = "${H2.makeConnectionUrl("foo", "bar", 42)}"
            |  }
            |}""".stripMargin
      )
      H2.makeDbConfigFor("foo", "bar", 42) must beEqualTo(expected)
    }
  }

  "MySQL" should {
    "have specific constant values" in {
      MySQL.jdbcDriverClassName must beEqualTo("com.mysql.jdbc.Driver")
      MySQL.slickDriver must beEqualTo("com.reactific.slickery.MySQLDriver$")
      MySQL.urlPrefix must beEqualTo("jdbc:mysql")
      MySQL.driver must beEqualTo(MySQLDriver)
      MySQL.config_name must beEqualTo("mysql")
      MySQL.connectionTestUrl must beEqualTo("jdbc:mysql://localhost:3306/?useSSL=false")
      MySQL.defaultPort must beEqualTo(3306)
    }
    "fill in the db config correctly" in {
      val expected = ConfigFactory.parseString(
        s"""foo {
            |  driver = "com.reactific.slickery.MySQLDriver$$"
            |  db {
            |    driver = "com.mysql.jdbc.Driver"
            |    url = "${MySQL.makeConnectionUrl("foo", "bar", 42)}"
            |  }
            |}""".stripMargin
      )
      MySQL.makeDbConfigFor("foo", "bar", 42) must beEqualTo(expected)
    }
  }

  "SQLite" should {
    "have specific constant values" in {
      SQLite.jdbcDriverClassName must beEqualTo("org.sqlite.JDBC")
      SQLite.slickDriver must beEqualTo("com.reactific.slickery.SQLiteDriver$")
      SQLite.urlPrefix must beEqualTo("jdbc:sqlite")
      SQLite.driver must beEqualTo(SQLiteDriver)
      SQLite.config_name must beEqualTo("sqlite")
      SQLite.connectionTestUrl must beEqualTo("jdbc:sqlite:test")
      SQLite.defaultPort must beEqualTo(0)
    }
    "fill in the db config correctly" in {
      val expected = ConfigFactory.parseString(
        s"""foo {
            |  driver = "com.reactific.slickery.SQLiteDriver$$"
            |  db {
            |    driver = "org.sqlite.JDBC"
            |    url = "${SQLite.makeConnectionUrl("foo", "bar", 42)}"
            |  }
            |}""".stripMargin
      )
      SQLite.makeDbConfigFor("foo", "bar", 42) must beEqualTo(expected)
    }
  }

  "PostgresQL" should {
    "have specific constant values" in {
      PostgresQL.jdbcDriverClassName must beEqualTo("org.postgresql.Driver")
      PostgresQL.slickDriver must beEqualTo("com.reactific.slickery.PostgresDriver$")
      PostgresQL.urlPrefix must beEqualTo("jdbc:postgresql")
      PostgresQL.driver must beEqualTo(PostgresDriver)
      PostgresQL.config_name must beEqualTo("postgresql")
      PostgresQL.connectionTestUrl must beEqualTo("jdbc:postgresql://localhost:5432/test")
      PostgresQL.defaultPort must beEqualTo(5432)
    }
    "fill in the db config correctly" in {
      val expected = ConfigFactory.parseString(
        s"""foo {
            |  driver = "com.reactific.slickery.PostgresDriver$$"
            |  db {
            |    driver = "org.postgresql.Driver"
            |    url = "${PostgresQL.makeConnectionUrl("foo", "bar", 42)}"
            |  }
            |}""".stripMargin
      )
      PostgresQL.makeDbConfigFor("foo", "bar", 42) must beEqualTo(expected)
    }
  }
}
