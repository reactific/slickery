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

import java.time.{Duration, Instant}

/** A Storable Object, the base trait of all storables
  * Objects stored in the database are uniquely identified by a 64-bit integer for each table.
  */

trait Storable {
  def oid : Option[Long]
  def isStored : Boolean = oid.isDefined
  def getId : Long = oid.getOrElse(-1)
}

/** Creatable objects.
  * Objects that have a creation time stamp
  */
trait Creatable extends Storable {
  def created: Option[Instant]
  def isCreated = created.isDefined
  def olderThan(d : Duration) : Boolean = {
    created match {
      case Some(c : Instant) =>
        Instant.now().minus(d).compareTo(c) > 0
      case None => false
    }
  }
  def newerThan(d : Duration) : Boolean = !olderThan(d)
}

/** Modifiable objects.
  * Objects that have a modification time stamp
  */
trait Modifiable extends Storable {
  def modified: Option[Instant]
  def isModified = modified.isDefined
  def changedSince(i : Instant) : Boolean = {
    modified match {
      case Some(c : Instant) =>
        i.compareTo(c) < 0
      case None => false
    }
  }
  def changedInLast(d : Duration) : Boolean = changedSince(Instant.now().minus(d))
}

/** Expirable objecst.
  * Something that has an expiration date
  */
trait Expirable extends Storable {
  def expired : Option[Instant]
  def isExpirable = expired.isDefined
  def isExpired = expired match {
    case None ⇒ false
    case Some(instant) ⇒ instant.isBefore(Instant.now())
  }
  def unexpired : Boolean = !isExpired
}

/** Nameable objects.
  * Something that can be named with a String
  */
trait Nameable extends Storable {
  val name : String
  def isNamed : Boolean = ! name.isEmpty
}

/** Something that has a short textual description */
trait Describable extends Storable {
  val description : String
  def isDescribed : Boolean = ! description.isEmpty
}

trait Useable extends Storable with Creatable with Modifiable with Expirable with Nameable with Describable
