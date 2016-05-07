package engine

import java.time.LocalDate

import dase.algorithm.{LinearRegressionStrategy, MomentumStrategy}
import dase.data._
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory}

case class Query(date: LocalDate, tickers: Seq[String])

case class PredictedResult(tickerPriceMap: Map[String, Double])

case class ActualResult(tickerPriceMap: Map[String, Double])

object StockPredictionEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map(
        "yahoo" -> classOf[YahooDataSource]
      ),
      Map("" -> classOf[YahooDataPreparator]),
      Map(
        "regression" -> classOf[LinearRegressionStrategy],
        "momentum" -> classOf[MomentumStrategy]
      ),
      Map("" -> classOf[Serving])
    )
}
