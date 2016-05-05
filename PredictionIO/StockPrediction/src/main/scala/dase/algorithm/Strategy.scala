package dase.algorithm

import java.time.LocalDate

import dase.data._
import engine.{PredictedResult, Query}
import io.prediction.controller.LAlgorithm

case class QueryWithData(idx: Int, dataView: DataView, tickers: Array[String])

trait StrategyModel extends Serializable {
  def predict(date: LocalDate, tickers: Array[String]): Map[String, Double]
}

trait Strategy[Model <: StrategyModel] extends LAlgorithm[PreparedData, Model, Query, PredictedResult] {
  def createModel(dataView: DataView): Model

  def train(pd: PreparedData): Model = {
    pd.stocksTimeSeriesFrameB
  }

  def predict(dataModel: (TrainingData, Model), query: Query): PredictedResult = {
    val (trainingData, model) = dataModel

    val rawData = trainingData.stocksTimeSeriesFrameB.value
    val dataView = DataView(rawData, query.date, trainingData.maxWindowSize)

    val activeTickers = dataView
      .activeFrame()
      .rowAt(0)
      .filter(identity)
      .index
      .toVec
      .contents

    val queryWithData = QueryWithData(query.date, dataView, activeTickers)

    val tickerPriceMap = model.predict(query.date, activeTickers)

    PredictedResult(tickerPriceMap)
  }
}

class EmptyStrategy extends Strategy[AnyRef] {
  def createModel(dataView: DataView): AnyRef = None
  def onClose(model: AnyRef, query: QueryWithData): PredictedResult = PredictedResult(Map[String, Double]())
}
