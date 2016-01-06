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
import scoverage.ScoverageSbtPlugin.ScoverageKeys._
import org.scoverage.coveralls.Imports.CoverallsKeys._


import scala.language.postfixOps

object SlickeryBuild extends Build {

  // Utilities
  val helpers             = "com.reactific"       %% "helpers"              % "0.3.3"
  val helpers_tk          = "com.reactific"       %% "helpers-testkit"      % "0.3.3"
  val slick               = "com.typesafe.slick"  %% "slick"                % "3.1.1"
  val hickaricp           = "com.typesafe.slick"  %% "slick-hikaricp"       % "3.1.1"
  val h2                  = "com.h2database"       % "h2"                   % "1.4.187"
  val mysql               = "mysql"                % "mysql-connector-java" % "5.1.38"
  val sqlite              = "org.xerial"           % "sqlite-jdbc"          % "3.8.11.2"
  val play_json           = "com.typesafe.play"   %% "play-json"            % "2.4.4"
  val slick_pg            = "com.github.tminglei" %% "slick-pg"             % "0.10.2"
  val slick_pg_play_json  = "com.github.tminglei" %% "slick-pg_play-json"   % "0.10.2"
  val slick_pg_date2      = "com.github.tminglei" %% "slick-pg_date2"       % "0.10.2"
  val slick_pg_jts        = "com.github.tminglei" %% "slick-pg_jts"         % "0.10.2"

  val classesIgnoredByScoverage : String = Seq[String](
    "<empty>" // Avoids warnings from scoverage
  ).mkString(";")

  lazy val root  : sbt.Project =
    sbt.Project("slickery", file("."), aggregate=Seq(testkit))
    .enablePlugins(ProjectPlugin, ScoverageSbtPlugin)
    .settings(
      organization    := "com.reactific",
      copyrightHolder := "Reactific Software LLC",
      copyrightYears  := Seq(2015),
      codePackage     := "com.reactific.slickery",
      titleForDocs    := "Reactific Slick Utilities",
      developerUrl    := url("http://www.reactific.com/"),
      coverageFailOnMinimum := true,
      coverageExcludedPackages := classesIgnoredByScoverage,
      coverageMinimum := 90,
      coverallsToken := Some("kxHEjzKGBB3aclIfZgtw6oDWERuSUudIv"),
      libraryDependencies ++= Seq(
        helpers, slick, h2, mysql, sqlite, play_json,
        slick_pg, slick_pg_play_json, slick_pg_date2, slick_pg_jts, hickaricp
      )
    )

  lazy val testkit : sbt.Project =
    sbt.Project("slickery-testkit", file("testkit"), dependencies=Seq(root))
      .enablePlugins(ProjectPlugin)
      .settings(
        organization    := "com.reactific",
        copyrightHolder := "Reactific Software LLC",
        copyrightYears  := Seq(2015),
        codePackage     := "com.reactific.slickery",
        titleForDocs    := "Reactific Slickery Test Kit",
        developerUrl    := url("http://www.reactific.com/"),
        libraryDependencies ++= Seq(helpers_tk)
      )
}
