package com.reactific.slickery

/** Test cases for SupportedDatabase.
  *
  * Description of thing
  */
class SupportedDatabaseSpec extends SlickerySpec {

  "SupportedDatabases" should {
    "find H2" in {
      SupportedDatabase.forDriverName("slick.driver.H2Driver") must beEqualTo(Some(H2))
      SupportedDatabase.forJDBCUrl("jdbc:h2") must beEqualTo(Some(H2))
    }
    "find MySQL" in {
      SupportedDatabase.forDriverName("slick.driver.MySQLDriver") must beEqualTo(Some(MySQL))
      SupportedDatabase.forJDBCUrl("jdbc:mysql") must beEqualTo(Some(MySQL))
    }
    "find SQLite" in {
      SupportedDatabase.forDriverName("slick.driver.SQLiteDriver") must beEqualTo(Some(SQLite))
      SupportedDatabase.forJDBCUrl("jdbc:sqllite") must beEqualTo(Some(SQLite))
    }
    "find Postgres" in {
      SupportedDatabase.forDriverName("slick.driver.PostgresDriver") must beEqualTo(Some(PostgresQL))
      SupportedDatabase.forJDBCUrl("jdbc:postgresql:") must beEqualTo(Some(PostgresQL))
    }
    "not find non-existent db" in {
      SupportedDatabase.forDriverName("foo") must beEqualTo(None)
      SupportedDatabase.forJDBCUrl("jdbc:db2") must beEqualTo(None)
    }
    "provide makeSchema for each" in {
      val profile = jdbcProfile("supported_db")
      H2.makeSchema(profile, "h2-makeSchema")
      MySQL.makeSchema(profile, "mysql-makeSchema")
      SQLite.makeSchema(profile, "sqlite-makeSchema")
      PostgresQL.makeSchema(profile, "postgres-makeSchema")
      success
    }
  }
}
