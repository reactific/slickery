package com.reactific.slickery.testkit

import java.io.File

import com.reactific.helpers.testkit.HelperSpecification
import com.reactific.slickery._
import com.typesafe.config.Config
import org.specs2.execute.{Result, AsResult}

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._


trait SlickerySpecification extends HelperSpecification {

  def slickeryTestDir = "target/slickeryDB"

  def ensureTestDirectoryExists() : Unit = {
    val file = new File(slickeryTestDir)
    if (!file.isDirectory) {
      if (file.exists)
        toss(s"Test database path, $slickeryTestDir should be a directory.")
      else
        file.mkdirs()
    }
  }

  def H2Config(dbName : String) : Config = {
    H2.makeDbConfigFor(dbName, dir=s"./$slickeryTestDir/", disableConnectionPool = true)
  }
  def PostgresConfig(dbName : String) : Config = {
    PostgresQL.makeDbConfigFor(dbName, disableConnectionPool = true)
  }
  def SQLiteConfig(dbName : String) : Config = {
    SQLite.makeDbConfigFor(dbName, dir=s"$slickeryTestDir/", disableConnectionPool = true)
  }
  def MySQLConfig(dbName : String) : Config = {
    MySQL.makeDbConfigFor(dbName, dir=slickeryTestDir, disableConnectionPool = true)
  }

  def FakeH2Schema(name : String)(implicit ec: ExecutionContext) = {
    new Schema[H2Driver](name,name,H2Config(name)) {
      import driver.SchemaDescription
      override def schemas: Map[String,SchemaDescription] = Map.empty[String,SchemaDescription]
    }
  }

  def FakePGSchema(name : String)(implicit ec : ExecutionContext)  = {
    new Schema[PostgresDriver](name,name,PostgresConfig(name)) {
      import driver.SchemaDescription
      override def schemas: Map[String,SchemaDescription] = Map.empty[String,SchemaDescription]
    }
  }

  def FakeMySQLSchema(name : String)(implicit ec : ExecutionContext)  = {
    new Schema[MySQLDriver](name,name,MySQLConfig(name)) {
      import driver.SchemaDescription
      override def schemas: Map[String,SchemaDescription] = Map.empty[String,SchemaDescription]
    }
  }

  def FakeSQLiteSchema(name : String)(implicit ec : ExecutionContext)  = {
    new Schema[SQLiteDriver](name,name,SQLiteConfig(name)) {
      import driver.SchemaDescription
      override def schemas: Map[String,SchemaDescription] = Map.empty[String,SchemaDescription]
    }
  }

  object WithSchema {
    ensureTestDirectoryExists()
    def apply[D <: SlickeryDriver, ST <: Schema[D], R](dbName: String)(createSchema: ⇒ ST)(f : (ST) ⇒ R)
      (implicit ec : ExecutionContext, ev : AsResult[R]) : Result = {
      val theSchema = createSchema
      val future = theSchema.driver.createDatabase(dbName, theSchema.db).flatMap { wasCreated: Boolean ⇒
        if (wasCreated) {
          theSchema.create().map { u ⇒
            try {
              AsResult(f(theSchema))
            } finally {
              val future = theSchema.drop().flatMap { u ⇒
                theSchema.driver.dropDatabase(dbName, theSchema.db)
              }
              Await.result(future, 10.seconds)
            }
          }
        } else {
          Future.successful { failure("Failed to create schema") }
        }
      }
      Await.result(future, 10.seconds)
    }
  }

  object WithH2Schema {
    def apply[H2S <: Schema[H2Driver], R](dbName : String)(createSchema : (String) ⇒ H2S)(f : (H2S) ⇒ R)
      (implicit ec : ExecutionContext, ev : AsResult[R]) : Result = {
      WithSchema[H2Driver,H2S,R](dbName)(createSchema(dbName))(f)
    }
  }

  object WithPostgresSchema {
    def apply[PGS <: Schema[PostgresDriver],R](dbName : String)(createSchema : (String) ⇒ PGS)(f : (PGS) ⇒ R)
      (implicit ec : ExecutionContext, ev : AsResult[R]) : Result = {
      WithSchema[PostgresDriver,PGS,R](dbName)(createSchema(dbName))(f)
    }
  }

  object WithSQLiteSchema {
    def apply[SLS <: Schema[SQLiteDriver], R](dbName : String)(createSchema : (String) ⇒ SLS)(f : (SLS) ⇒ R)
      (implicit ec : ExecutionContext, ev : AsResult[R]) : Result = {
      WithSchema[SQLiteDriver,SLS,R](dbName)(createSchema(dbName))(f)
    }
  }

  object WithMySQLSchema {
    def apply[MSS <: Schema[MySQLDriver], R](dbName : String)(createSchema : (String) ⇒ MSS)(f : (MSS) ⇒ R)
      (implicit ec : ExecutionContext, ev : AsResult[R]) : Result = {
      WithSchema[MySQLDriver,MSS,R](dbName)(createSchema(dbName))(f)
    }
  }
}
