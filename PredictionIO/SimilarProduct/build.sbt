import AssemblyKeys._

assemblySettings

name := "similarproduct"
scalaVersion := "2.10.6"

parallelExecution in Test := false

test in assembly := {}

libraryDependencies ++= {
  val pioVersion = "0.9.5"
  val sparkVersion = "1.4.1"

  Seq(
    "io.prediction" %% "core" % pioVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
    "org.scalatest" %% "scalatest" % "2.2.1" % "test"
  )
}
