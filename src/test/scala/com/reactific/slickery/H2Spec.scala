package com.reactific.slickery

import com.reactific.helpers.{FutureHelper, LoggingHelper}
import org.specs2.mutable.Specification

class H2Spec extends Specification with LoggingHelper with FutureHelper {

  lazy val h2IsViable : Boolean = H2.testConnection

  "H2" should {
    "create test database" in {
      h2IsViable must beTrue
    }
  }
}
