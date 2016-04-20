package dase.data

import io.prediction.controller.PPreparator
//import io.prediction.data.storage.BiMap

import grizzled.slf4j.Logger
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

/**
  * LabeledPoint class is defined by in MLlib and it's required for the RandomForest Algorithm
  * featureIndex is a Map of feature name to the position index in the feature vector.
  * featureCategoricalIntMap is a Map of categorical feature name to the Map of categorical value map for this feature
  *
  * By default, the feature used for classification is landingPage, referrer and browser.
  * Since these features contain categorical values, we need to create a map of categorical values to the integer values for the algorithm to use
  */
case class PreparedData(labeledPoints: RDD[LabeledPoint], featureIndex: Map[String, Int], featureCategoricalIntMap: Map[String, Map[String, Int]])

class DataPreparator extends PPreparator[TrainingData, PreparedData] {
  @transient lazy val logger = Logger[this.type]

  // values - categorical values
  // default - default categorical value
  private def createCategoricalIntMap(values: Array[String], default: String): Map[String, Int] = {
    val m = values.zipWithIndex.toMap
    if (m.contains(default)) m
    // add default value if original values don't have it
    else m + (default -> m.size)
  }

  def prepare(sc: SparkContext, data: TrainingData): PreparedData = {
    // find out all values of the each feature
    val landingValues = data.session.map(_.landingPageId).distinct.collect
    val referrerValues = data.session.map(_.referrerId).distinct.collect
    val browserValues = data.session.map(_.browser).distinct.collect


    // index position of each feature in the vector
    val featureIndex = Map(
      "landingPage" -> 0,
      "referrer" -> 1,
      "browser" -> 2
    )
    // map feature value to integer for each categorical feature
    val featureCategoricalIntMap = Map(
      "landingPage" -> createCategoricalIntMap(landingValues, ""),
      "referrer" -> createCategoricalIntMap(referrerValues, ""),
      "browser" -> createCategoricalIntMap(browserValues, "")
    )

    // inject some default to cover default cases
    val defaultRDD = sc.parallelize(Seq(Session("", "", "", buy = false), Session("", "", "", buy = true)))

    val labeledPoints =
      (data.session union defaultRDD)
        .map { session =>
          logger.debug(s"$session")
          val label = if (session.buy) 1.0 else 0.0

          val feature = new Array[Double](featureIndex.size)
          feature(featureIndex("landingPage")) = featureCategoricalIntMap("landingPage")(session.landingPageId).toDouble
          feature(featureIndex("referrer")) = featureCategoricalIntMap("referrer")(session.referrerId).toDouble
          feature(featureIndex("browser")) = featureCategoricalIntMap("browser")(session.browser).toDouble

          LabeledPoint(label, Vectors.dense(feature))
        }
        .cache()

    logger.debug(s"labelelPoints count: ${labeledPoints.count()}")
    PreparedData(labeledPoints, featureIndex, featureCategoricalIntMap)
  }
}
