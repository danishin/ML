name := "machinelearning"
version := "1.0"
scalaVersion := "2.11.7"


libraryDependencies ++= {
  val SmileVersion = "1.1.0"

  Seq(
    /* JAVA */
    "com.github.haifengl" % "smile-core" % SmileVersion,
    "com.github.haifengl" % "smile-plot" % SmileVersion,

    /* SCALA */
    "com.github.haifengl" %% "smile-scala" % SmileVersion,
    "org.scala-lang" % "scala-swing" % "2.11.0-M7",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "com.github.tototoshi" %% "scala-csv" % "1.3.0"

  )
}
