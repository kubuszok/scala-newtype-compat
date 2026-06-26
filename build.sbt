import kubuszok.sbt._
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
  homepage := Some(url("https://github.com/kubuszok/scala-newtype-compat")),
  organizationHomepage := Some(url("https://kubuszok.com")),
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/kubuszok/scala-newtype-compat/"),
      "scm:git:git@github.com:kubuszok/scala-newtype-compat.git"
    )
  ),
  startYear := Some(2026),
  developers := List(
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://kubuszok.com"))
  ),
  pomExtra := (
    <issueManagement>
      <system>GitHub issues</system>
      <url>https://github.com/kubuszok/scala-newtype-compat/issues</url>
    </issueManagement>
  ),
  projectType := ProjectType.ScalaLibrary
)

val noPublishSettings =
  Seq(projectType := ProjectType.NonPublished)

// Explicit JDK bytecode target per Scala version — never rely on implicit inference, which would
// follow the build host's JDK (17, since sbt 2.0 requires it). Scala 2.13 uses `-release`; Scala 3
// uses `-java-output-version`:
//   - 2.13                 -> Java 8  (without this it infers the host JDK 17)
//   - 3.3.0 - 3.3.7        -> Java 8
//   - 3.3.8+ (3.3 LTS)     -> Java 11, plus `-Yfuture-lazy-vals` (new lazy-val encoding, needed for
//                             JDK 26+; built-in on 3.4+, opt-in on 3.3, and it requires output >= 9)
//   - 3.4, 3.5, 3.6, 3.7   -> Java 8
//   - 3.8+ (incl. 3.9 LTS) -> Java 17 (Scala 3.8 raised the minimum JDK to 17)
val jdkFutureProofSettings = Seq(
  scalacOptions ++= {
    val sv = scalaVersion.value
    if (sv.startsWith("2.")) Seq("-release:8")
    else {
      val parts = sv.split('.')
      val minor = parts(1).toInt
      val patch = parts(2).takeWhile(_.isDigit).toInt
      val out =
        if (minor >= 8) "17"                    // 3.8+ require JDK 17
        else if (minor == 3 && patch >= 8) "11" // 3.3.8+: -Yfuture-lazy-vals needs output >= 9
        else "8"                                // 3.3.0-3.3.7 and 3.4-3.7: widest floor
      val base = Seq("-java-output-version", out)
      if (minor == 3 && patch >= 8) "-Yfuture-lazy-vals" +: base else base
    }
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
    crossScalaVersions := Nil
  )
  .aggregate(compat, plugin, tests)

// Convenience aliases. sbt-welcome (which previously exposed `logo`/`usefulTasks`) has no sbt 2.0
// build and is no longer bundled by sbt-kubuszok, so the help-menu tasks are registered as aliases
// instead. Note: in sbt 2.0 the bare `test` task is incremental/cached; `testFull` forces a real run.
addCommandAlias("compileAll", "+compile")
addCommandAlias("testAll", "+testFull")
addCommandAlias("publishLocalAll", "+publishLocal")

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
      else {
        // sbt 2.0: packageBin yields an xsbti.HashedVirtualFileRef (no getAbsolutePath); convert
        // to a real path via the build's fileConverter.
        val pluginJar = fileConverter.value.toPath((plugin / Compile / packageBin).value).toAbsolutePath
        Seq(s"-Xplugin:$pluginJar")
      }
    },
    // sbt 2.0 caches task results and has no sjsonnew JsonFormat for CompileAnalysis; this override
    // only exists to force the compiler plugin to be packaged before the Scala 3 tests compile, so
    // opt out of caching with Def.uncached.
    (Test / compile) := Def.uncached {
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
