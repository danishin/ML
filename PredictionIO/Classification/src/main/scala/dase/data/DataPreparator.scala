package dase.data

import io.prediction.controller.PPreparator
import org.apache.spark.SparkContext
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

case class PreparedData(labeledPoints: RDD[LabeledPoint])

class DataPreparator extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData =
    new PreparedData(trainingData.labeledPoints)
}
