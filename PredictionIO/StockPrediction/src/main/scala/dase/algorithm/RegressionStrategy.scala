package dase.algorithm

import java.time.LocalDate

import breeze.linalg.{DenseMatrix, DenseVector}
import dase.data.{DataView, Indicator, PreparedData, RSIIndicator}
import engine.{PredictedResult, Query}
import nak.regress.LinearRegression
import io.prediction.controller.{LAlgorithm, P2LAlgorithm, Params}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.{LabeledPoint, LinearRegressionWithSGD}
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import org.saddle.{Frame, Series}

import scala.collection.immutable.HashMap

/** Regression Strategy parameters case class
  *
  * @param indicators a sequence of Indicators
  * @param maxTrainingWindowSize maximum window size of price frame desired for training
  */
case class RegressionStrategyParams(indicators: Seq[Indicator], maxTrainingWindowSize: Int) extends Params

class RegressionStrategyModel(tickerCoefficientMap: Map[String, DenseVector[Double]], priceFrame: Frame[LocalDate, String, Double]) extends Serializable {
  private lazy val tickers = tickerCoefficientMap.keys.toSet

  def predict(date: LocalDate, ticker: String)(implicit params: RegressionStrategyParams): Option[Double] =
    if (!tickers.contains(ticker)) None
    else {
      val prices = priceFrame.firstCol(ticker)

      val rsiArray = params.indicators.map { indicator =>
        val windowedPriceSeries = prices.splitBy(date)._1.tail(indicator.getMinWindowSize)
        indicator.getOne(windowedPriceSeries.mapValues(math.log))
      }.toArray

      val coef = tickerCoefficientMap(ticker)

      // TODO: What this for?
      val denseRSIArray = rsiArray ++ Array[Double](1)
      val rsiVector = DenseVector(denseRSIArray)

      Some(coef dot rsiVector)
    }
}

/**
  * Creates a linear model for each stock using a vector comprised of the 1-day, 1-week, and 1-month return of the stock
  */
class RegressionStrategy(implicit params: RegressionStrategyParams) extends P2LAlgorithm[PreparedData, RegressionStrategyModel, Query, PredictedResult] {
  import common.implicits._

  def train(sc: SparkContext, pd: PreparedData): RegressionStrategyModel = {
    // Regress on specific ticker
    def regress(windowedRSIArray: Seq[Series[LocalDate, Double]], returnFor1Day: Series[LocalDate, Double]): DenseVector[Double] = {
      val array = windowedRSIArray.map(_.toVec.contents).reduce(_ ++ _) ++ Array.fill(returnFor1Day.length)(1.0)

      val observations = DenseMatrix.create(returnFor1Day.length, windowedRSIArray.length + 1, array)
      val outputs = DenseVector(returnFor1Day.toVec.contents)

      // TODO: START FROM HERE!!! Learn DataFrame API and use it to model in place of Frame[LocalDate, String, Daily] and use LinearRegression from ml to do prediction
      {
        val labeledPoints = (returnFor1Day.toSeq zip windowedRSIArray)
          .map { case ((_, ret), timeSeries) => LabeledPoint(ret, Vectors.dense(timeSeries.toSeq.map(_._2).toArray)) }

        val labeledPointsRDD: RDD[LabeledPoint] = ???

        val model = LinearRegressionWithSGD.train(labeledPointsRDD, 100)

        val tickerWindowedRSIVector = Vectors.dense(0)
        model.predict(tickerWindowedRSIVector)
      }

      LinearRegression.regress(observations, outputs)
    }

    val stockTimeSeries = pd.stockTimeSeries

    // price: row is time, col is ticker, values are prices
    //    val price = dataView.priceFrame(params.maxTrainingWindowSize)
    //    val logPrice = price.mapValues(math.log)
    //    val active = dataView.activeFrame(params.maxTrainingWindowSize)

    // value used to query prediction results


    stockTimeSeries
      .filter(_._2.forall(_.active))
      .map { case (ticker, timeSeries) =>
        val prices = timeSeries.mapValues(_.adjClose)

        val trainingData = params.indicators.map(_.getTraining(prices).toSeq.map(_._2))
        val windowedRSIArr = trainingData.map(_.slice(???, ???))

        val returns: Series[LocalDate, Double] = {
          val day = 1
          val logPrices = prices.mapValues(math.log)

          // calculate return with offset of 1 day
          (logPrices - logPrices.shift(-day)).fillNA(_ => 0.0)
        }



        regress(windowedRSIArr, returns)
      }


    // TODO: Need to slice using max training window size?
    val price = stockTimeSeries.mapValues(_.adjClose)
    val returnFor1Day: Frame[LocalDate, String, Double] = {
      val day = 1
      val logPrice = price.mapValues(math.log)

      // calculate return with offset of 1 day
      (logPrice - logPrice.shift(-day)).mapVec(_.fillNA(_ => 0.0))
    }

    // Get max period from series of indicators
    val firstIdx = params.indicators.map(_.getMinWindowSize).max + 3
    val lastIdx = returnFor1Day.rowIx.length

    // Get array of ticker strings
    //    val tickers = returnFor1Day.colIx.toVec.contents
    // TODO: START FROM HERE!!!!
    val activeTickers = stockTimeSeries.toColSeq.collect { case (ticker, timeSeries) if timeSeries.forall(_.active) => ticker }

    activeTickers
      .map { ticker =>
        val rsiArr = params.indicators.map(_.getTraining(price.firstCol(ticker)))
        val windowedRSIArray = rsiArr.map(_.slice(firstIdx, lastIdx))

        val model = regress(
          windowedRSIArray = windowedRSIArray,
          returnFor1Day = returnFor1Day.firstCol(ticker).slice(firstIdx, lastIdx)
        )

        (ticker, model)
      }

    // For each active ticker, pass in trained series into regress
    //    val tickerModelMap = tickers
    //      .filter(ticker => active.firstCol(ticker).findOne(_ == false) == -1)
    //      .map { ticker =>
    //        val model = regress(
    //          calcIndicator(price.firstCol(ticker)).map(_.slice(firstIdx, lastIdx)),
    //          returnFor1Day.firstCol(ticker).slice(firstIdx, lastIdx)
    //        )
    //
    //        (ticker, model)
    //      }.toMap

    // tickers mapped to model
    tickerModelMap

    ???
  }

  def predict(model: RegressionStrategyModel, query: Query): PredictedResult = {
    val tickerPriceMap = query.tickers
      .flatMap(ticker => model.predict(query.date, ticker).map(price => (ticker, price)))
      .toMap

    PredictedResult(tickerPriceMap)
  }
}
