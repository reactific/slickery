/**********************************************************************************************************************
  *                                                                                                                    *
  * Copyright (c) 2013, Reid Spencer and viritude llc. All Rights Reserved.                                            *
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

import com.reactific.helpers.LoggingHelper
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable
import slick.profile.FixedSqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

/**
 * The abstract database component.
 * This trait allows use to define database components which are simply collections of related tables and the
 * various query methods on those tables to provide access to them. Since Components contain Tables and Scrupal requires
 * all database entities to have a particular shape, that shape is enforced in the EntityTable class. Note that
 * Component extends Sketch which is mixed in to other components but resolved by the Schema class.
 */
abstract class Schema(val schemaName: String, val dbConfig: DatabaseConfig[JdbcProfile])
  (implicit ec: ExecutionContext) extends LoggingHelper {

  import dbConfig.driver.api._

  protected def db = dbConfig.db

  trait FinderOf[R] {
    def apply() : FixedSqlStreamingAction[Seq[R],R,slick.dbio.Effect.Read]
  }

  trait StandardQueries[R,ID, T<:BasicTable[R]] { self : TableQuery[T] =>

    def create(entity: R) : Future[ID]
    def retrieve(id : ID) : Future[Option[R]]
    def update(entity: R) : Future[ID]
    def delete(id: ID) : Future[Int]
    def fetch(id: ID) : Future[Option[R]] = retrieve(id)

    final def fetch(oid: Option[ID]) : Future[Option[R]] = {
      oid match {
        case None =>
          Future.successful(None)
        case Some(id) =>
          fetch(id)
      }
    }

    val findAll : FinderOf[R] = new FinderOf[R] { def apply() = { self.result } }

    final def find( finder: FinderOf[R] ) : FixedSqlStreamingAction[Seq[R],R,slick.dbio.Effect.Read] = {
      finder()
    }
  }

  abstract class BasicTable[S](tag: Tag, tableName: String) extends Table[S](tag, Some(schemaName), tableName) {
    def fullName : String = {
      val schemaPrefix = { schemaName.map { n => n + "." } getOrElse "" }
      s"$schemaPrefix$tableName"
    }

    protected def nm(columnName: String) : String = {
      s"${fullName}_$columnName"
    }

    protected def fkn(foreignTableName: String ) : String = {
      nm( foreignTableName + "_fkey")
    }

    protected def idx(name: String) : String = nm(name + "_idx")

  }

  abstract class StandardTable[S,ID,T <: BasicTable[S]](c : Tag => T)
    extends TableQuery[T]( c ) with StandardQueries[S,ID,T]

  def tables : Seq[StandardTable[_,_,_]]

  def schemas : dbConfig.driver.DDL

  def validateTables( tables: Vector[MTable] ) : Seq[Throwable] = ???

  final def validate(implicit session: Session) : Future[Seq[Throwable]] = db.run {
    MTable.getTables.map[Seq[Throwable]] { tables : Vector[MTable] =>
      validateTables(tables)
    }
  }

  final def create() : Future[Unit] = {
    SupportedDatabase.forDriverName(dbConfig.driverName) match {
      case Some(sdb) =>
        val extensions = dbConfig.driver.createSchemaActionExtensionMethods(schemas)
        db.run {
          DBIO.seq(
            sdb.makeSchema(dbConfig.driver, schemaName),
            extensions.create
          )
        }
      case None =>
        throw new Exception(s"No database for ${dbConfig.driverName}")
    }
  }

  final def drop() : Future[Unit] = {
    val extensions = dbConfig.driver.createSchemaActionExtensionMethods(schemas)
    db.run { extensions.drop }
  }

  /*
  trait SymbolicTable[S <: SymbolicIdentifiable] extends ScrupalTable[S] with AbstractStorage[Symbol,Symbol,S] {

    def id = column[Symbol](nm("id"), O.PrimaryKey)

    lazy val fetchByIDQuery = for { id <- Parameters[Symbol] ; ent <- this if ent.id === id } yield ent

    override def fetch(id: Symbol) : Option[S] =  fetchByIDQuery(id).firstOption

    override def insert(entity: S) : Symbol = { *  insert(entity) ; entity.id }

    override def update(entity: S) : Int = this.filter(_.id === entity.id) update(entity)

    override def delete(entity: S) : Boolean = delete(entity.id)

    override def delete(id: Symbol) : Boolean =  {
      this.filter(_.id === id).delete > 0
    }
  }

  trait NumericTable[S <: NumericIdentifiable] extends ScrupalTable[S] with StorageFor[S] {

    def id = column[Identifier](nm("id"), O.PrimaryKey, O.AutoInc)

    lazy val fetchByIDQuery = for { id <- Parameters[Identifier] ; ent <- this if ent.id === id } yield ent

    override def fetch(id: Identifier) : Option[S] =  fetchByIDQuery(id).firstOption

    override def insert(entity: S) : Identifier = * returning id insert(entity)

    override def update(entity: S) : Int = this.filter(_.id === entity.id) update(entity)

    override def delete(entity: S) : Boolean = delete(entity.id)

    protected def delete(oid: Option[Identifier]) : Boolean = {
      oid match { case None => false; case Some(id) => delete(id) }
    }

    override def delete(id: Identifier) : Boolean =  {
      this.filter(_.id === id).delete > 0
    }

  }

  trait CreatableTable[S <: Creatable] extends ScrupalTable[S] {

    def created = column[DateTime](nm("created"), O.Nullable) // FIXME: Dynamic Date required!

    lazy val findSinceQuery = for {
      created <- Parameters[DateTime] ;
      e <- this if (e.created > created)
    } yield e

    case class CreatedSince(d: DateTime) extends FinderOf[S] { override def apply() = findSinceQuery(d).list }
  }

  trait ModifiableTable[S <: Modifiable] extends ScrupalTable[S] {

    def modified_index = index(nm("modified_index"), modified, unique=false)

    def modified = column[DateTime](nm("modified"), O.Nullable) // FIXME: Dynamic Date required!

    lazy val modifiedSinceQuery = for {
      chg <- Parameters[DateTime];
      mt <- this if mt.modified > chg
    } yield mt

    case class ModifiedSince(d: DateTime) extends FinderOf[S] { override def apply() = modifiedSinceQuery(d).list }
  }

  trait NameableTable[S <: Nameable] extends ScrupalTable[S] {
    def name = column[Symbol](nm("name"), O.NotNull)

    def name_index = index(idx("name"), name, unique=true)

    lazy val fetchByNameQuery = for {
      n <- Parameters[Symbol] ;
      e <- this if e.name === n
    } yield e

    case class ByName(n: Symbol) extends FinderOf[S] { override def apply() = fetchByNameQuery(n).list }
  }

  trait DescribableTable[S <: Describable] extends ScrupalTable[S] {
    def description = column[String](nm("_description"), O.NotNull)
  }

  trait EnablableTable[S <: Enablable] extends ScrupalTable[S] {
    def enabled = column[Boolean](nm("enabled"), O.NotNull)
    def enabled_index = index(idx("enabled"), enabled, unique=false)

    lazy val enabledQuery = for { en <- this if en.enabled === true } yield en
    def allEnabled() : List[S] = enabledQuery.list
  }

  trait NumericThingTable[S <: NumericThing]
    extends NumericTable[S]
    with NameableTable[S]
    with CreatableTable[S]
    with ModifiableTable[S]
    with DescribableTable[S]

  trait NumericEnablableThingTable[S <: NumericEnablableThing]
    extends NumericThingTable[S] with EnablableTable[S]

  trait SymbolicThingTable[S <: SymbolicThing]
    extends SymbolicTable[S]
    with CreatableTable[S]
    with ModifiableTable[S]
    with DescribableTable[S]

  trait SymbolicEnablableThingTable[S <: SymbolicEnablableThing]
    extends SymbolicThingTable[S] with EnablableTable[S]

  abstract class SymbolicNumericCorrelationTable[A <: SymbolicIdentifiable, B <: NumericIdentifiable] (
    tableName: String, nameA: String, nameB: String, tableA: SymbolicTable[A], tableB: NumericTable[B]) extends
  ScrupalTable[(Symbol, Identifier)](tableName) {
    def a_id = column[Symbol](nm(nameA + "_id"))
    def b_id = column[Identifier](nm(nameB + "_id"))
    def a_fkey = foreignKey(fkn(nameA), a_id, tableA)(_.id, onDelete = ForeignKeyAction.Cascade )
    def b_fkey = foreignKey(fkn(nameB), b_id, tableB)(_.id, onDelete = ForeignKeyAction.Cascade )
    def a_b_uniqueness = index(idx(nameA + "_" + nameB), (a_id, b_id), unique= true)

    lazy val findAsQuery = for {
      bId <- Parameters[Identifier];
      b <- this if b.b_id === bId;
      a <- tableA if a.id === b.a_id
    } yield a

    lazy val findBsQuery = for {
      aId <- Parameters[Symbol];
      a <- this if a.a_id === aId;
      b <- tableB if b.id === a.b_id
    } yield b

    def findAssociatedA(b: B) : List[A] = { if (b.id.isDefined) findAsQuery(b.id.get).list else List() }
    def findAssociatedB(a: A) : List[B] = { findBsQuery(a.id).list }
    def * = a_id ~ b_id
  }

  abstract class SymbolicSymbolicCorrelationTable[A <: SymbolicIdentifiable, B <: SymbolicIdentifiable] (
    tableName: String, nameA: String, nameB: String, tableA: SymbolicTable[A], tableB: SymbolicTable[B]) extends
  ScrupalTable[(Symbol, Symbol)](tableName) {
    def a_id = column[Symbol](nm(nameA + "_id"))
    def b_id = column[Symbol](nm(nameB + "_id"))
    def a_fkey = foreignKey(fkn(nameA), a_id, tableA)(_.id, onDelete = ForeignKeyAction.Cascade )
    def b_fkey = foreignKey(fkn(nameB), b_id, tableB)(_.id, onDelete = ForeignKeyAction.Cascade )
    def a_b_uniqueness = index(idx(nameA + "_" + nameB), (a_id, b_id), unique= true)

    lazy val findAsQuery = for {
      bId <- Parameters[Symbol];
      b <- this if b.b_id === bId;
      a <- tableA if a.id === b.a_id
    } yield a

    lazy val findBsQuery = for {
      aId <- Parameters[Symbol];
      a <- this if a.a_id === aId;
      b <- tableB if b.id === a.b_id
    } yield b

    def findAssociatedA(b: B) : List[A] = { findAsQuery(b.id).list }
    def findAssociatedB(a: A) : List[B] = { findBsQuery(a.id).list }
    def * = a_id ~ b_id
  }

  /**
   * The base class of all correlation tables.
   * This allows many-to-many relationships to be established by simply listing the pairs of IDs
   */
  abstract class ManyToManyTable[A <: NumericIdentifiable, B <: NumericIdentifiable ] (tableName: String,
    nameA: String, nameB: String, tableA: NumericTable[A], tableB: NumericTable[B])
    extends ScrupalTable[(Identifier,Identifier)](tableName) {
    def a_id = column[Identifier](nm(nameA + "_id"))
    def b_id = column[Identifier](nm(nameB + "_id"))
    def a_fkey = foreignKey(fkn(nameA), a_id, tableA)(_.id, onDelete = ForeignKeyAction.Cascade )
    def b_fkey = foreignKey(fkn(nameB), b_id, tableB)(_.id, onDelete = ForeignKeyAction.Cascade )
    def a_b_uniqueness = index(idx(nameA + "_" + nameB), (a_id, b_id), unique= true)
    lazy val findBsQuery = for { aId <- Parameters[Long]; a <- this if a.a_id === aId; b <- tableB if b.id === a.b_id } yield b
    lazy val findAsQuery = for { bId <- Parameters[Long]; b <- this if b.b_id === bId; a <- tableA if a.id === b.a_id } yield a
    def findAssociatedA(b: B) : List[A] = { if (b.id.isDefined) findAsQuery(b.id.get).list else List() }
    def findAssociatedB(a: A) : List[B] = { if (a.id.isDefined) findBsQuery(a.id.get).list else List() }
    def * = a_id ~ b_id

  };

  /**
   * The base class of all tables that provide a string key to reference some Identifiable table.
   * This allows a
   */
  abstract class NamedNumericTable[ReferentType <: NumericIdentifiable](
    tableName: String, valueTable: NumericTable[ReferentType])
    extends ScrupalTable[(String,Identifier)](tableName) {
    def key = column[String](nm("key"))
    def value = column[Identifier](nm("value"))
    def value_fkey = foreignKey(fkn(valueTable.tableName), value, valueTable)(_.id,
      onDelete = ForeignKeyAction.Cascade)
    def key_value_index = index(idx("key_value"), on=(key, value), unique=true)
    def * = key ~ value

    def insert( pair: (String, Long) )  = { * insert pair }

    def insert(k: String, v: Long) : Unit = insert( Tuple2(k, v) )

    // -- operations on rows
    def delete(k: String) : Boolean =  {
      this.filter(_.key === k).delete > 0
    }

    lazy val findValuesQuery = for {
      k <- Parameters[String];
      ni <- this if ni.key === k ;
      vt <- valueTable if ni.value === vt.id
    } yield vt

    def findValues(aKey: String): List[ReferentType] = findValuesQuery(aKey).list

    lazy val findKeysQuery = for {
      id <- Parameters[Long];
      ni <- this if ni.value === id
    } yield ni.key

    def findKeys(id : Long) : List[String] = findKeysQuery(id).list
  }
  */
}

