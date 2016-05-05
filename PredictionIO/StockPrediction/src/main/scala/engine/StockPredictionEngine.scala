package engine

import java.time.LocalDate

import dase.algorithm.{MomentumStrategy, RegressionStrategy}
import dase.data._
import dase.evaluator.BacktestingEvaluation
import io.prediction.controller.{Engine, EngineFactory}

case class Query(date: LocalDate, tickers: Seq[String])

case class PredictedResult(tickerPriceMap: Map[String, Double])

case class ActualResult()

object StockPredictionEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map(
        "yahoo" -> classOf[YahooDataSource]
      ),
      Map("" -> classOf[YahooDataPreparator]),
      Map(
        "regression" -> classOf[RegressionStrategy],
        "momentum" -> classOf[MomentumStrategy]
      ),
      Map("" -> classOf[BacktestingEvaluation])
    )
}
