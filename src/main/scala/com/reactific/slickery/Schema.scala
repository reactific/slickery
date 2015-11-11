/**********************************************************************************************************************
 *                                                                                                                    *
 * Copyright (c) 2013, Reactific Software LLC. All Rights Reserved.                                                   *
 *                                                                                                                    *
 * Scrupal is free software: you can redistribute it and/or modify it under the terms                                 *
 * of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License,   *
 * or (at your option) any later version.                                                                             *
 *                                                                                                                    *
 * Scrupal is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more      *
 * details.                                                                                                           *
 *                                                                                                                    *
 * You should have received a copy of the GNU General Public License along with Scrupal. If not, see either:          *
 * http://www.gnu.org/licenses or http://opensource.org/licenses/GPL-3.0.                                             *
 **********************************************************************************************************************/

package com.reactific.slickery

import java.sql.{Clob, Timestamp}
import java.time.{Duration, Instant}

import com.reactific.helpers.LoggingHelper
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json.{Json, JsObject}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable
import slick.profile.SqlProfile.ColumnOption.{Nullable, NotNull}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

/**
 * The abstract database component.
 * This trait allows use to define database components which are simply collections of related tables and the
 * various query methods on those tables to provide access to them. Since Components contain Tables and Scrupal requires
 * all database entities to have a particular shape, that shape is enforced in the EntityTable class. Note that
 * Component extends Sketch which is mixed in to other components but resolved by the Schema class.
 */
