package dase.data

import dase.{ActualResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{EmptyEvaluationInfo, PDataSource, Params}
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.mllib.util.MLUtils
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

  /**
    * In recommendation evaluation, the raw data is a sequence of known ratings.
    * A rating has 3 components: user, item and a score.
    * We use k-fold method for evaluation. The raw data is sliced into a sequence of (training, validation) data tuple.
    *
    * In the validation data, we construct a query for each user, and get a list of recommended items from the engine.
    * It is vastly different from the classification tutorial, where there is a one-to-one corresponding between the training data point and the validation data point.
    * In this evaluation, our unit of evaluation is user. We evaluate the quality of an engine using the known rating of a user.
    */
  override def readEval(sc: SparkContext): Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    require(params.evalParams.nonEmpty, "Must specify evalParams")

    val evalParams = params.evalParams.get

    val kFold = evalParams.kFold
    val ratings = getRatings(sc)

    MLUtils.kFold(ratings, kFold, 0)
      .map { case (training, testing) =>
        (
          TrainingData(training),
          new EmptyEvaluationInfo(),
          // we group ratings by user, and one query is constructed for each user
          testing
            .groupBy(_.user)
            .map { case (user, groupedRatings) => (Query(user, evalParams.queryNum), ActualResult(groupedRatings.toArray)) }
          )
      }

  }
}

