package dase.data

import io.prediction.controller.PPreparator
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast

case class PreparedData(stocksTimeSeriesFrameB: Broadcast[StocksTimeSeriesFrame])

// TODO: Move some appropriate logic out of data source
class YahooDataPreparator extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData =
    PreparedData(trainingData.stocksTimeSeriesFrameB)
}
