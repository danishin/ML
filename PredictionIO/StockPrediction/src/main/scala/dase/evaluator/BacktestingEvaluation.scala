package dase.evaluator

import breeze.stats.{MeanAndVariance, meanAndVariance}
import dase.data.{EvalParams, RawData, StockData, StocksTimeSeriesFrame}
import engine.{PredictedResult, Query}
import io.prediction.controller.{Evaluation, Params}
import org.apache.spark.broadcast.Broadcast

import scala.collection.mutable.{ArrayBuffer, Map => MMap}

/**
  * Open Position
  *
  * An open position, in investing, is any trade that has been established, or entered, that has yet to be closed with an opposing trade.
  * An open position can exist following a buy (long) position, or a sell (short) position.
  * In either case, the position will remain open until an opposing trade has taken place.
  *
  * For example, an investor who owns 500 shares of a certain stock is said to have an open position in that stock. When the investor sells those 500 shares, the position will be closed
  * Buy-and-hold investors generally ahve one or more open positions at any given time.
  * Short-term traders may execute "round-trip" trades; a position is opended and closed within a relatively short period of time.
  * Day traders and scalpers may even open and close a position within a few seconds, trying the catch very small, but frequent, price movements throughout the day.
  *
  * Close Position
  *
  * Executing a security transaction that is the exact opposite of an open position, thereby nullifying it and eliminating the initial exposure.
  * Closing a long position in a security would entail selling it, while closing a short position in a security would involve buying it back.
  * The difference between the price at which the position in a security was opened, or initiated, and the price at which it was closed, represents the gross profit or loss on that security position.
  * Taking offseting positions in swaps is also very common to eliminate prior to maturity.
  *
  * Also known as "position squaring"
 */

/*
* Drawdown
*
* A drawdown is the peak-to-trough decline during a specific record period of an investment, fund or commodity.
* A drawdown is usually quoted as the percentage between the peak and the trough
*
* A drawdown is measured from the time a retrenchment begins to when a new high is reached.
* This method is used because a valley cannot be measure until a new high occurs.
* Once the new high is reached, the percentage change from the old high to the smallest trough is recorded.
*
* Drawdowns help determine an investment's financial risk.
* Both the Calmar and Sterling ratios use this metric to compare a security's possible reward to its risk
*
* Trough
*
* A trough is the stage of the economy's business cycle that marks the end of a period of declining business activity and the transition to expansion.
* In general, the business cycle is said to go through expansion, then the peak, followed by contraction and then it finally bottoms out with the trough.
* */

/*
* Volatility
*
* Volatility is a statistical measure of the dispersion of returns for a given security or market index.
* Volatility can either be measured using the standard deviation or variance between returns from that same security or maket index.
* Commonly the higher the volatility, the riskier the security.
*
* A variable in option pricing formulas showing the extent to which the return of the underlying asset will fluctuate between now and the option's expiration.
* Volatility, as expressed as a percentage coefficient within the option-pricing formulas, arises from daily trading activities.
* How volatility is measured will affect the value of the coefficient used.
*
* In other words, volatility refers to the amount of uncertainty or risk about the size of changes in a security's value.
* A higher volatility means that a security's value can potentially be spread out over a larger range of values.
* This means that the price of the security can change dramatically over a short time period in either direction.
* A lower volatility means that a security's value does not fluctuate dramatically, but changes in value at a steady pace over a period of time.
*
* One measure of the relative volatility of a particular stock to the market is its beta.
* A beta approximates the overall volatility of a security's returns against the returns of a relevant benchmakr (usually S&P 500 is used).
* For example, a stock with a beta value of 1.1 has historically moved 110* for every 100% move in the benchmark based on price level.
* */

/*
* Beta
*
* Beta is a measure of the volatility, or systematic risk, of a security or a portfolio in comparison to the market as a whole.
* Beta is used in the capital asset pricing model (CAPM), a model that calculates the expected return of an asset based on its beta and expected market return.
*
* Also known as "beta coefficient"
* Beta is calculated using regression analysis, and you can think of beta as the tendency of a security's returns to respond to the swings in the market.
* */

case class EvaluationInfo(stocksTimeSeriesFrame: Broadcast[StocksTimeSeriesFrame])

/**
  *
  * @param enterThreshold the minimum predicted return to open a new position
  * @param exitThreshold the maximum predicted return to close an existing position
  * @param maxPositions the maximum number of open positions.
  * @param optOutputPath
  *
  * Everyday, this metrics adjusts its portfolio based on the stock algorithm's `PredictedResult`.
  * For a current position, if its predicted return is lower than the `exitThreshold`, then metrics will close this position.
  * On the other hand, metrics will look at all stocks that have predicted return higher than enterThreshold, and will repeatedly open new ones with maximum predicted value until it reaches `maxPositions`
  */
case class BacktestingEvaluationParams(enterThreshold: Double, exitThreshold: Double, maxPositions: Int = 1, optOutputPath: Option[String] = None) extends Params

