name := "textclassification"
scalaVersion := "2.10.6"

libraryDependencies ++= {
  val pioVersion = "0.9.5"
  val sparkVersion = "1.4.1"

  Seq(
    "io.prediction" %% "core" % pioVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided"
  )
}
