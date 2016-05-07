package dase.algorithm

import dase.data.{PreparedData, TFIDFModel}
import engine.{PredictedResult, Query}
import io.prediction.controller.{P2LAlgorithm, Params}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{NaiveBayes, NaiveBayesModel}
import org.apache.spark.mllib.linalg.Vector

import scala.math._

/*
* Naive Bayes
*
* Naive Bayes is a simple multiclass classification algorithm with the assumption of independence between every pair of features
* Naive Bayes can be trained very efficiently.
* Within a single pass to the training data, it computes the conditional probability distribution of each feature given label,
* and then applies Bayes' theorem to compute the conditional probability distribution of label given an observation
* and use it for prediction
*
* Multinomial Naive Bayes model is typically used for document classification.
* Within this context, each observation is a document and each feature represents a term whose value is the frequency of the term.
* Feature values must be non-negative.
* Additive Smoothing can be used by setting the lambda.
* For document classification, the input feature vectors are usually sparse, and sparse vectors should be supplied as input to take advantage of sparsity.
*
* Note that MLlib's Naive Bayes model has the class members pi and theta.
* Pi is a vector of log prior class probabilities, which show your prior beliefs regarding the probability that an arbitrary document belongs in a category.
* Pi is a log of clas priors, whose dimension is C, number of labels
*
* Theta is a C x D matrix, where C is the number of classes and D, the number of features, giving the log probabilities that parametrize the Multinomial likelihood model assumed for each class.
* Theta is a log of class conditional probabilities, whose dimension is C-by-D where D is number of features
*
* The multinomial model is easiest to think about as a problem of randomly throwing balls into bins, where the ball lands in each bin with a certain probability.
* The model treats each n-gram as a bin, and the corresponding tf-idf value as the number of balls thrown into it.
* The likelihood is the probability of observing a (vectorized) document given that it comes from a particular class.
*
* Now, letting x be vectorized text document, then it can be shown that the vector is a vector with C components that represent the posterior class membership probabilities for the document given X.
* That is, the update belief regarding what category this document belongs to after observing its vectorized form.
* This is the motivation behind defining the class NBModel which uses Spark MLlib's NaiveBayesModel, but implements a separate prediction method.
* */

// lambda - additive smoothing factor
case class NBAlgorithmParams(lambda: Double) extends Params

class NBModel(tfIdf: TFIDFModel, categoryMap: Map[Double, String], nb: NaiveBayesModel) extends Serializable {
  private val scoreArray = nb.pi zip nb.theta

  // Given a document string, return a vector of corresponding class membership probabilities.
  private def getScoreVector(doc: String): Array[Double] = {
    def innerProduct(x: Array[Double], y: Array[Double]): Double =
      (x zip y).map(e => e._1 * e._2).sum

    def normalize(u: Array[Double]) = {
      val uSum = u.sum
      u.map(e => e / uSum)
    }

    // Vectorize query
    val x = tfIdf.transform(doc)

    // TODO: wrap pi and theta in vector and matrix and do implicit class dot
    val z = scoreArray.map { case (pi, theta) => innerProduct(theta, x.toArray) + pi }

    normalize(z.indices.map(k => exp(z(k) - z.max)).toArray)
  }

  // TODO: I think this can be replaced with Spark 1.5's predictProbabilities?
  /**
    * We don't use NaiveBayesModel.predict() because it doesn't give the probability conditional upon the input data (i.e confidence)
    *
    * Once you have a vector of class probabilities, you can classify the text document to the category with highest posterior probability,
    * and finally, return both the category as well as the probability to that category (i.e the confidence in the prediction) given the observed data.
    */
  def predict(doc: String): PredictedResult = {
    val scores = getScoreVector(doc)
    val (label, score) = (nb.labels zip scores).maxBy(_._2)
    PredictedResult(categoryMap.getOrElse(label, ""), score)
  }
}

class NBAlgorithm(params: NBAlgorithmParams) extends P2LAlgorithm[PreparedDataNBModel, Query, PredictedResult] {
  def train(sc: SparkContext, data: PreparedData): NBModel =
    new NBModel(data.tfIdfModel, data.categoryMap, NaiveBayes.train(data.labeledPoints, params.lambda))

  /*
  * Transform a query to an appropriate feature vector and uses this to predict with the fitted model.
  * The vectorization function is implemented by a PreparedData object, and the cateogrization (prediction) is handled by an instance of the NBModel implementation.
  * */
  def predict(model: NBModel, query: Query): PredictedResult =
    model.predict(query.text)
}
