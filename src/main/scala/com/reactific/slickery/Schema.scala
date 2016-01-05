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

import java.time.Instant

import com.typesafe.config.{Config, ConfigFactory}
import slick.backend.DatabaseConfig
import slick.jdbc.ResultSetAction

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
 * The abstract database component.
 * This trait allows use to define database components which are simply collections of related tables and the
 * various query methods on those tables to provide access to them. Since Components contain Tables and Scrupal requires
 * all database entities to have a particular shape, that shape is enforced in the EntityTable class. Note that
 * Component extends Sketch which is mixed in to other components but resolved by the Schema class.
 */
abstract class Schema[DRVR <: SlickeryDriver](
  schemaNamePrototype: String,
  val databaseKind : SupportedDB[DRVR],
  configPath : String,
  config : Config = ConfigFactory.load)
  (implicit ec: ExecutionContext, classTag : ClassTag[DRVR]) extends SlickeryComponent {

  val schemaName = schemaNamePrototype.replaceAll("[ $!@#%^&*~`]", "_")
  val dbConfig = DatabaseConfig.forConfig[DRVR](configPath, config)
  val driver = dbConfig.driver
  import driver.api._

  val db = dbConfig.db

  import driver.{DriverAction,StreamingDriverAction,ReturningInsertActionComposer,SchemaDescription}
  import slick.jdbc.meta.MTable
  import slick.profile.SqlProfile.ColumnOption.{Nullable, NotNull}

  protected def validateExistingTables( tables: Seq[MTable] ) : Seq[Throwable] = {
    val theSchemas = schemas
    def checkValidity(mtable: MTable) : Option[Throwable] = {
      mtable.name.schema match {
        case Some(sName) if sName != schemaName ⇒
          Some(mkThrowable(s"Table $sName.${mtable.name.name} is not part of schema $schemaName"))
        case Some(sName) ⇒
          if (!theSchemas.contains(mtable.name.name)) {
            Some(mkThrowable(s"Spurious table ${mtable.name.name} found in schema $schemaName"))
          } else {
            None
          }
        case None ⇒
          Some(mkThrowable(s"Table ${mtable.name.name} has no schema name"))
      }
    }
    def checkSchema(name: String, sd: SchemaDescription) : Option[Throwable] = {
      if (tables.exists { mtable ⇒ mtable.name.name == name})
        None
      else
        Some(mkThrowable(s"Required table $name is missing"))
    }
    val tableChecks = for (mtable ← tables; error <- checkValidity(mtable)) yield { error }
    val schemaChecks = for ((name,sd) ← theSchemas; error ← checkSchema(name,sd)) yield { error }
    tableChecks.toSeq ++ schemaChecks
  }

  final def schemaNames() : Future[Seq[String]] = {
    db.run { ResultSetAction[String](_.metaData.getSchemas() ) { r => r.nextString() } }
  }

  final def metaTables() : Future[Seq[MTable]] = {
    db.run {
      MTable.getTables(None, Some(schemaName), None, None)
    }
  }

  final def validate() : Future[Seq[Throwable]] = {
    metaTables().map { tables : Seq[MTable] =>
      validateExistingTables(tables)
    }
  }

  def schemas : Map[String,SchemaDescription]

  private val nullSD : SchemaDescription = null

  final def create() : Future[Unit] = {
    db.run {
      driver.makeSchema(schemaName).flatMap { rows ⇒
        MTable.getTables(None, Some(schemaName), None, None).flatMap[Unit,NoStream,Effect.Schema] { tables ⇒
          log.debug(s"Existing Tables: ${tables.map{_.name}}")
          val statements = for (
            (name, sd) ← schemas if !tables.exists { mt ⇒ mt.name.name == name }
          ) yield sd
          log.debug(s"Schema creation statements: $statements")
          val ddl : SchemaDescription = statements.foldLeft(nullSD) {
            case (accum, statement) ⇒
              if (accum == null)
                statement
              else
                accum ++ statement
          }
          if (ddl != null) {
            val extensions = driver.createSchemaActionExtensionMethods(ddl)
            extensions.create
          } else {
            sqlu"".map { i ⇒ () }
          }
        }
      }
    }
  }

  final def drop() : Future[Unit] = {
    db.run {
      val ddl : SchemaDescription = schemas.values.foldLeft(nullSD) {
        case (accum, statement) ⇒
          if (accum == null)
            statement
          else
            accum ++ statement
      }
      if (ddl != null) {
        val extensions = driver.createSchemaActionExtensionMethods(ddl)
        extensions.drop
      } else {
        toss("No DDL Statements In Schema To Drop")
      }
    }
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

  type OIDType = Storable.OIDType

  abstract class TableRow[S](tag: Tag, tableName: String) extends Table[S](tag, Some(schemaName), tableName) {
    def fullName : String = {
      val schemaPrefix = { schemaName.map { n => n + "." } getOrElse "" }
      s"$schemaPrefix$tableName"
    }
    protected def nm(columnName: String) : String = s"${fullName}_$columnName"
    protected def fkn(foreignTableName: String ) : String = nm( foreignTableName + "_fkey")
    protected def idx(name: String) : String = nm(name + "_idx")
  }

  abstract class StorableRow[S <: Storable](tag: Tag, tableName: String) extends TableRow[S](tag, tableName) {
    def oid = column[OIDType](nm("oid"), O.PrimaryKey, O.AutoInc, NotNull)
  }

  abstract class StorableQuery[S <: Storable, T <:StorableRow[S]](cons : Tag => T)
    extends TableQuery[T](cons) with CRUDQueries[S,OIDType,T] {
    val query = this
    lazy val byIdQuery = { this.findBy(_.oid) }
    def byId(idToFind : OIDType) = {
      byIdQuery(idToFind).result.headOption
    }

    override def create(entity: S)  : CreateResult = {
      (this returning this.map(_.oid)) += entity
    }
    override def retrieve(id: OIDType) : RetrieveResult = {
      byId(id)
    }
    override def update(entity: S)  : UpdateResult = {
      byIdQuery(entity.getId).update(entity)
    }
    override def delete(oid: OIDType)  : DeleteResult = {
      byIdQuery(oid).delete
    }
    def runCreate(entity: S)   : Future[OIDType] = db.run { this.create(entity) }
    def runRetrieve(oid : OIDType): Future[Option[S]] = db.run { this.retrieve(oid) }
    def runUpdate(entity: S)   : Future[Int] = db.run { this.update(entity) }
    def runDelete(oid: OIDType)   : Future[Int] = db.run { this.delete(oid) }
  }

  implicit val instantMapper = driver.instantMapper

  trait CreatableRow[S <: Creatable] extends StorableRow[S] {
    def created = column[Instant](nm("created"), Nullable)
    def created_index = index(idx("created"), created, unique = false)
  }

  trait CreatableQuery[S <: Creatable, T <:CreatableRow[S]] extends StorableQuery[S,T] {
    lazy val createdSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.created >= since) }
    def createdSince(since: Instant) = createdSinceQuery(since).result
  }

  trait ModifiableRow[S <: Modifiable] extends StorableRow[S] {
    def modified = column[Instant](nm("modified"), Nullable)
    def modified_index = index(idx("modified"), modified, unique = false)
  }

  trait ModifiableQuery[S <: Modifiable, T <:ModifiableRow[S]] extends StorableQuery[S,T] {
    lazy val modifiedSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.modified >= since) }
    def modifiedSince(since: Instant) = modifiedSinceQuery(since).result
  }

  trait ExpirableRow[S <: Expirable] extends StorableRow[S] {
    def expiresAt = column[Instant](nm("expiresAt"), Nullable)
    def expiresAt_index = index(idx("expiresAt"), expiresAt, unique = false)
  }

  trait ExpirableQuery[S <: Expirable, T <: ExpirableRow[S]] extends StorableQuery[S,T] {
    lazy val expiredSinceQuery = Compiled { since : Rep[Instant] => this.filter(_.expiresAt <= since ) }
    def expiredSince(since: Instant) = expiredSinceQuery(since).result
  }

  trait NameableRow[S <: Nameable] extends StorableRow[S] {
    def name = column[String](nm("name"), NotNull)
    def name_index = index(idx("name"), name, unique = true)
  }

  trait NameableQuery[S <: Nameable, T <: NameableRow[S]] extends StorableQuery[S,T] {
    lazy val byNameQuery = Compiled { aName : Rep[String] => this.filter(_.name === aName) }
    def byName(name: String) = byNameQuery(name).result
  }

  trait DescribableRow[S <: Describable] extends StorableRow[S] {
    def description = column[String](nm("description"), NotNull)
  }

  trait DescribableQuery[S <: Describable, T <: DescribableRow[S]] extends StorableQuery[S,T] {
    lazy val byDescriptionQuery = Compiled { desc : Rep[String] => this.filter(_.description === desc) }
    def byDescription(name: String) = byDescriptionQuery(name).result
  }

  abstract class SlickeryRow[S <: Slickery](tag : Tag, name: String) extends StorableRow[S](tag, name)
    with CreatableRow[S] with ModifiableRow[S] with NameableRow[S] with DescribableRow[S]

  abstract class SlickeryQuery[S <: Slickery, T <: SlickeryRow[S]](cons : Tag => T) extends StorableQuery[S,T](cons)
    with CreatableQuery[S,T] with ModifiableQuery[S,T] with NameableQuery[S,T] with DescribableQuery[S,T]

  abstract class ExpirableSlickeryRow[S <: Slickery with Expirable](tag : Tag, name : String)
    extends SlickeryRow[S](tag, name) with ExpirableRow[S]

  abstract class ExpirableSlickeryQuery[S <: Slickery with Expirable, T <: ExpirableSlickeryRow[S]](cons : Tag ⇒ T)
    extends SlickeryQuery[S,T](cons) with ExpirableQuery[S,T]

  /**
   * The base class of all correlation tables.
   * This allows many-to-many relationships to be established by simply listing the pairs of IDs
   */
  abstract class ManyToManyRow[
    A <: Storable, TA <: StorableRow[A],
    B <: Storable, TB <: StorableRow[B]](
    tag : Tag, tableName:String,
    nameA: String, queryA: StorableQuery[A,TA],
    nameB: String, queryB: StorableQuery[B,TB]) extends TableRow[(OIDType,OIDType)](tag, tableName) {

    def a_id = column[OIDType](nm(nameA + "_id"))

    def b_id = column[OIDType](nm(nameB + "_id"))

    def a_fkey = foreignKey(fkn(nameA), a_id, queryA.query)(_.oid, onDelete = ForeignKeyAction.Cascade)

    def b_fkey = foreignKey(fkn(nameB), b_id, queryB.query)(_.oid, onDelete = ForeignKeyAction.Cascade)

    def a_b_uniqueness = index(idx(nameA + "_" + nameB), (a_id, b_id), unique = true)
    def * = (a_id, b_id)
  }

  abstract class ManyToManyQuery[
    A <: Storable, TA <: StorableRow[A],
    B <: Storable, TB <: StorableRow[B],
    T <: ManyToManyRow[A,TA,B,TB]](cons: Tag => T) extends TableQuery[T](cons) {
    lazy val findAsQuery = Compiled { bId : Rep[OIDType] => this.filter (_.b_id === bId ) }
    lazy val findBsQuery = Compiled { aId : Rep[OIDType] => this.filter (_.a_id === aId ) }
    def findAssociatedA(id: OIDType) : DBIOAction[Seq[OIDType], NoStream, Effect.Read] =
      findAsQuery(id).result.map { s : Seq[(OIDType,OIDType)] => s.map { p : (OIDType,OIDType) => p._1 } }
    def findAssociatedA(b: B) : DBIOAction[Seq[OIDType], NoStream, Effect.Read]  = findAssociatedA(b.getId)
    def findAssociatedB(id: OIDType) : DBIOAction[Seq[OIDType], NoStream, Effect.Read] =
      findBsQuery(id).result.map { s : Seq[(OIDType,OIDType)] => s.map { p : (OIDType,OIDType) => p._2 } }
    def findAssociatedB(a: A) : DBIOAction[Seq[OIDType], NoStream, Effect.Read] = findAssociatedB(a.getId)
    def associate(a: A, b: B) = this += (a.getId, b.getId)
  }

}

