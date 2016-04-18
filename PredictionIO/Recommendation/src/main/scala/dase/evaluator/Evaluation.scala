package dase.evaluator

import dase.{ActualResult, PredictedResult, Query}
import engine._
import io.prediction.controller._

/*
*
* PredictionIO engine is instantiated by a set of parameters, these parameters determines which algorithm is used as well as the parameter for the algorithm.
* It naturally raises a question of how to choose the best set of parameters.
* The evaluation module streamlines the process of tuning the engine to the best parameter set and deploy it.
* */

/*
* Key Assumptions
*
* There are multiple assumptions we have to make when we evaluate a recommendation engine:
*
* 1. Definition of 'good'.
* We want to quantify if the engine is able to recommend items which the user likes, we need to define what is meant by 'good'.
* In this example, we have two kind sof events: rate and buy.
* rate event is associated with a rating value which ranges between 1 to 4 and the buy event is mapped to a rating of 4.
* When we implement the metric, we have to specify a rating threshold, only the rating above the threshold is considered good.
*
* 2. The absence of complete rating.
* It is extremely unlikely that the training data contains rating for all user-item tuples.
* In contrast, of a system containing 1000 items, a user may only have rated 20 of them, leaving 980 items unrated.
* There is no way for us to certainly tell if the user likes an unrated product.
* When we examine the evaluation result, it is important for us to keep in mind that the final metric is only an approximation of the actual result.
*
* 3. Recommendation affects user behavior.
* Suppose you are a e-commerce company and would like to use the recommendation engine to personalize the landing page, the item you show in the landing page directly impacts what the user is going to purchase.
* This is different from weather prediction, whatever the weather forecase engine predicts, tomorrow's weather wont be affected.
* Therefore, when we conduct offline evaluation for recommendation engines, it is possible that the final user behavior is dramatically different from the evaluation result.
* However, in the evaluation, for simplicity, we have to assume that user behavior is homogenous
* */

/*
* Evaluation Metrics
*
* We used one metric to compute the quality of an engine variant.
* However, in acutal use cases like recommendation, as we have made many assumptions in our model, using a single metric may lead to a biased evaluation.
* We will discuss using multiple metrics to generate a comprehensive evaluation, to generate a more global view of the engine.
*
* Precision@K
* Precision@K measures the portion of relevant items amongs the first k items.
* Recommendation engine usually wants to make sure the first top few items recommended are appealing to the user.
* Think about Google Search, we usually give up after looking at the first and second result pages.
*
* Precision@K Parameters
* There are two question associated with it:
* 1. How do we define relevant?
* 2. What is a good value of k?
*
* Before we answer these questions, we need to understand what constitute a good metric is.
* It is like exams,
* if everyone gets full scores, the exam fails its goal to determine what the candidates don't know.
* if everyone fails, the exam fails its goal to determine what the candidates know.
* A good metric should be able to distinguish the good from the bad.
*
* A way to define relevant is to use the notion of rating threshold.
* If the user rating for an item is higher than a certain threshold, we say it is relevant.
* However, without looking at the data, it is hard to pick a reasonable threshold.
* We can set the threshold be as high as the maximum rating of 4.0, but it may severely limit the relevant set size and the precision scores will be close to zero or undefined (precision is undefined if there is no relevant data)
* On the other hand, we can set the threshold be as low as the minimum rating, but it makes the precision metric uninformative as well since all scores will be close to 1.
* Similar argument applies to picking a good value of k too.
*
* A method to choose a good parameter is not to choose one, but instead test out a whole spectrum of parameters.
* If an engine variant is good, it should robustly perform well across different metric parameters.
* */


// Usage:
// $ pio eval dase.evaluator.RecommendationEvaluation engine.EvalEngineParamsList

/* METRIC */


/**
  * PositiveCount is a helper metric that returns the average number of positive samples for a specific rating threshold, therefore we get some idea about the demographic of the data
  * If PositiveCount is too low or too high for certain threshold, we know that it should not be used.
  */
class PositiveCount(ratingThreshold: Double = 2.0) extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header = s"PositiveCount (threshold=$ratingThreshold)"

  def calculate(q: Query, p: PredictedResult, a: ActualResult): Double =
    a.ratings.count(_.rating >= ratingThreshold)
}

/**
  * Precision@K is the actual metrics we use.
  * We have two lists of parameters:
  * ratingThreshold defines what rating is good, and
  * k defines how many items we evaluate in the PredictedResult.
  * We generate a list of all combinations.
  */
class PrecisionAtK(k: Int, ratingThreshold: Double = 2.0) extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
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

/* EVALUATION */
object RecommendationEvaluation extends Evaluation {
  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = new PrecisionAtK(k = 10, ratingThreshold = 4.0),
      otherMetrics = Seq(
        new PositiveCount(ratingThreshold = 4.0),
        new PrecisionAtK(k = 10, ratingThreshold = 2.0),
        new PositiveCount(ratingThreshold = 2.0),
        new PrecisionAtK(k = 10, ratingThreshold = 1.0),
        new PositiveCount(ratingThreshold = 1.0)
      ))
    )
}

object ComprehensiveRecommendationEvaluation extends Evaluation {
  val ratingThresholds = Seq(0.0, 2.0, 4.0)
  val ks = Seq(1, 3, 10)

  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = new PrecisionAtK(k = 3, ratingThreshold = 2.0),
      otherMetrics = ratingThresholds.map(rt => new PositiveCount(rt)) ++ (ks zip ratingThresholds).map { case (k, ratingThreshold) => new PrecisionAtK(k, ratingThreshold) }
    ))
}

