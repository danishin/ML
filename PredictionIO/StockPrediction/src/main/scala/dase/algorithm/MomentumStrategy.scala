package dase.algorithm

import dase.data.DataView
import engine.PredictedResult
import io.prediction.controller.Params

// Buy if l-days daily return is high than s-days daily-return
case class MomentumStrategyParams(l: Int, s: Int) extends Params

/**
  * Momentum Investing
  *
  * Momentum investing is an investing strategy tha aims to capitalize on the continuance of existing trends in the market.
  * The momentum investor believes that large increases in the price of a security will be followed by additional gains and vice versa for declining values.
  *
  * Momentum and rate of change (ROC) are a simple technical analysis indicators showing the difference between today's closing price and the close N days ago.
  *
  * LogPrice: A series of logarithm of all prices for a particular stock. Logarithm values are recommended for more accurate results.
  */
class MomentumStrategy(params: MomentumStrategyParams) extends Strategy[AnyRef] {
  def createModel(dataView: DataView): AnyRef = None

  def onClose(model: AnyRef, query: QueryWithData): PredictedResult = {
    val dataView = query.dataView

    val priceFrame = dataView.priceFrame(params.l + 1)
    val todayLogPrice = priceFrame.rowAt(params.l).mapValues(math.log)
    val lLogPrice = priceFrame.rowAt(0).mapValues(math.log)
    val sLogPrice = priceFrame.rowAt(params.l - params.s).mapValues(math.log)

    val lLogReturn = (todayLogPrice - lLogPrice) / params.l
    val sLogReturn = (todayLogPrice - sLogPrice) / params.s

    val output = query.tickers
      .map { ticker => {
        val s = sLogReturn.first(ticker)
        val l = lLogReturn.first(ticker)
        val p = l - s
        (ticker, p)
      }}
      .toMap

    PredictedResult(output)
  }
}
