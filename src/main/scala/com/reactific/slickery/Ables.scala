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

import com.reactific.slickery.Storable.OIDType

/** A Storable Object, the base trait of all storables
  * Objects stored in the database are uniquely identified by a 64-bit integer for each table.
  */

object Storable {
  type OIDType = Long
}

trait Storable {
  def oid : Option[OIDType]
  def isStored : Boolean = oid.isDefined
  def getId : Long = oid.getOrElse(-1)
}

/** Creatable objects.
  * Objects that have a creation time stamp
  */
trait Creatable extends Storable {
  def created: Instant
  def isCreated : Boolean = created != Instant.EPOCH
  def olderThan(d : Duration) : Boolean = {
    created match {
      case Instant.EPOCH => false
      case c : Instant =>
        Instant.now().minus(d).compareTo(c) > 0
    }
  }
  def newerThan(d : Duration) : Boolean = !olderThan(d)
}

/** Modifiable objects.
  * Objects that have a modification time stamp
  */
trait Modifiable extends Storable {
  def modified: Instant
  def isModified : Boolean = modified != Instant.EPOCH
  def changedSince(i : Instant) : Boolean = {
    modified match {
      case Instant.EPOCH => false
      case c : Instant =>
        i.compareTo(c) < 0
    }
  }
  def changedInLast(d : Duration) : Boolean = {
    changedSince(Instant.now().minus(d))
  }
}

/** Expirable objects.
  * Something that has an expiration date
  */
trait Expirable extends Storable {
  def expiresAt : Instant
  def hasExpiry : Boolean = expiresAt != Instant.EPOCH
  def isExpired = {
    expiresAt match {
      case Instant.EPOCH ⇒ false
      case e: Instant ⇒ e.isBefore(Instant.now())
    }
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

trait Useable extends Storable with Creatable with Modifiable with Nameable with Describable
