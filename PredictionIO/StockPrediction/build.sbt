import AssemblyKeys._

assemblySettings

lazy val stock_prediction = (project in new File(".")).enablePlugins(SbtTwirl)
scalaVersion := "2.10.6"

libraryDependencies ++= {
  val pioVersion = "0.9.5"
  val sparkVersion = "1.4.1"

  Seq(
    "io.prediction" %% "core" % pioVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
    "org.scala-saddle" %% "saddle-core" % "1.3.2" exclude("ch.qos.logback", "logback-classic")
  )
}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
{
  case PathList("scala", xs @ _*) => MergeStrategy.discard
  case PathList("org", "xmlpull", xs @ _*) => MergeStrategy.last
  case x => old(x)
}
}