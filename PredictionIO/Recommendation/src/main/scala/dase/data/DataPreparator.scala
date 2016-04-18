package dase.data

import io.prediction.controller.{PPreparator, Params}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.io.Source

case class PreparedData(ratings: RDD[TrainingData.Rating])

case class DataPreparatorParams(excludeItemsFilePath: Option[String]) extends Params

class DataPreparator(params: DataPreparatorParams) extends PPreparator[TrainingData, PreparedData] {
  /**
    * Takes `TrainingData` as its input and performs any necessary feature selection and data processing tasks.
    * It returns `PreparedData` which should contain the data `Algorithm` needs.
    * For MLlib ALS, it is RDD[Rating]
    * PredictionIO passes the returned `PreparedData` object to `Algorithm`'s `train` function
    *
    * Data Preparator is where pre-processing actions occur.
    * For example, one may want to remove some very popular items from the training data because she thinks that these items may not help finding individual person's tastes
    * or one may have a black list of item that she wants to remove from the training data before feeding it to the algorithm
    *
    * Data Source reads data from the data store of Event Server and then Data Preparator prepares RDD[Rating] for the ALS Algorithm
    */
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    val excludeItems = params.excludeItemsFilePath.map(p => Source.fromFile(p).getLines().toSet).getOrElse(Set())

    val ratings = trainingData.ratings.filter(r => !excludeItems.contains(r.item))

    PreparedData(ratings)
  }
}