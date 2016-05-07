package dase.algorithm

import java.time.LocalDate

import dase.data.PreparedData
import engine.{PredictedResult, Query}
import io.prediction.controller.P2LAlgorithm

trait StrategyModel extends Serializable {
  def predict(date: LocalDate, ticker: String): Option[Double]
}

trait Strategy [Model <: StrategyModel] extends P2LAlgorithm[PreparedData, Model, Query, PredictedResult] {
  def predict(model: Model, query: Query): PredictedResult = {
    val tickerPriceMap = query.tickers
      .flatMap(ticker => model.predict(query.date, ticker).map(price => (ticker, price)))
      .toMap

    PredictedResult(tickerPriceMap)
  }
}
