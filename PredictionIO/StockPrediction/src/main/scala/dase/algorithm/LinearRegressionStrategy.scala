package dase.algorithm

import java.time.LocalDate

import dase.data.{Indicator, Indicators, PreparedData}
import io.prediction.controller.Params
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.{LabeledPoint, LinearRegressionModel, LinearRegressionWithSGD}
import org.saddle.Series

case class LinearRegressionStrategyParams(indicatorNames: Seq[String]) extends Params

class LinearRegressionStrategyModel(tickerModelMap: Map[String, (LinearRegressionModel, Series[LocalDate, Double])], indicators: Seq[Indicator]) extends StrategyModel {
  import common.implicits._

  def predict(date: LocalDate, ticker: String): Option[Double] =
    tickerModelMap.get(ticker)
      .map { case (model, prices) =>
        val indicatorResults = indicators
          .map { indicator =>
            val windowedPriceSeries = prices.sliceLeftFrom(date, indicator.getMinWindowSize)
            indicator.getLast(windowedPriceSeries.mapValues(math.log))
          }
          .toArray

        // TODO: This will return log price not price????
        model.predict(Vectors.dense(indicatorResults))
      }
      .orElse { println(s"Unsupported ticker: $ticker"); None }
}

/**
  * Creates a linear model for each stock using a vector comprised of the 1-day, 1-week, and 1-month return of the stock
  */
class LinearRegressionStrategy(params: LinearRegressionStrategyParams) extends Strategy[LinearRegressionStrategyModel] {
  import common.implicits._

  private val indicators = Indicators.from(params.indicatorNames)

  def train(sc: SparkContext, pd: PreparedData): LinearRegressionStrategyModel = {
    val tickerModelMap = pd.stockTimeSeries
      .filter(_._2.forall(_.active))
      .map { case (ticker, timeSeries) =>
        val logPrices = timeSeries.mapValues(_.adjClose).mapValues(math.log)

        val indicatorsResults = indicators.map(_.getTraining(logPrices))
//        // TODO: Need to slice?
//        val windowedTrainingData = trainingData.map(_.slice(???, ???))

        val dailyReturnSeries = (logPrices - logPrices.shift(1)).fillNA(_ => 0.0)

        val labeledPoints = (dailyReturnSeries.values.toSeq zip indicatorsResults.map(_.values.toSeq).transpose)
          .map { case (dailyReturn, indicatorResults) => LabeledPoint(dailyReturn, Vectors.dense(indicatorResults.toArray)) }

        ticker -> (LinearRegressionWithSGD.train(sc.parallelize(labeledPoints), 100), logPrices)
      }
      .collectAsMap()
      .toMap

    new LinearRegressionStrategyModel(tickerModelMap, indicators)
  }
}
