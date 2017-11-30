import Dependencies._
import Merging._
import Publishing._
import Testing._
import Version._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._

object Settings {

  val artifactory = "https://artifactory.broadinstitute.org/artifactory/"

  val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot"
  )

  //coreDefaultSettings + defaultConfigs = the now deprecated defaultSettings
  val commonBuildSettings = Defaults.coreDefaultSettings ++ Defaults.defaultConfigs ++ Seq(
    javaOptions += "-Xmx2G",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )

  val commonCompilerSettings = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "utf8",
    "-target:jvm-1.8"
  )

  //sbt assembly settings
  val commonAssemblySettings = Seq(
    assemblyMergeStrategy in assembly := customMergeStrategy((assemblyMergeStrategy in assembly).value),
    test in assembly := {}
  )

  val samAssemblySettings = Seq(
    mainClass in assembly := Some("org.broadinstitute.dsde.workbench.sam.Boot")
  )

  //common settings for all sbt subprojects
  val commonSettings =
    commonBuildSettings ++ commonAssemblySettings ++ commonTestSettings ++ List(
    organization  := "org.broadinstitute.dsde.workbench",
    scalaVersion  := "2.12.2",
    resolvers ++= commonResolvers,
    scalacOptions ++= commonCompilerSettings
  )

  val modelSettings = commonSettings ++ List(
    name := "sam-model",
    libraryDependencies ++= modelDependencies
  ) ++ publishSettings ++ rootVersionSettings

  val samCoreSettings = commonSettings ++ List(
    name := "rawls-core",
    version := "0.1",
    libraryDependencies ++= samCoreDependencies
  ) ++ samAssemblySettings ++ noPublishSettings

  //the full list of settings for the root project that's ultimately the one we build into a fat JAR and run
  //coreDefaultSettings (inside commonSettings) sets the project name, which we want to override, so ordering is important.
  //thus commonSettings needs to be added first.
  val rootSettings = commonSettings ++ List(
    name := "sam",
    version := "0.1"
  ) ++ samAssemblySettings ++ rootVersionSettings


}
