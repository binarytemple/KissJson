import sbt._
import Keys._

object BuildSettings
{

	val buildSettings = Defaults.defaultSettings ++ Seq(
			organization := "com.bryghts.kissjson",
			version      := "0.0.1-SNAPSHOT",
			scalaVersion := "2.10.2",
			scalacOptions ++= Seq(),
			libraryDependencies += "com.bryghts.kissnumber" % "kissnumber_2.10" % "0.0.1",
			libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.0-M4"
	)

}

object MyBuild extends Build
{
	val projectName = "KissJson"

	import BuildSettings._


	lazy val root: Project = Project(
		projectName,
		file("."),
		settings = buildSettings
	)

}