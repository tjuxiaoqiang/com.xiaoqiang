import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object KafkaUtilsBuild extends Build {

  def sharedSettings = Defaults.defaultSettings ++ assemblySettings ++ Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.10.3",
    organization := "com.quantifind",
    scalacOptions := Seq("-deprecation", "-unchecked", "-optimize"),
    unmanagedJars in Compile <<= baseDirectory map { base => (base / "lib" ** "*.jar").classpath },
    retrieveManaged := true,
    transitiveClassifiers in Scope.GlobalScope := Seq("sources"),
    resolvers ++= Seq(
      "sonatype-snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
      "sonatype-releases"  at "http://oss.sonatype.org/content/repositories/releases",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "JBoss Repository" at "http://repository.jboss.org/nexus/content/repositories/releases/"
//      "Xiaonei Repository" at "http://repos.d.xiaonei.com/nexus/content/groups/public/"
    ),
    libraryDependencies ++= Seq(
      "log4j" % "log4j" % "1.2.17",
      "org.scalatest" %% "scalatest" % "2.2.2" % "test",
//      "org.apache.kafka" % "kafka" % "0.7.2",
      "com.101tec" % "zkclient" % "0.4"
//      "org.scala-lang" % "scala-reflect" % "2.8.0"
//      "org.scala-lang" % "scala-library" % "2.8.0"

	)
  )

  val slf4jVersion = "1.6.1"

//offsetmonitor project

  lazy val offsetmonitor = Project("offsetmonitor", file("."), settings = offsetmonSettings)

  def offsetmonSettings = sharedSettings ++ Seq(
  	  name := "KafkaOffsetMonitor",
	  libraryDependencies ++= Seq(
	  	"net.databinder" %% "unfiltered-filter" % "0.6.7",
		"net.databinder" %% "unfiltered-jetty" % "0.6.7",
		"net.databinder" %% "unfiltered-json" % "0.6.7",
		"com.quantifind" %% "sumac" % "0.2.3",
        "com.typesafe.slick" %% "slick" % "2.0.0",
        "org.xerial" % "sqlite-jdbc" % "3.7.2",
        "org.scala-lang" % "scala-reflect" % "2.8.0"
//		"com.twitter" % "util-core_2.11" % "6.20.0"
	  ),
	   resolvers ++= Seq(
	     "java m2" at "http://download.java.net/maven/2"
//		 "twitter repo" at "http://maven.twttr.com"
	)
  )
  
   mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => 
      { 
        case x if x startsWith "org/eclipse/" => MergeStrategy.first 
        case x => old(x) 
      } 
    } 
}
