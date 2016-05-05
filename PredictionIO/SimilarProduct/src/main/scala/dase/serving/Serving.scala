package dase.serving

import engine.{ItemScore, PredictedResult, Query}
import io.prediction.controller.LServing

/*
* Standard Score (z-score)
*
* In statistics, the standard score is the signed number of standard deviations an observation or datum is above the mean.
* A positive standard score indicates a datum above the mean, while a negative standard score indicates a datum below the mean.
* It is a dimensionless quantity obtained by subtracting population mean from an individual raw score and then dividing the difference by the population standard deviation.
* This conversion process is called standardizing or normalizing.
*
* Standard scores are also called z-values, z-scores, normal scores, and standardized variables
* The use of Z is because the normal distribution is also known as the Z distribution.
* They are most frequently used to compare a sample to a standard normal deviate, though they can be defined without assumptions of normality.
*
* The z-score is only defined if one knows the population parameters.
* If one only has a sample set, then the analogous computation with sample mean and sample standard deviation yields the Student's t-statistic
* */

/*
* When the engine is deployed, the Query is sent to all algorithms of the engine
*
* serve() function at first standardizes the PredictedResults of each algorithm so that we can combine the scores of multiple algorithms by adding the scores of the same item.
* Then we can take the top N items as defined in query.
* */
class Serving extends LServing[Query, PredictedResult] {
  def serve(query: Query, predictedResults: Seq[PredictedResult]): PredictedResult = {
    val standardized: Seq[Array[ItemScore]] =
      if (query.num == 1)
        predictedResults.map(_.itemScores)
      else
        predictedResults
          .map { pr =>
            val mv = breeze.stats.meanAndVariance(pr.itemScores.map(_.score))
            pr.itemScores.map(is => ItemScore(is.item, zscore(is.score, mv.mean, mv.stdDev)))
          }

    // sum the standardized score for same item
    val combined: Array[ItemScore] = standardized
      .flatten
      .groupBy(_.item)
      .mapValues(_.map(_.score).sum)
      .toArray
      .sortBy(_._2)(Ordering.Double.reverse)
      .take(query.num)
      .map(ItemScore.tupled)

    PredictedResult(combined)
  }

  // standardize score (z-score)
  // if standard deviation is 0 (when all items have the same score, meaning all items are ranking equally) return 0
  private def zscore(score: Double, mean: Double, stdDev: Double) =
    if (stdDev == 0) 0.0 else (score - mean) / stdDev
}
