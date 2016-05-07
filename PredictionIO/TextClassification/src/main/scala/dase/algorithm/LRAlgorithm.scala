package dase.algorithm

import dase.data.{PreparedData, TFIDFModel}
import engine.{PredictedResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{P2LAlgorithm, Params}
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.sql.{SQLContext, functions}

/*
* Generative vs Discriminative Algorithm
*
* Both are used in supervised learning where you want to learn a rule that maps input x to output y, given a number of training examples of the form {(x_i, y_i)}
*
* Generative Algorithm models how the data was generated in order to categorize a signal.
* It asks the question: based on my generation assumptions, which category is most likely to generate this signal?
* A generative algorithm models how the data was generated, so you aks it "what's the likelihood this or that class generated this instance?" and pick the one with the better probability
* Models the joint probability distribution p(x, y) and then uses the Bayes rule to compute p(y|x)
*
* Discriminative Algorithm does not care about how the data was generated.
* It simply categorizes a given signal.
* A discriminative algorithm uses the data to create a decision boundary, so you ask it "what side of the decision boundary is this instance on?"
* So it doesn't create a model of how the data was generated, it makes a model of what it thinks the boundary between classes look like.
* Directly models p(y|x)
*
* SVM and decision trees are discriminative because they learn explicit boundaries between classes.
* SVM is a maximal margin classifier, meaning that it learns a decision boundary that maximizes the distance between samples of the two classes, given a kernel.
* The distance between a sample and the learned decision boundary can be used to make the SVM a "soft" classifer.
* Decision Tree learn the decision boundary by recursively partitioning the space in a manner that maximizes the information gain.
*
* Some people argue that the discriminative model is better in the sense that it directly models the quantity you care about (y), so you don't have to spend your modeling efforts on the input x (you need to compute p(x|y) as well in a generative model)
* However, the generative model has its own advantage such as the capability of dealing with missing data
*
* Fundamental difference between discriminative and generative models is
* - Discriminative models learn the (hard or soft) boundary between classes
* - Generative models model the distribution of individual classes
*
*
* Overall, the discriminative models generally outperform generative models in classification tasks.
* */

/*
* Naive Bayes vs Logistic Regression
*
* For feature x and label y, NaiveBayes estimates a joint probability p(x, y) = p(y) * p(x|y) from the training data (that is, builds a model that could "generate" the data),
* and uses Bayes Rule to predict p(y|x) for new test instances.
*
* Logistic Regression estimates p(y|x) directly from the training data by minimizing an error function (which is more "discriminative")
*
* When there are very few training instances, logistic regression might overfit, because there isn't enough data to estimate p(y|x) reliably.
* NaiveBayes might do better because it models the entire joint distribution.
*
* When the feature set is large (and sparse, like word features in text classification) NaiveBayes might "double count" features that are correlated with each other, because it assumes that each p(x|y) event is independent, when they are not.
* Logistic Regression can do a better job by naturally "splitting the difference" among these corrleated features.
* */

case class LRAlgorithmParams(regParam: Double) extends Params

case class LREstimate(coefficients: Array[Double], intercept: Double)

class LRModel(tfIdf: TFIDFModel, categoryMap: Map[Double, String], lrModels: Seq[(Double, LREstimate)]) extends Serializable {
  def predict(doc: String): PredictedResult = {
    def dotProduct(x: Array[Double], y: Array[Double]): Double =
      (x zip y).map(e => e._1 * e._2).sum

    val x: Array[Double] = tfIdf.transform(doc).toArray

    // Logistic Regression binary formula for positive probability.
    // According to MLLib documentation, class labeled 0 is used as pivot.
    // Thus, we are using:
    // log(p1/p0) = log(p1/(1 - p1)) = b0 + xTb =: z
    // p1 = exp(z) * (1 - p1)
    // p1 * (1 + exp(z)) = exp(z)
    // p1 = exp(z)/(1 + exp(z))
    val (label, score) = lrModels
      .map { case (label, estimate) =>
        val z = math.exp(dotProduct(estimate.coefficients, x) + estimate.intercept)
        (label, z / (1 + z))
      }
      .maxBy(_._2)

    PredictedResult(categoryMap(label), score)
  }
}

class LRAlgorithm(params: LRAlgorithmParams) extends P2LAlgorithm[PreparedData, LRModel, Query, PredictedResult] {
  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): LRModel = {
    // Import SQLContext for creating DataFrame.
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val lr = new LogisticRegression()
      .setMaxIter(10)
      .setThreshold(0.5)
      .setRegParam(params.regParam)

    val labels = data.categoryMap.keys.toSeq

    // Transform to Spark DataFrame
    // Add the different binary columns for each label.
    val dataFrame = labels.foldLeft(data.labeledPoints.toDF) { (dataFrame, label) =>
      // function: multiclass labels --> binary labels
      val f = functions.udf[Double, Double](l => if (l == label) 1.0 else 0.0)

      dataFrame.withColumn(label.toInt.toString, f(dataFrame("label")))
    }

    // Create a logistic regression model for each class.
    val lrModels = labels.map { label =>
      val lab = label.toInt.toString

      val fit = lr.setLabelCol(lab).fit(dataFrame.select(lab, "features"))

      // Return (label, feature coefficients, and intercept term.
      (label, LREstimate(fit.weights.toArray, fit.intercept))
    }

    new LRModel(data.tfIdfModel, data.categoryMap, lrModels)
  }

  def predict(model: LRModel, query: Query): PredictedResult =
    model.predict(query.text)
}
