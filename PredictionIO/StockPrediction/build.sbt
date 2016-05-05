import AssemblyKeys._

assemblySettings

lazy val stock_prediction = project in new File(".")
scalaVersion := "2.10.6"

libraryDependencies ++= {
  val pioVersion = "0.9.5"
  val sparkVersion = "1.4.1"

  Seq(
    "io.prediction"     %% "core"           % pioVersion % "provided",
    "com.github.scopt"  %% "scopt"          % "3.2.0",
    "org.apache.spark"  %% "spark-core"     % sparkVersion % "provided",
    "org.apache.spark"  %% "spark-mllib"    % sparkVersion % "provided",
    "org.clapper"       %% "grizzled-slf4j" % "1.0.2",
    "org.json4s"        %% "json4s-native"  % "3.2.10",
    "org.scala-saddle"  %% "saddle-core"    % "1.3.2" exclude("ch.qos.logback", "logback-classic"),
    "org.scalanlp"      %% "nak"            % "1.3",
    "org.scalatest"     %% "scalatest"      % "2.2.0" % "test"
  )
}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
{
  case PathList("scala", xs @ _*) => MergeStrategy.discard
  case PathList("org", "xmlpull", xs @ _*) => MergeStrategy.last
  case x => old(x)
}
}

lazy val root = (project in file(".")).enablePlugins(SbtTwirl)
