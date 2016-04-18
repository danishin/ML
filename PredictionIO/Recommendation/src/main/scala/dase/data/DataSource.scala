package dase.data

import dase.{ActualResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{EmptyEvaluationInfo, PDataSource, Params}
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

// PredictionIO automatically loads the parameters of `datasource` specified in `engine.json`
case class DataSourceParams(appName: String, evalParams: Option[DataSourceParams.EvalParams]) extends Params
object DataSourceParams {
  case class EvalParams(kFold: Int, queryNum: Int)
}

// PredictionIO passes the returned `TrainingData` object to `Data Preparator`
case class TrainingData(ratings: RDD[TrainingData.Rating]) {
  override def toString = s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
}
object TrainingData {
  case class Rating(user: String, item: String, rating: Double)
}

class DataSource(params: DataSourceParams) extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, ActualResult] {
  @transient private lazy val logger = Logger[this.type]

  private def getRatings(sc: SparkContext): RDD[TrainingData.Rating] =
    PEventStore
      .find(
        appName = params.appName,
        entityType = Some("user"),
        eventNames = Some(List("rate", "buy")),
        targetEntityType = Some(Some("item"))
      )(sc)
      .map(event => event.event match {
        case "rate" =>
          val rating = event.properties.get[Double]("rating")
          TrainingData.Rating(event.entityId, event.targetEntityId.get, rating)
        case "buy" =>
          val rating = 4.0 // map buy event to rating value of 4
          TrainingData.Rating(event.entityId, event.targetEntityId.get, rating)
        case _ =>
          logger.error(s"Cannot convert $event to Rating.")
          throw new Exception(s"Unexpected event $event is read.")
      })
      .cache()

  def readTraining(sc: SparkContext): TrainingData = TrainingData(getRatings(sc))

  override def readEval(sc: SparkContext): Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    import dase.implicits._

    require(params.evalParams.nonEmpty, "Must specify evalParams")

    val evalParams = params.evalParams.get

    val kFold = evalParams.kFold
    val ratings = getRatings(sc)
      .zipWithUniqueId
      .cache()

    (0 until kFold).map { idx =>
      val (trainingRatings, testingRatings) = ratings.mapPartition(_._2 % kFold == idx)(_._1)

      (
        TrainingData(trainingRatings),
        new EmptyEvaluationInfo(),
        testingRatings
          .groupBy(_.user)
          .map { case (user, groupedRatings) => (Query(user, evalParams.queryNum), ActualResult(groupedRatings.toArray)) }
      )
    }
  }
}