// prediction is Ticker -> ({1:Enter, -1:Exit}, ActualReturn)
case class DailyResult(dateIdx: Int, toEnter: Seq[String], toExit: Seq[String])

case class DailyStat(time: Long, nav: Double, ret: Double, market: Double, positionCount: Int)
case class OverallStat(ret: Double, vol: Double, sharpe: Double, days: Int)

case class BacktestingResult(daily: Seq[DailyStat], overall: OverallStat)

/**
  * Backtesting Metrics
  *
  * This is the most common method in quantatitive equity research.
  * We test the prediction algorithm against historical data.
  * For each day in the evaluation period, we open or close positions according to the prediction of the algorithm.
  * This allows us to simulate the daily P/L (Profit and Loss Statement == Income Statement), volatility, and drawdown of the prediction algorithm.
  */
class BacktestingEvaluation(params: BacktestingEvaluationParams) extends Evaluation {
  def evaluateUnit(query: Query, prediction: PredictedResult, unusedActual: AnyRef): DailyResult = {
    val todayIdx = query.date

    // Decide enter / exit, also sort by pValue desc
    val data = prediction.tickerPriceMap
      .map { case (ticker, pValue) =>
        val dir = pValue match {
          case p if p >= params.enterThreshold => 1
          case p if p <= params.exitThreshold => -1
          case _ => 0
        }
        (ticker, dir, pValue)
      }
      .toArray
      .sortBy(-_._3)

    val toEnter = data.filter(_._2 == 1).map(_._1)
    val toExit = data.filter(_._2 == -1).map(_._1)

    new DailyResult(
      dateIdx = todayIdx,
      toEnter = toEnter,
      toExit = toExit)
  }

  def evaluateSet(dp: EvaluationInfo, input: Seq[DailyResult]): Seq[DailyResult] = input

  def evaluateAll(input: Seq[(EvaluationInfo, Seq[DailyResult])]): BacktestingResult = {
    val dailyResultsSeq = input
      .flatMap(_._2)
      .toArray
      .sortBy(_.dateIdx)

    val rawData = input.head._1.stocksTimeSeriesFrame.value
    val retFrame = rawData._retFrame
    val priceFrame = rawData._priceFrame
    val mktTicker = rawData.marketTicker

    val dailyNavs = ArrayBuffer[Double]()

    val dailyStats = ArrayBuffer[DailyStat]()

    val initCash = 1000000.0
    var cash = initCash
    // Ticker to current size
    val positions = MMap[String, Double]()
    val maxPositions = params.maxPositions

    for (daily <- dailyResultsSeq) {
      val todayIdx = daily.dateIdx
      val today = rawData.timeIndex(todayIdx)
      val todayRet = retFrame.rowAt(todayIdx)
      val todayPrice = priceFrame.rowAt(todayIdx)

      // Update price change
      positions.keys.foreach { ticker => {
        positions(ticker) *= todayRet.first(ticker).get
      }}

      // Determine exit
      daily.toExit.foreach { ticker => {
        if (positions.contains(ticker)) {
          val money = positions.remove(ticker).get
          cash += money
        }
      }}

      // Determine enter
      val slack = maxPositions - positions.size
      val money = cash / slack
      daily.toEnter
        .filter(t => !positions.contains(t))
        .take(slack)
        .map{ ticker => {
          cash -= money
          positions += (ticker -> money)
        }}

      // Book keeping
      val nav = cash + positions.values.sum

      val ret =
        if (dailyStats.isEmpty) 0
        else {
          val yestStats = dailyStats.last
          val yestNav = yestStats.nav
          (nav - yestNav) / nav - 1
        }

      dailyStats.append(DailyStat(
        time = today.getMillis,
        nav = nav,
        ret = ret,
        market = todayPrice.first(mktTicker).get,
        positionCount = positions.size
      ))
    }
    // FIXME. Force Close the last day.

    val lastStat = dailyStats.last

    //val dailyVariance = meanAndVariance(dailyStats.map(_.ret))._2
    //val dailyVariance = meanAndVariance(dailyStats.map(_.ret))._2
    val retStats: MeanAndVariance = meanAndVariance(dailyStats.map(_.ret))
    //val dailyVol = math.sqrt(dailyVariance)
    //val annualVol = dailyVariance * math.sqrt(252.0)
    val annualVol = retStats.stdDev * math.sqrt(252.0)
    val n = dailyStats.size
    val totalReturn = lastStat.nav / initCash

    val annualReturn = math.pow(totalReturn, 252.0 / n) - 1
    val sharpe = annualReturn / annualVol

    val overall = OverallStat(
      annualReturn,
      annualVol,
      sharpe,
      n)

    val result = BacktestingResult(
      daily = dailyStats,
      overall = overall
    )

    params.optOutputPath.map { path => MV.save(result, path) }

    result
  }
}

object RenderMain {
  def main(args: Array[String]) {
    MV.render(MV.load[BacktestingResult](args(0)), args(0))
  }
}
