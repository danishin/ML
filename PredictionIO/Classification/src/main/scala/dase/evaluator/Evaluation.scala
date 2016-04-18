package dase.evaluator

import engine.{ActualResult, ClassificationEngine, PredictedResult, Query}
import io.prediction.controller._

/**
  * Hyperparameter Tuning
  *
  * An engine often depends on a number of parameters, for example, the naive bayesian algorithm has a smoothing parameter (lambda) to make the model more adaptive to unseen data
  * Compared with parameters which are learned by the machine learning algorithm, this smoothing parameter teaches the algorithm how to work.
  * Therefore, such parameters are usally called hyperparameters.
  *
  * In PredictionIO, we always take a holistic view of an engine.
  * An engine is comprised of a set of DAS controllers, as well as the necessary parameters for the controllers themselves.
  * In the evaluation, we attempt to find out the best hyperparameters for an engine, which we call engine params.
  * Using engine params, we can deploy a complete engine.
  */

/**
  * Sensitivity Analysis
  *
  * A key concern of users of Bayesian statistics is the dependence of the posterior distribution on one's prior.
  * Hyperparameters address this by allowing one to easily vary them and see how the posterior distribution (and various statistics of it, such as credible intervals) vary.
  * One can see how sensitive one's conclusions are to one's prior assumptions and the process is called sensitivity analysis.
  *
  * Similar one may use a prior distribution with a range for a hyperparameter, perhaps reflecting uncertainty in the correct prior to take and reflect this in a range for final uncertainty
  */

/**
  * Prior vs Posterior
  *
  * The prior is a probability distribution that represents your uncertainty over quantity of interest before you have sampled any data and attempted to estimate it.
  * The posterior is a probability distribution representing your uncertainty over quantity of interest after you have sampled data.
  * It is conditional distribution because it conditions on the observed data.
  *
  * From Bayes' theorem we relate the two.
  *
  * Taking a step back, we widely accept that the process of having beliefs about the world, interacting with it and then updating these beliefs is a fundamental component of learning.
  * Computing the posterior from the prior with Bayes' theorem is simply a mathematical formulation of updating your beliefs.
  * In fact, these ideas are at the core of Bayesian statistics.
  *
  * In simple parlance, the prior is what you believe about some quantity at particular point in time, and the posterior is your belief once additional information comes in.
  * More specifically, the prior tells you the relative likelihood of different values of some quantity (a parameter) in the absence of data
  * The posterior tells you how you'd revise those beliefs in the presence of data
  * Note as well that data can keep coming in, so you can update your prior to a posterior, then update THAT posterior to another one etc.
  *
  * The way to connect these is exactly Bayes' theorem.
  * Modern Bayesian econometrics is the study of how this updating is done for a variety of statistical models, condtional on various sorts of data
  */

/* Metric
 *
 * We define metric to compare predicted result returned from the engine with the actual result which we obtained from the test data.
 * The goal is to maximize the metric score
 *
 * An engine serving better prediction should yield a higher metric score, the tuning module returns the engine parameter with the highest score.
 * It is sometimes called loss function in literature, where the goal is to minimize the loss function.
 *
 * During tuning, it is important for us to understand the definition of the metric, to make sure it is aligned with the prediction engine's goal.
 * In the classification, we use Accuracy as our metric. Accuracy is defined as: the percentage of queries where the engine is able to predict the correct label.
 *
 * COMMON METRICS
 *
 * Accuracy means that how many data points are predicted correctly.
 * It is one of the simplest form of evaluation metrics.
 * The accuracy score is # of correct points / # total
 *
 * Precision is a metric for binary classifier which measures the correctness among all positive labels.
 * A binary classifier gives only two output values (i.e positive and negative).
 * For problem where there are multiple values, we first have to transform our problem into a binary classification problem.
 * For example, we can have problem whether label = 1.0.
 * Precision is the ratio between the number of correct positive answer (true positive) and the sum of correct positive answer (true positive) and wrong but positively labeled answer (false positive).
 *
 * Recall is a metric for binary classifier which measures how many positive labels are successfully predicted amongst all positive labels.
 * Formally, it is the ratio between the number of correct positive answer (true positive) and the sum of correct positive answer (true positive) and wrongly negative labeled answer (false negative).
 */


/*
* Loss Function
*
* Loss funciton or cost function is a function that maps an event or values of one or more variables onto a real number intuitively representing some "cost" associated with the event.
* An optimization problem seeks to minimize a loss function.
* An objective function is either a loss function or its negative (sometimes called a reward/utility/fitness/profit function), in which case it is to be maximized
*
* In statistics, typically a loss function is used for parameter estimation, and the event in question is some function of the difference between estimated and true values for an instance of data
* In classification, it is the penalty for an incorrect classification of an example.
* */

/*
* Confusion Matrix
*
* In the field of machine learning and specifically the problem of statistical classification, a confusion matrix, also known as an error matrix, is a specific table layout that allows visualization of the performance of an algorithm, typically a supervised learning one.
* Each column of the matrix represents the instances in a predicted class while each row represents the instances in an actual class.
* The name stems from the fact that it makes it easy to see if the system is confusing two classes (i.e commonly mislabeling one as another)
*
* A confusion matrix is a table that is often used to describe the performance of a classification model (or a classifier) on a set of test data for which the true values are known.
* */

/* METRIC */
/**
  * Accuracy is a description of systematic errors, a measure of statistical bias.
  * Accuracy is a metric capturing the portion of correct prediction among all test data points.
  * A way to model this is for each correct QPA-tuple, we give a score of 1.0 and otherwise 0.0, then we take an average of all tuple scores.
  */
private class Accuracy extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult): Double =
    if (predicted.label == actual.label) 1.0 else 0.0
}

/**
  * Precision is a description of random errors, a measure of statistical variability
  * Precision is a metric for binary classifier capturing the portion of correction prediction among all positive predictions.
  * We don't care about the cases where the QPA-tuple gives a negative prediction.
  *
  */
private class Precision(label: Double) extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header: String = s"Precision(label = $label)"

  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult): Option[Double] =
    predicted.label match {
      case `label` =>
        // True positive
        if (predicted.label == actual.label) Some(1.0)
        // False positive
        else Some(0.0)

      // Unrelated case for calculating precision
      case _ => None
    }
}

/* EVALUATION */
object AccuracyEvaluation extends Evaluation {
  engineMetric = (ClassificationEngine(), new Accuracy)
}

object PrecisionEvaluation extends Evaluation {
  engineMetric = (ClassificationEngine(), new Precision(1.0))
}

object CompleteEvaluation extends Evaluation {
  engineEvaluator = (
    ClassificationEngine(),
    MetricEvaluator(
      metric = new Accuracy,
      otherMetrics = Seq(new Precision(0.0), new Precision(1.0), new Precision(2.0)),
      outputPath = "best.json"
    )
  )
}
