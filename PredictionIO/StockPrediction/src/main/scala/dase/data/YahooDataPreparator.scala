package dase.data

import io.prediction.controller.PPreparator
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

case class PreparedData(stockTimeSeries: RDD[(String, StockTimeSeries)])

// TODO: Move some appropriate logic out of data source
class YahooDataPreparator extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData =
    PreparedData(trainingData.stockTimeSeries)
}
