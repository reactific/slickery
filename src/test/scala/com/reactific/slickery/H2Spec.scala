package com.reactific.slickery

import com.reactific.slickery.testkit.SlickerySpecification

import scala.concurrent.ExecutionContext.Implicits.global

class H2Schema(name : String) extends CommonTestSchema[H2Driver](name, name, H2.makeDbConfigFor(name))

class H2Spec extends SlickerySpecification with CommonTests {

  "H2" should {
    "support common type extensions" in WithH2Schema("H2Create")(new H2Schema(_)) { schema : H2Schema ⇒
      readAndWriteMappedTypes[H2Driver,H2Schema](schema)
    }
  }
}
