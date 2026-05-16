import kubuszok.sbt._
import sbtwelcome.UsefulTask
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions:

val scala2_13 = "2.13.16"

val scala3Versions = Seq(
  "3.3.0", "3.3.1", "3.3.3", "3.3.4", "3.3.5", "3.3.6", "3.3.7",
  "3.4.0", "3.4.1", "3.4.2", "3.4.3",
  "3.5.0", "3.5.1", "3.5.2",
  "3.6.2", "3.6.3", "3.6.4",
  "3.7.0", "3.7.1", "3.7.2", "3.7.3", "3.7.4",
  "3.8.0", "3.8.1", "3.8.2", "3.8.3"
)

val scala3Latest = scala3Versions.last

val newtypeVersion = "0.4.4"

// Common settings:

val publishSettings = Seq(
  organization := "com.kubuszok",
  homepage := Some(url("https://github.com/MateuszKubuszok/scala-newtype-compat")),
  organizationHomepage := Some(url("https://kubuszok.com")),
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/MateuszKubuszok/scala-newtype-compat/"),
      "scm:git:git@github.com:MateuszKubuszok/scala-newtype-compat.git"
    )
  ),
  startYear := Some(2026),
  developers := List(
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://github.com/MateuszKubuszok"))
  ),
  pomExtra := (
    <issueManagement>
      <system>GitHub issues</system>
      <url>https://github.com/MateuszKubuszok/scala-newtype-compat/issues</url>
    </issueManagement>
  ),
  projectType := ProjectType.ScalaLibrary
)

val noPublishSettings =
  Seq(projectType := ProjectType.NonPublished)

// Modules:

lazy val root = project
  .in(file("."))
  .enablePlugins(KubuszokRootPlugin)
  .settings(publishSettings)
  .settings(noPublishSettings)
  .settings(
    name := "scala-newtype-compat-root",
    crossScalaVersions := Nil,
    logo := s"scala-newtype-compat ${version.value}",
    usefulTasks := Seq(
      UsefulTask("+compile", "Compile all Scala versions").noAlias,
      UsefulTask("+test", "Test all Scala versions").noAlias,
      UsefulTask("+publishLocal", "Publish locally for all versions").noAlias,
      UsefulTask("ci-release", "Publish snapshot or release (based on git tags)").noAlias
    )
  )
  .aggregate(compat, plugin, tests)

lazy val compat = project
  .enablePlugins(KubuszokRootPlugin)
  .settings(publishSettings)
  .settings(
    name := "newtype-compat",
    crossScalaVersions := Seq(scala2_13, scala3Versions.head),
    scalaVersion := scala3Versions.head,
    Compile / sources := Seq.empty,
    libraryDependencies += "io.estatico" % "newtype_2.13" % newtypeVersion
  )

lazy val plugin = project
  .enablePlugins(KubuszokRootPlugin)
  .settings(publishSettings)
  .settings(
    name := "newtype-plugin",
    crossVersion := CrossVersion.full,
    crossScalaVersions := scala3Versions,
    scalaVersion := scala3Latest,
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided
  )

lazy val tests = project
  .dependsOn(compat)
  .enablePlugins(KubuszokRootPlugin)
  .settings(publishSettings)
  .settings(noPublishSettings)
  .settings(
    name := "newtype-compat-tests",
    crossScalaVersions := scala2_13 +: scala3Versions,
    scalaVersion := scala3Latest,
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq("-Ymacro-annotations")
      else
        Seq(s"-Xplugin:${(plugin / Compile / packageBin).value.getAbsolutePath}")
    },
    (Test / compile) := {
      if (scalaVersion.value.startsWith("3."))
        (plugin / Compile / packageBin).value
      (Test / compile).value
    },
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % "3.2.19" % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
      "org.typelevel"  %% "cats-core"  % "2.12.0" % Test,
      "eu.timepit"     %% "refined"    % "0.11.3" % Test
    )
  )
