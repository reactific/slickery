package com.reactific.slickery

import java.sql.DriverManager

import com.typesafe.config.{ConfigFactory, Config}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

sealed trait SupportedDB[DriverType <: SlickeryDriver] extends SlickeryComponent {
  def jdbcDriverClassName: String

  /** Load JDBC Driver Class .. carefully
    * This needs to be done before we attempt to find the driver and we need to do it in such a way that we don't
    * attempt the Class.forName call while someone is holding the DriverManager's class lock because deadlock may
    * ensue. The Schema class invokes this method to ensure the JDBC Driver is loaded but may block if a Slick
    * Driver is registering the JDBC Driver or another Schema is attempting to fetch the JDBC Driver.
    * @return Class of the JDBC Driver for this supported DB
    */
  lazy val jdbcDriverClass = classOf[DriverManager].synchronized { Class.forName(jdbcDriverClassName) }

  def slickDriver: String

  def urlPrefix: String

  def driver: DriverType

  def config_name: String

  def defaultPort : Int

  def connectionTestUrl: String

  def kindName: String = getClass.getSimpleName.replaceAll("[$]", "")

  def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort, dir: String = "") : String = {
    if (dir.isEmpty)
      s"$urlPrefix://$host:$port/$dbName"
    else
      s"$urlPrefix://$host:$port/$dir/$dbName"
  }

  def makeDbConfigFor(
      dbName: String,
      host : String = "localhost",
      port : Int = defaultPort,
      dir : String = "",
      disableConnectionPool : Boolean = false
  ) : Config = {
    ConfigFactory.parseString(
      s"""$dbName {
         |  driver = "$slickDriver"
         |  db {
         |    ${if(disableConnectionPool){"connectionPool = disabled"}else{""}}
         |    driver = "$jdbcDriverClassName"
         |    url = "${makeConnectionUrl(dbName, host, port, dir)}"
         |  }
         |}""".stripMargin)
  }

  def testConnection: Boolean = Try {
    val clazz = jdbcDriverClass
    DriverManager.getConnection(connectionTestUrl)
  } match {
    case Success(conn) ⇒
      val db = slick.jdbc.JdbcBackend.Database.forURL(connectionTestUrl)
      try {
        val d = driver
        import d.api._
        val future = db.run {
          sql"SELECT 1".as[Int]
        }
        Await.result(future, 1.minute)
        log.info(s"Connection to $kindName @$connectionTestUrl is viable")
        true
      } catch {
        case xcptn: Throwable ⇒
          log.warn(s"Connection to $kindName @$connectionTestUrl is not viable: ${xcptn.getClass.getSimpleName + ": " + xcptn.getMessage}")
          false
      } finally {
        db.close
      }
    case Failure(xcptn) ⇒
      log.warn(s"Connection to $kindName @$connectionTestUrl is not viable: ${xcptn.getClass.getSimpleName + ": " + xcptn.getMessage}")
      false
  }
}

class H2_SupportedDB extends SupportedDB[H2Driver] {
  val jdbcDriverClassName = "org.h2.Driver"
  val slickDriver = "com.reactific.slickery.H2Driver$"
  val urlPrefix = "jdbc:h2"
  val driver = H2Driver
  val config_name = "h2"
  val connectionTestUrl = "jdbc:h2:mem:test"
  val defaultPort = 0
  override def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort, dir: String = "") : String = {
    val directory = if (dir.isEmpty) "./" else dir
    s"$urlPrefix:$directory$dbName"
  }

  override def makeDbConfigFor(
      dbName: String,
      host: String = "localhost",
      port: Int = defaultPort,
      dir: String = "",
      disableConnectionPool: Boolean = false
  ): Config = {
    super.makeDbConfigFor(dbName, host, port, dir, disableConnectionPool = true)
  }
}
case object H2 extends H2_SupportedDB

class MySQL_SupportedDB extends SupportedDB[MySQLDriver] {
  val jdbcDriverClassName = "com.mysql.jdbc.Driver"
  val slickDriver = "com.reactific.slickery.MySQLDriver$"
  val urlPrefix = "jdbc:mysql"
  val driver = MySQLDriver
  val config_name = "mysql"
  val defaultPort = 3306
  val connectionTestUrl = makeConnectionUrl(dbName="", host="localhost", port=defaultPort)
  override def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort, dir: String = "") : String = {
    super.makeConnectionUrl(dbName, host, port) + "?useSSL=false"
  }
}
case object MySQL extends MySQL_SupportedDB

class SQLite_SupportedDB extends SupportedDB[SQLiteDriver] {
  val jdbcDriverClassName = "org.sqlite.JDBC"
  val slickDriver = "com.reactific.slickery.SQLiteDriver$"
  val urlPrefix = "jdbc:sqlite"
  val driver = SQLiteDriver
  val config_name = "sqlite"
  val connectionTestUrl = "jdbc:sqlite:test"
  val defaultPort = 0
  override def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort, dir: String = "") : String = {
    s"$urlPrefix:$dir$dbName"
  }
}
case object SQLite extends SQLite_SupportedDB

class PostgresQL_SupportedDB extends SupportedDB[PostgresDriver] {
  val jdbcDriverClassName = "org.postgresql.Driver"
  val slickDriver = "com.reactific.slickery.PostgresDriver$"
  val urlPrefix = "jdbc:postgresql"
  val driver = PostgresDriver
  val config_name = "postgresql"
  val connectionTestUrl = "jdbc:postgresql://localhost:5432/postgres"
  val defaultPort = 5432
  override def makeConnectionUrl(dbName : String, host : String = "localhost", port : Int = defaultPort, dir: String = "") : String = {
    s"$urlPrefix://$host:$port/$dbName"
  }

}
case object PostgresQL extends PostgresQL_SupportedDB

object SupportedDB extends SlickeryComponent {
  def all  = Seq( H2, MySQL, SQLite, PostgresQL)

  def forConfig(path: String, config : Config = ConfigFactory.load() ) : Option[SupportedDB[_]] = {
    Try { config.getConfig(path) } match {
      case Success(dbConfig) ⇒ {
        if (dbConfig.hasPath("db.url") && dbConfig.hasPath("db.driver")) {
          Try {dbConfig.getString("driver")} match {
            case Success(driver) ⇒
              forDriverName(driver)
            case Failure(xcptn) ⇒
              log.warn(s"Error finding SupportedDB in configuration: ", xcptn)
              None
          }
        } else {
          log.warn(s"Configuration lacks 'db.url' and 'db.driver' paths")
          None
        }
      }
      case Failure(xcptn) ⇒
        log.warn(s"Error finding SupportedDB in configuration: ", xcptn)
        None
    }
  }

  def forDriverName(driverName: String) : Option[SupportedDB[_]] = {
    for (sdb <- all) {
      if (sdb.slickDriver.startsWith(driverName))
        return Some(sdb)
    }
    log.warn(s"SupportedDB for driver name '$driverName' not found.")
    None
  }

  def forJDBCUrl(url: String) : Option[SupportedDB[_]] = {
    for (sdb <- all) {
      if (url.startsWith(sdb.urlPrefix))
        return Some(sdb)
    }
    log.warn(s"SupportedDB for url '$url' not found.")
    None
  }
}
