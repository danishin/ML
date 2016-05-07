package dase.data

import java.time.ZoneId

import dase.data.time_series.StockTimeSeries
import dase.evaluator.EvaluationInfo
import engine.{ActualResult, Query}
import io.prediction.controller.{PDataSource, Params}
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.JObject

case class YahooDataSourceParams(appName: String, marketTicker: String, maxTestingWindowSize: Int) extends Params

case class TrainingData(stockTimeSeries: RDD[(String, StockTimeSeries)])

class YahooDataSource(params: YahooDataSourceParams) extends PDataSource[TrainingData, EvaluationInfo, Query, ActualResult] {
  private implicit val stockTimeZone = ZoneId.of("US/Eastern")

  private def readStockTimeSeries(sc: SparkContext): RDD[(String, StockTimeSeries)] = {
    val marketTimeSeries =
      PEventStore
        .find(
          appName = params.appName,
          entityType = Some("yahoo"),
          entityId = Some(params.marketTicker) // Only extracts market ticker as the main reference of market hours
        )(sc)
        .map(event => StockTimeSeries.from(event.properties.get[JObject]("yahoo"), checkContinuous = true))
        .first()

    PEventStore
      .find(
        appName = params.appName,
        entityType = Some("yahoo")
      )(sc)
      .map { e =>
        val dailyTimeSeries = StockTimeSeries.from(e.properties.get[JObject]("yahoo"), checkContinuous = false)
        val marketAlignedTimeSeries = marketTimeSeries.alignAndFill(dailyTimeSeries)
        e.entityId -> marketAlignedTimeSeries
      }
  }

  def readTraining(sc: SparkContext): TrainingData =
    TrainingData(readStockTimeSeries(sc))

//  override def readEval(implicit sc: SparkContext): Seq[(TrainingData, EvaluationInfo, RDD[(Query, ActualResult)])] = {
//    val stockTimeSeries = readStockTimeSeries
//
//    val evalInfo: EvaluationInfo = ???
//
//    // TODO: What to do???
//    val tickers = stockTimeSeries.colIx.toSeq
//
//    stockTimeSeries.rowIx.toVec
//      .rolling(params.maxTestingWindowSize, { dates =>
//        val trainingData = TrainingData(stockTimeSeries)
//        val queries  = sc.parallelize(dates.map(date => (Query(date, tickers), ActualResult())).toSeq)
//        (trainingData, evalInfo, queries)
//      })
//      .toSeq
//
//    ???
//  }
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
