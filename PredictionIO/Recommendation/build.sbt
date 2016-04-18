import AssemblyKeys._

assemblySettings

name := "recommendation"
scalaVersion := "2.10.6" // Spark relies on 2.10.X. Therefore so does prediction io

libraryDependencies ++= {
  // Cannot use latest spark since predictionio breaks otherwise.
  // Best to refer to docket images like this https://github.com/mingfang/docker-predictionio/blob/master/Dockerfile for versioning
  val pioVersion = "0.9.5"
  val sparkVersion = "1.4.1"

  Seq(
    /* For development ONLY - will be 'provided' externally when run `deploy` (i.e the actual spark and pio processes) */
    "io.prediction" %% "core" % pioVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided"
  )
}
