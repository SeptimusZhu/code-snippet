name := "yyyy"

version := "1.0-SNAPSHOT"
organization := "com.xxx"
scalaVersion := "2.11.11"

// parse the app's configure
lazy val module1 = project in file("module1")
lazy val module2 = project in file("module2")
lazy val module3 = (project in file("module3")).dependsOn(module1, module2)

lazy val root = (project in file(".")).aggregate(module1, module2, module3)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.3" % "test"
)

artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  artifact.name + "_" + sv.binary + "-" + module.revision + "." + artifact.extension
}