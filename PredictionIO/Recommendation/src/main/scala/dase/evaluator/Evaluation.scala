package dase.evaluator

import dase.{ActualResult, PredictedResult, Query}
import engine._
import io.prediction.controller._

/*
*
* PredictionIO engine is instantiated by a set of parameters, these parameters determines which algorithm is used as well as the parameter for the algorithm.
* It natually raises a question of how to choose the best set of parameters.
* The evaluation module streamlines the process of tuning the engine to the best parameter set and deploy it.
* */

// TODO: START FROM HERE!!!! Read evaluation doc and try tweaking params for best results. Also read the entire docs that you haven't read yet. Then do UniversalRecommender template

// Usage:
// $ pio eval dase.evaluator.RecommendationEvaluation engine.EvalEngineParamsList

case class PrecisionAtK(k: Int, ratingThreshold: Double = 2.0) extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  require(k > 0, "k must be greater than 0")

  override def header = s"Precision@K (k=$k, threshold=$ratingThreshold)"

  def calculate(q: Query, p: PredictedResult, a: ActualResult): Option[Double] = {
    val positives = a.ratings.filter(_.rating >= ratingThreshold).map(_.item).toSet

    // If there is no positive results, Precision is undefined. We don't consider this case in the
    // metrics, hence we return None.
    if (positives.isEmpty) return None

    val tpCount = p.itemScores.take(k).count(is => positives(is.item))

    Some(tpCount.toDouble / math.min(k, positives.size))
  }
}

case class PositiveCount(ratingThreshold: Double = 2.0) extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header = s"PositiveCount (threshold=$ratingThreshold)"

  def calculate(q: Query, p: PredictedResult, a: ActualResult): Double =
    a.ratings.count(_.rating >= ratingThreshold)
}

object RecommendationEvaluation extends Evaluation {
  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = PrecisionAtK(k = 10, ratingThreshold = 4.0),
      otherMetrics = Seq(
        PositiveCount(ratingThreshold = 4.0),
        PrecisionAtK(k = 10, ratingThreshold = 2.0),
        PositiveCount(ratingThreshold = 2.0),
        PrecisionAtK(k = 10, ratingThreshold = 1.0),
        PositiveCount(ratingThreshold = 1.0)
      ))
    )
}

object ComprehensiveRecommendationEvaluation extends Evaluation {
  val ratingThresholds = Seq(0.0, 2.0, 4.0)
  val ks = Seq(1, 3, 10)

  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = PrecisionAtK(k = 3, ratingThreshold = 2.0),
      otherMetrics = ratingThresholds.map(PositiveCount) ++ (ks zip ratingThresholds).map(PrecisionAtK.tupled)
    ))
}

