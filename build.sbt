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

// Explicit JDK target per Scala version — don't rely on the compiler's implicit bytecode-version
// inference. The build host is JDK 17 (sbt 2.0 requires it), so `-release` is what actually pins
// each artifact's JDK floor:
//   - 3.3 LTS through 3.7 -> JDK 11. These compilers support JDK 8+, so a JDK 11 floor is valid
//     (we deliberately avoid JDK 8). The 3.3 line additionally needs `-Yfuture-lazy-vals` (legacy
//     lazy-val bitmap encoding breaks under newer JDKs; built-in on 3.4+), which needs output >= 9.
//   - 3.8+ (incl. the future 3.9 LTS) -> JDK 17: Scala 3.8 raised the minimum JDK to 17.
//   - 2.13 -> untouched: the published `compat` shim has empty sources and the plugin is Scala-3
//     only, so 2.13 appears only in the non-published `tests` module.
val jdkFutureProofSettings = Seq(
  scalacOptions ++= {
    val sv = scalaVersion.value
    if (sv.startsWith("3.")) {
      val minor = sv.split('.')(1).toInt
      val release = Seq("-release", if (minor >= 8) "17" else "11")
      if (minor == 3) "-Yfuture-lazy-vals" +: release else release
    } else Seq.empty
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
