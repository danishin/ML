package dase.data

import java.time.{LocalDate, ZoneId}

import dase.evaluator.EvaluationInfo
import engine.{ActualResult, Query}
import io.prediction.controller.{PDataSource, Params}
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.json4s._
import org.saddle.Frame

case class YahooDataSourceParams(appName: String, entityType: String, fromDate: LocalDate, toDate: LocalDate, marketTicker: String, tickerList: Seq[String], maxTestingWindowSize: Int) extends Params

case class TrainingData(stocksTimeSeriesFrameB: Broadcast[StocksTimeSeriesFrame])

class YahooDataSource(params: YahooDataSourceParams) extends PDataSource[TrainingData, EvaluationInfo, Query, AnyRef] {
  private val timezone = ZoneId.of("US/Eastern")

  private def getStocksTimeSeriesFrame(implicit sc: SparkContext): StocksTimeSeriesFrame = {
    val marketTimeSeries =
      PEventStore.find(
        appName = params.appName,
        entityType = Some(params.entityType),
        entityId = Some(params.marketTicker) // // Only extracts market ticker as the main reference of market hours
      )(sc)
        .map(event => StockTimeSeries.from(event.properties.get[JObject]("yahoo"), params.fromDate, params.toDate, checkContinuous = true))
        .first()

    // TODO:
    val defaultTickerMap = params.tickerList
      .map(ticker => ticker -> StockTimeSeries.empty(marketTimeSeries.index))

    val tickers = params.tickerList :+ params.marketTicker
    val tickerArr =
      PEventStore.find(
        appName = params.appName,
        entityType = Some(params.entityType)
      )(sc)
        .collect { case e if tickers.contains(e.entityId) =>
          val dailyTimeSeries = StockTimeSeries.from(e.properties.get[JObject]("yahoo"), params.fromDate, params.toDate, checkContinuous = false)
          val marketAlignedTimeSeries = marketTimeSeries.alignAndFill(dailyTimeSeries)
          e.entityId -> marketAlignedTimeSeries
        }
        .collect()

     Frame(tickerArr: _*)
  }

  def readTraining(implicit sc: SparkContext): TrainingData =
    TrainingData(sc.broadcast(getStocksTimeSeriesFrame))

  override def readEval(implicit sc: SparkContext): Seq[(TrainingData, EvaluationInfo, RDD[(Query, ActualResult)])] = {
    val stocksTimeSeriesFrame = getStocksTimeSeriesFrame

    // Broadcast it.
    val stocksTimeSeriesFrameB = sc.broadcast(stocksTimeSeriesFrame)

    val evalInfo = EvaluationInfo(stocksTimeSeriesFrameB)

    val tickers = stocksTimeSeriesFrame.colIx.toSeq

    stocksTimeSeriesFrame.rowIx.toVec
      .rolling(params.maxTestingWindowSize, { dates =>
        val trainingData = TrainingData(stocksTimeSeriesFrameB)
        val queries  = sc.parallelize(dates.map(date => (Query(date, tickers), ActualResult())).toSeq)
        (trainingData, evalInfo, queries)
      })
      .toSeq
  }
}

//object PredefinedDSP {
//  val BigSP500 = YahooDataSourceParams(
//    appName = "StockPrediction",
//    entityType = "yahoo",
//    windowParams = DataSourceParams(
//      baseDate = new DateTime(2000, 1, 1, 0, 0),
//      fromIdx = 250,
//      untilIdx = 3500,
//      trainingWindowSize = 200,
//      maxTestingWindowSize = 30,
//      marketTicker = "SPY",
//      tickerList = Run.sp500List
//    )
//  )
//
//  val SmallSP500 = YahooDataSourceParams(
//    appName = "StockPrediction",
//    entityType = "yahoo",
//    windowParams = DataSourceParams(
//      baseDate = new DateTime(2000, 1, 1, 0, 0),
//      fromIdx = 250,
//      untilIdx = 3500,
//      trainingWindowSize = 200,
//      maxTestingWindowSize = 30,
//      marketTicker = "SPY",
//      tickerList = Run.sp500List.take(25)
//    )
//  )
//
//  val Test = YahooDataSourceParams(
//    appName = "StockPrediction",
//    entityType = "yahoo",
//    windowParams = DataSourceParams(
//      baseDate = new DateTime(2014, 1, 1, 0, 0),
//      fromIdx = 20,
//      untilIdx = 50,
//      trainingWindowSize = 15,
//      maxTestingWindowSize = 10,
//      marketTicker = "SPY",
//      tickerList = Seq("AAPL", "MSFT", "IBM", "FB", "AMZN", "IRONMAN")
//    )
//  )
//}

//object EngineParamsList extends EngineParamsGenerator {
//  private val baseEP = EngineParams(
//    dataSourceParams = PredefinedDSP.SmallSP500
//  )
//
//  engineParamsList = Seq(
//    baseEP.copy(algorithmParamsList = Seq(
//      ("regression", RegressionStrategyParams(Seq(new RSIIndicator(1), new RSIIndicator(5), new RSIIndicator(22)), 200))
//    ))
//  )
//}

//object YahooDataSourceRun {
//
//  def main(args: Array[String]) {
//    // Make sure you have a lot of memory.
//    // --driver-memory 12G
//
//    // val dsp = PredefinedDSP.BigSP500
//    val dsp = PredefinedDSP.SmallSP500
//    //val dsp = PredefinedDSP.Test
//
//    val momentumParams = MomentumStrategyParams(20, 3)
//
//    //val x =  Series(Vec(1,2,3))
//    //println(x)
//
//    val metricsParams = BacktestingEvaluationParams(
//      enterThreshold = 0.01,
//      exitThreshold = 0.0,
//      maxPositions = 10//,
//      //optOutputPath = Some(new File("metrics_results").getCanonicalPath)
//    )
//
//    Workflow.run(
//      dataSourceClassOpt = Some(classOf[YahooDataSource]),
//      dataSourceParams = dsp,
//      preparatorClassOpt = Some(IdentityPreparator(classOf[YahooDataSource])),
//      algorithmClassMapOpt = Some(Map(
//        //"" -> classOf[MomentumStrategy]
//        "" -> classOf[RegressionStrategy]
//      )),
//      //algorithmParamsList = Seq(("", momentumParams)),
//      algorithmParamsList = Seq(("", RegressionStrategyParams(Seq[(String, Indicator)](
//        ("RSI1", new RSIIndicator(rsiPeriod=1)),
//        ("RSI5", new RSIIndicator(rsiPeriod=5)),
//        ("RSI22", new RSIIndicator(rsiPeriod=22))),
//        200))),
//      servingClassOpt = Some(LFirstServing(classOf[EmptyStrategy])),
//      evaluatorClassOpt = Some(classOf[BacktestingEvaluation]),
//      evaluatorParams = metricsParams,
//      params = WorkflowParams(
//        verbose = 0,
//        saveModel = false,
//        batch = "Imagine: Stock III"))
//  }
//}
