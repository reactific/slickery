/**********************************************************************************************************************
 *                                                                                                                    *
 * Copyright (c) 2015, Reactific Software LLC. All Rights Reserved.                                                   *
 *                                                                                                                    *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance     *
 * with the License. You may obtain a copy of the License at                                                          *
 *                                                                                                                    *
 *     http://www.apache.org/licenses/LICENSE-2.0                                                                     *
 *                                                                                                                    *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed   *
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for  *
 * the specific language governing permissions and limitations under the License.                                     *
 **********************************************************************************************************************/


import com.reactific.sbt.ProjectPlugin
import com.reactific.sbt.ProjectPlugin.autoImport._
import sbt._
import sbt.Keys._
import scoverage.ScoverageSbtPlugin

import scala.language.postfixOps

object SlickeryBuild extends Build {

  // Utilities
  val helpers         = "com.reactific"       %% "helpers"              % "0.1.0"
  val slick           = "com.typesafe.slick"  %% "slick"                % "3.1.0"
  val h2              = "com.h2database"       % "h2"                   % "1.4.187"
  val play_json       = "com.typesafe.play"   %% "play-json"            % "2.4.3"

  val classesIgnoredByScoverage : String = Seq[String](
    "<empty>" // Avoids warnings from scoverage
  ).mkString(";")


  lazy val root  = sbt.Project("slickery", file("."))
    .enablePlugins(ProjectPlugin)
    .settings(
      organization    := "com.reactific",
      copyrightHolder := "Reactific Software LLC",
      copyrightYears  := Seq(2015),
      codePackage     := "com.reactific.slickery",
      titleForDocs    := "Reactific Slick Utilities",
      developerUrl    := url("http://www.reactific.com/"),
      ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := classesIgnoredByScoverage,
      ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100,
      libraryDependencies ++= Seq(
        helpers, slick, h2, play_json
      )
    )
}
