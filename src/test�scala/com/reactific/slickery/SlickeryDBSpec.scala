package com.reactific.slickery

/** Test cases for SupportedDatabase.
  *
  * Description of thing
  */
class SlickeryDBSpec extends SlickerySpec {

  "SlickeryDB" should {
    "find H2" in {
      SupportedDB.forDriverName("com.reactific.slickery.H2Driver") must beEqualTo(Some(H2))
      SupportedDB.forJDBCUrl("jdbc:h2") must beEqualTo(Some(H2))
    }
    "find MySQL" in {
      SupportedDB.forDriverName("com.reactific.slickery.MySQLDriver") must beEqualTo(Some(MySQL))
      SupportedDB.forJDBCUrl("jdbc:mysql") must beEqualTo(Some(MySQL))
    }
    "find SQLite" in {
      SupportedDB.forDriverName("com.reactific.slickery.SQLiteDriver") must beEqualTo(Some(SQLite))
      SupportedDB.forJDBCUrl("jdbc:sqllite") must beEqualTo(Some(SQLite))
    }
    "find Postgres" in {
      SupportedDB.forDriverName("com.reactific.slickery.PostgresDriver") must beEqualTo(Some(PostgresQL))
      SupportedDB.forJDBCUrl("jdbc:postgresql:") must beEqualTo(Some(PostgresQL))
    }
    "not find non-existent db" in {
      SupportedDB.forDriverName("foo") must beEqualTo(None)
      SupportedDB.forJDBCUrl("jdbc:db2") must beEqualTo(None)
    }
    "provide makeSchema for each" in {
      H2.driver.makeSchema("h2-makeSchema")
      MySQL.driver.makeSchema("mysql-makeSchema")
      SQLite.driver.makeSchema("sqlite-makeSchema")
      PostgresQL.driver.makeSchema("postgres-makeSchema")
      success
    }
  }
}
