package dase.algorithm

import dase.{PredictedResult, Query}
import dase.data.PreparedData
import grizzled.slf4j.Logger
import io.prediction.controller.{PAlgorithm, Params}
import io.prediction.data.storage.BiMap
import org.apache.spark.SparkContext
import org.apache.spark.mllib.recommendation.{ALS, Rating => MLlibRating}
import org.apache.spark.rdd.RDD

/**
  * PredictionIO automatically loads the parameters of `algorithms[].params` specified in `engine.json`
  * If `seed` is not specified, current system time would be used and hence each train may produce different results.
  * Specify a fixed value for the `seed` if you want to have deterministic result (i.e testing)
  */
case class ALSAlgorithmParams(rank: Int, numIterations: Int, lambda: Double, seed: Option[Long]) extends Params

class ALSAlgorithm(params: ALSAlgorithmParams) extends PAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {
  @transient private lazy val logger = Logger[this.type]

  if (params.numIterations > 30)
    logger.warn(s"ALSAlgorithmParams.numIterations > 30, current: ${params.numIterations}. There is a chance of running to StackOverflowException. Lower this number to remedy it")

  /**
    * `train` is called when you run `pio train`
    *
    * `ALS.train` returns `MatrixFactorizationModel` which contains RDD data.
    * RDD is a distributed collection of items which does not persist.
    * To store the model, you convert the model to `ALSModel` class at the end.
    * `ALSModel` is a persistable class that extends `MatrixFactorizationModel`
    *
    * PredictionIO will automatically store the returned model, i.e `ALSModel`
    */
  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    // MLLib ALS cannot handle empty training data.
    require(!data.ratings.isEmpty(), "RDD[Rating] in PreparedData cannot be empty. Please check if DataSource generates TrainingData and DataPreparator generates PreparedData correctly.")

    // Convert user and item String IDs to Int index for MLlib
    val userIDMap = BiMap.stringInt(data.ratings.map(_.user))
    val itemIDMap = BiMap.stringInt(data.ratings.map(_.item))

    val model = ALS.train(
      ratings = data.ratings.map(r => MLlibRating(userIDMap(r.user), itemIDMap(r.item), r.rating)),
      rank = params.rank,
      iterations = params.numIterations,
      lambda = params.lambda,
      blocks = -1,
      seed = params.seed.getOrElse(System.nanoTime)
    )

    new ALSModel(
      rank = model.rank,
      userFeatures = model.userFeatures,
      productFeatures = model.productFeatures,
      userIDMap = userIDMap,
      itemIDMap = itemIDMap
    )
  }

  /**
    * Called when you send a JSON query to `http:://localhost:8000/queries.json`
    * PredictionIO converts the query, such as `{"user": "1", "num": 4}` to the `Query` class you defined previously
    *
    * PredictionIO passes the returned `PredictedResult` object to `Serving`
    */
  def predict(model: ALSModel, query: Query): PredictedResult =
    model.getUserID(query.user) match {
      case Some(userID) =>
        val itemScores = model.recommendProducts(userID, query.num).map(r => PredictedResult.ItemScore(model.getItem(r.product).get, r.rating))

        PredictedResult(itemScores)

      case None =>
        logger.info(s"No prediction for unknown user ${query.user}.")
        PredictedResult(Array.empty)
    }

  // TODO: refactor this
  // This function is used by the evaluation module, where a batch of queries is sent to this engine
  // for evaluation purpose.
  override def batchPredict(model: ALSModel, queries: RDD[(Long, Query)]): RDD[(Long, PredictedResult)] = {
    val userIxQueries: RDD[(Int, (Long, Query))] = queries
      .map { case (index, query) =>
        (model.getUserID(query.user).getOrElse(-1), (index, query))
      }

    // Cross product of all valid users from the queries and products in the model.
    val usersProducts: RDD[(Int, Int)] = userIxQueries
      .keys
      .filter(_ != -1)
      .cartesian(model.productFeatures.map(_._1))

    // Call mllib ALS's predict function.
    val ratings: RDD[MLlibRating] = model.predict(usersProducts)

    // The following code construct predicted results from mllib's ratings.
    // Not optimal implementation. Instead of groupBy, should use combineByKey with a PriorityQueue
    val userRatings: RDD[(Int, Iterable[MLlibRating])] = ratings.groupBy(_.user)

    userIxQueries
      .leftOuterJoin(userRatings)
      .map {
        // When there are ratings
        case (userIx, ((ix, query), Some(ratings))) =>
          val topItemScores = ratings
            .toArray
            .sortBy(_.rating)(Ordering.Double.reverse) // note: from large to small ordering
            .take(query.num)
            .map(rating => PredictedResult.ItemScore(model.getItem(rating.product).get, rating.rating))

          (ix, PredictedResult(itemScores = topItemScores))

        // When user doesn't exist in training data
        case (userIx, ((ix, query), None)) =>
          require(userIx == -1)
          (ix, PredictedResult(itemScores = Array.empty))
      }
  }
}
