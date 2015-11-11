package com.reactific.slickery

import java.time.{Duration, Instant}

import org.specs2.mutable.Specification

/** Test Cases For The Ables */
class AblesSpec extends Specification {

  case class TestUsable(id : Option[Long] = None, created : Option[Instant] = None, modified: Option[Instant] = None,
    expired: Option[Instant] = None, name: String = "", description: String = "") extends Useable {
  }

  val tu1 = TestUsable()
  val now = Instant.now()
  val tu2 = TestUsable(Some(1),Some(now), Some(now), Some(now), "foo", "This is fooness." )

  "Useable" should {
    "support Storable" in {
      tu1.isStored must beFalse
      tu1.getId must beEqualTo(-1)
      tu2.isStored must beTrue
      tu2.getId must beEqualTo(1)
    }
    "support Creatable" in {
      tu1.isCreated must beFalse
      tu2.isCreated must beTrue
      tu1.olderThan(Duration.ofNanos(0L)) must beFalse
      tu2.olderThan(Duration.ofDays(1L)) must beFalse
      tu2.newerThan(Duration.ofNanos(0L)) must beFalse
    }
    "support Modifiable" in {
      tu1.isModified must beFalse
      tu2.isModified must beTrue
      tu1.changedInLast(Duration.ofDays(1L)) must beFalse
      tu2.changedInLast(Duration.ofDays(1L)) must beTrue
      tu2.changedInLast(Duration.ofNanos(0L)) must beFalse
    }
    "support Expirable" in {
      tu1.isExpirable must beFalse
      tu2.isExpirable must beTrue
      tu1.isExpired must beFalse
      tu2.isExpired must beTrue
      tu1.unexpired must beTrue
      tu2.unexpired must beFalse
    }
    "support Nameable" in {
      tu1.isNamed must beFalse
      tu2.isNamed must beTrue
    }
    "support Describable" in {
      tu1.isDescribed must beFalse
      tu2.isDescribed must beTrue
    }
  }
}
