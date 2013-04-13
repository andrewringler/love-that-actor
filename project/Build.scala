import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "love-that-actor"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
    		jdbc, anorm,
    		"postgresql" % "postgresql" % "9.1-901-1.jdbc4",
    		"com.google.guava" % "guava" % "14.0.1"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
    		// Add your own project settings here      
        )
}
