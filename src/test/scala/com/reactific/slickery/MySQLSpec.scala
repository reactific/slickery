package com.reactific.slickery

import com.reactific.slickery.testkit.SlickerySpecification

import scala.concurrent.ExecutionContext.Implicits.global

class MySQLSchema(name : String)
  extends CommonTestSchema[MySQLDriver](name, name, MySQL.makeDbConfigFor(name,disableConnectionPool = true))

class MySQLSpec extends SlickerySpecification with CommonTests {


  "MySQLSpec" should {
    "support common extension types" in {
      WithMySQLSchema("MySQL_common")(new MySQLSchema(_)){ schema : MySQLSchema ⇒
        readAndWriteMappedTypes[MySQLDriver,MySQLSchema](schema)
      } must throwA[java.sql.SQLException]
      pending(": resolution of MySQL 'Access denied for user ''@'localhost' to database 'mysql_common' issue")
    }
  }
}
