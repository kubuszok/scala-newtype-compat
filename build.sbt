val scala2_13 = "2.13.16"

val scala3Versions = Seq(
  "3.3.0", "3.3.1", "3.3.3", "3.3.4", "3.3.5", "3.3.6", "3.3.7",
  "3.4.0", "3.4.1", "3.4.2", "3.4.3",
  "3.5.0", "3.5.1", "3.5.2",
  "3.6.2", "3.6.3", "3.6.4",
  "3.7.0", "3.7.1", "3.7.2", "3.7.3", "3.7.4",
  "3.8.0", "3.8.1", "3.8.2"
)

val scala3Latest = scala3Versions.last

val newtypeVersion = "0.4.4"

ThisBuild / organization := "com.kubuszok"
ThisBuild / versionScheme := Some("early-semver")

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )
  .aggregate(compat, plugin, tests)

lazy val compat = project
  .settings(
    name := "newtype-compat",
    crossScalaVersions := scala2_13 +: scala3Versions,
    scalaVersion := scala3Latest,
    // Empty artifact — no source files, just brings in the dependency
    Compile / sources := Seq.empty,
    // Always depend on the 2.13 artifact (Scala 3 can consume 2.13 jars)
    libraryDependencies += "io.estatico" % "newtype_2.13" % newtypeVersion
  )

lazy val plugin = project
  .settings(
    name := "newtype-plugin",
    crossScalaVersions := scala3Versions,
    scalaVersion := scala3Latest,
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided
  )

lazy val tests = project
  .dependsOn(compat)
  .settings(
    name := "newtype-compat-tests",
    publish / skip := true,
    crossScalaVersions := Seq(scala2_13, scala3Latest),
    scalaVersion := scala3Latest,
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq("-Ymacro-annotations")
      else
        Seq(s"-Xplugin:${(plugin / Compile / packageBin).value.getAbsolutePath}")
    },
    // Force plugin to compile first when testing on Scala 3
    (Test / compile) := {
      if (scalaVersion.value.startsWith("3."))
        (plugin / Compile / packageBin).value
      (Test / compile).value
    },
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % "3.2.19" % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
      "org.typelevel"  %% "cats-core"  % "2.12.0" % Test
    )
  )
