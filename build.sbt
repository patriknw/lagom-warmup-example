organization in ThisBuild := "org.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1" % Test

lazy val `warmup` = (project in file(".")).aggregate(`warmup-api`, `warmup-impl`)

lazy val `warmup-api` = (project in file("warmup-api")).settings(libraryDependencies ++= Seq(lagomScaladslApi))

lazy val `warmup-impl` = (project in file("warmup-impl"))
  .enablePlugins(LagomScala)
  .settings(libraryDependencies ++= Seq(lagomScaladslPersistenceCassandra, lagomScaladslTestKit, macwire, scalaTest))
  .settings(lagomForkedTestSettings)
  .dependsOn(`warmup-api`)