abstract class Schema(val schemaName: String, val config_name: String, config : Config = ConfigFactory.load)
  (implicit ec: ExecutionContext) extends LoggingHelper  {

  lazy val dbConfig = DatabaseConfig.forConfig[JdbcProfile](config_name, config)

  import dbConfig._
  import dbConfig.driver.{DriverAction,StreamingDriverAction,ReturningInsertActionComposer,SchemaDescription}
  import dbConfig.driver.api._

  protected def validateExistingTables( tables: Vector[MTable] ) : Seq[Throwable] = Seq.empty[Throwable]

  final def metaTables() : Future[Vector[MTable]] = {
    db.run { MTable.getTables(None, Some(schemaName), None, None) }
  }

  final def validate() : Future[Seq[Throwable]] = {
    metaTables().map[Seq[Throwable]] { tables : Vector[MTable] =>
      validateExistingTables(tables)
    }
  }

  def schemas : SchemaDescription

  final def create() : Future[Unit] = {
    SupportedDatabase.forDriverName(dbConfig.driverName) map { sdb =>
        val extensions = dbConfig.driver.createSchemaActionExtensionMethods(schemas)
        db.run {
          DBIO.seq(
            sdb.makeSchema(dbConfig.driver, schemaName),
            extensions.create
          )
        }
    } getOrElse Future.failed(
      new java.sql.SQLException(s"Unsupported Database Driver: ${dbConfig.driverName}")
    )
  }

  final def drop() : Future[Unit] = {
    val extensions = dbConfig.driver.createSchemaActionExtensionMethods(schemas)
    db.run { extensions.drop }
  }

  trait CRUDQueries[R,ID, T<:TableRow[R]] { self : TableQuery[T] =>
    type CreateResult = DriverAction[ReturningInsertActionComposer[T,Long]#SingleInsertResult,NoStream,Effect.Write]
    type RetrieveResult =
      StreamingDriverAction[Seq[T#TableElementType],R,Effect.Read]#ResultAction[Option[R],NoStream,Effect.Read]
    type UpdateResult = DriverAction[Int,NoStream,Effect.Write]
    type DeleteResult = DriverAction[Int,NoStream,Effect.Write]

    def create(entity: R) : CreateResult
    def retrieve(id : ID) : RetrieveResult
    def update(entity: R) : UpdateResult
    def delete(id: ID) : DeleteResult
  }

  abstract class TableRow[S](tag: Tag, tableName: String) extends Table[S](tag, Some(schemaName), tableName) {
    def fullName : String = {
      val schemaPrefix = { schemaName.map { n => n + "." } getOrElse "" }
      s"$schemaPrefix$tableName"
    }
    protected def nm(columnName: String) : String = s"${fullName}_$columnName"
    protected def fkn(foreignTableName: String ) : String = nm( foreignTableName + "_fkey")
    protected def idx(name: String) : String = nm(name + "_idx")
  }

  trait StorableRow[S <: Storable] extends TableRow[S] {
    def id = column[Long](nm("id"), O.PrimaryKey, O.AutoInc, NotNull)
  }

  trait StorableQuery[S <: Storable, T <:StorableRow[S]] extends CRUDQueries[S,Long,T] {
    self : TableQuery[T] =>
    val query = self
    lazy val byIdQuery = Compiled { idToFind : Rep[Long] => this.filter(_.id === idToFind) }
    def byId(idToFind : Long) = byIdQuery(idToFind).result.headOption

    override def create(entity: S) = (this returning this.map(_.id)) += entity
    override def retrieve(id: Long) = byId(id)
    override def update(entity: S) = byIdQuery(entity.getId).update(entity)
    override def delete(id: Long) = byIdQuery(id).delete
  }

  implicit lazy val instantMapper = MappedColumnType.base[Instant,Timestamp](
  { i => new Timestamp( i.toEpochMilli ) },
  { t => Instant.ofEpochMilli(t.getTime) }
  )

  implicit lazy val regexMapper = MappedColumnType.base[Regex, String] (
  { r => r.pattern.pattern() },
  { s => new Regex(s) }
  )

  implicit lazy val durationMapper = MappedColumnType.base[Duration,Long] (
  { d => d.toMillis },
  { l => Duration.ofMillis(l) }
  )

  implicit lazy val symbolMapper = MappedColumnType.base[Symbol,String] (
  { s => s.name},
  { s => Symbol(s) }
  )

  implicit lazy val jsObjectMapper = MappedColumnType.base[JsObject,Clob] (
  { jso =>
    val clob = dbConfig.db.createSession().conn.createClob()
    val str = Json.stringify(jso)
    clob.setString(1, str)
    clob
  },
  { clob =>
    Json.parse(clob.getAsciiStream).asInstanceOf[JsObject]
  }
  )

  trait CreatableRow[S <: Creatable] extends StorableRow[S] {
    def created = column[Option[Instant]](nm("created"), Nullable)
    def created_index = index(idx("created"), created, unique = false)
  }

  trait CreatableQuery[S <: Creatable, T <:CreatableRow[S]] extends StorableQuery[S,T] { self : TableQuery[T] =>
    lazy val createdSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.created >= since) }
    def createdSince(since: Instant) = createdSinceQuery(since).result
  }

  trait ModifiableRow[S <: Modifiable] extends StorableRow[S] {
    def modified = column[Option[Instant]](nm("modified"), Nullable)
    def modified_index = index(idx("modified"), modified, unique = false)
  }

  trait ModifiableQuery[S <: Modifiable, T <:ModifiableRow[S]] extends StorableQuery[S,T] { self : TableQuery[T] =>
    lazy val modifiedSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.modified >= since) }
    def modifiedSince(since: Instant) = modifiedSinceQuery(since).result
  }

  trait ExpirableRow[S <: Expirable] extends StorableRow[S] {
    def expired = column[Option[Instant]](nm("expired"), Nullable)
    def expired_index = index(idx("expired"), expired, unique = false)
  }
  trait ExpirableQuery[S <: Expirable, T <: ExpirableRow[S]] extends StorableQuery[S,T] { self: TableQuery[T] =>
    lazy val expiredSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.expired <= since )}
    def expiredSince(since: Instant) = expiredSinceQuery(since).result
  }

  trait NameableRow[S <: Nameable] extends StorableRow[S] {
    def name = column[String](nm("name"), NotNull)
    def name_index = index(idx("name"), name, unique = true)
  }

  trait NameableQuery[S <: Nameable, T <: NameableRow[S]] extends StorableQuery[S,T] { self : TableQuery[T] =>
    lazy val byNameQuery = Compiled { aName : Rep[String] => this.filter(_.name === aName) }
    def byName(name: String) = byNameQuery(name).result
  }

  trait DescribableRow[S <: Describable] extends StorableRow[S] {
    def description = column[String](nm("description"), NotNull)
  }

  trait DescribableQuery[S <: Describable, T <: DescribableRow[S]] extends StorableQuery[S,T] { self : TableQuery[T] =>
    lazy val byDescriptionQuery = Compiled { desc : Rep[String] => this.filter(_.description === desc) }
    def byDescription(name: String) = byDescriptionQuery(name).result
  }

  abstract class UseableRow[S <: Useable](tag : Tag, name: String) extends TableRow[S](tag, name)
    with StorableRow[S] with CreatableRow[S] with ModifiableRow[S]
    with ExpirableRow[S] with NameableRow[S] with DescribableRow[S]

  abstract class UseableQuery[S <: Useable, T <: UseableRow[S]](cons : Tag => T) extends TableQuery[T](cons)
    with StorableQuery[S,T] with CreatableQuery[S,T] with ModifiableQuery[S,T]
    with ExpirableQuery[S,T] with NameableQuery[S,T] with DescribableQuery[S,T] {
  }

  /**
   * The base class of all correlation tables.
   * This allows many-to-many relationships to be established by simply listing the pairs of IDs
   */
  abstract class ManyToManyRow[
    A <: Storable, TA <: StorableRow[A],
    B <: Storable, TB <: StorableRow[B]](
    tag : Tag, tableName:String,
    nameA: String, queryA: StorableQuery[A,TA],
    nameB: String, queryB: StorableQuery[B,TB]) extends TableRow[(Long,Long)](tag, tableName) {

    def a_id = column[Long](nm(nameA + "_id"))

    def b_id = column[Long](nm(nameB + "_id"))

    def a_fkey = foreignKey(fkn(nameA), a_id, queryA.query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def b_fkey = foreignKey(fkn(nameB), b_id, queryB.query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def a_b_uniqueness = index(idx(nameA + "_" + nameB), (a_id, b_id), unique = true)
    def * = (a_id, b_id)
  }

  abstract class ManyToManyQuery[
    A <: Storable, TA <: StorableRow[A],
    B <: Storable, TB <: StorableRow[B],
    T <: ManyToManyRow[A,TA,B,TB]](cons: Tag => T) extends TableQuery[T](cons) {
    lazy val findAsQuery = Compiled { bId : Rep[Long] => this.filter (_.b_id === bId ) }
    lazy val findBsQuery = Compiled { aId : Rep[Long] => this.filter (_.a_id === aId ) }
    def findAssociatedA(id: Long) : DBIOAction[Seq[Long], NoStream, Effect.Read] =
      findAsQuery(id).result.map { s : Seq[(Long,Long)] => s.map { p : (Long,Long) => p._1 } }
    def findAssociatedA(b: B) : DBIOAction[Seq[Long], NoStream, Effect.Read]  = findAssociatedA(b.getId)
    def findAssociatedB(id: Long) : DBIOAction[Seq[Long], NoStream, Effect.Read] =
      findBsQuery(id).result.map { s : Seq[(Long,Long)] => s.map { p : (Long,Long) => p._2 } }
    def findAssociatedB(a: A) : DBIOAction[Seq[Long], NoStream, Effect.Read] = findAssociatedB(a.getId)
    def associate(a: A, b: B) = this += (a.getId, b.getId)
  }

}

