import kubuszok.sbt._
import sbtwelcome.UsefulTask
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions:
// Single source of truth shared with .github/workflows/ci.yml — see project/scala*-versions.txt.

def readVersions(path: String): Seq[String] =
  IO.readLines(file(path)).map(_.trim).filterNot(line => line.isEmpty || line.startsWith("#"))

val scala2Versions = readVersions("project/scala2-versions.txt")
val scala3Versions = readVersions("project/scala3-versions.txt")

val scala2_13 = scala2Versions.head
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
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://kubuszok.com"))
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

// JDK 26+ future-proofing: on the 3.3 LTS line, lazy vals use the legacy bitmap encoding that
// breaks under newer JDKs. `-Yfuture-lazy-vals` opts into the new encoding (built-in on 3.4+),
// but it requires `-java-output-version >= 9`. Apply ONLY on 3.3.8 (JVM-only project); never on
// 2.13 and never on 3.4+/3.8 (which already have the new encoding by default).
val jdkFutureProofSettings = Seq(
  scalacOptions ++= {
    if (scalaVersion.value == "3.3.8") Seq("-Yfuture-lazy-vals", "-java-output-version", "17")
    else Seq.empty
  }
)

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
  .settings(jdkFutureProofSettings)
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
  .settings(jdkFutureProofSettings)
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
      "org.scalatest"  %% "scalatest"  % "3.2.20" % Test,
      "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
      "org.typelevel"  %% "cats-core"  % "2.13.0" % Test,
      "eu.timepit"     %% "refined"    % "0.11.3" % Test
    )
  )
