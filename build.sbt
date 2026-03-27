import com.jsuereth.sbtpgp.PgpKeys.publishSigned

// Used to publish snapshots to Maven Central.
val mavenCentralSnapshots = "Maven Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

// Versions:

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
  publishTo := {
    if (isSnapshot.value) Some(mavenCentralSnapshots)
    else localStaging.value
  },
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := {
    _ => false
  },
  versionScheme := Some("early-semver"),
  git.useGitDescribe := true,
  git.uncommittedSignifier := None,
  // Sonatype ignores isSnapshot setting and only looks at -SNAPSHOT suffix in version:
  //   https://central.sonatype.org/publish/publish-maven/#performing-a-snapshot-deployment
  // meanwhile sbt-git used to set up SNAPSHOT if there were uncommitted changes:
  //   https://github.com/sbt/sbt-git/issues/164
  // (now this suffix is empty by default) so we need to fix it manually.
  git.gitUncommittedChanges := git.gitCurrentTags.value.isEmpty,
  git.uncommittedSignifier := Some("SNAPSHOT")
)

val noPublishSettings =
  Seq(publish / skip := true, publishArtifact := false)

// Modules:

lazy val root = project
  .in(file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(publishSettings)
  .settings(noPublishSettings)
  .settings(
    name := "scala-newtype-compat-root",
    crossScalaVersions := Nil,
    commands += Command.command("ci-release") { state =>
      val extracted = Project.extract(state)
      val tags = extracted.get(git.gitCurrentTags)
      val cmd = if (tags.nonEmpty) "publishSigned ; sonaRelease" else "publishSigned"
      cmd :: state
    }
  )
  .aggregate(compat, plugin, tests)

lazy val compat = project
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(publishSettings)
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
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(publishSettings)
  .settings(
    name := "newtype-plugin",
    crossScalaVersions := scala3Versions,
    scalaVersion := scala3Latest,
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided
  )

lazy val tests = project
  .dependsOn(compat)
  .enablePlugins(GitVersioning, GitBranchPrompt)
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
