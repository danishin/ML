package dase.algorithm

import dase.data.PreparedData
import engine.{PredictedResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{P2LAlgorithm, Params}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.mllib.tree.configuration.{Algo, Strategy}
import org.apache.spark.mllib.tree.model.RandomForestModel

/*
* Decision Tree Parameters
*
* 1. Problem specification parameters
* These parameters describe the problem you want to solve and your dataset.
* They should be specified and do not require tuning.
*
* - algo: Classification or Regression
*
* - numClasses: Number of classes (for Classification only)
*
* - categoricalFeaturesInfo: Specifies which features are categorical and how many categorical values each of those features can take.
* This is given as a map from feature indices to feature arity (number of categories)
* Any features not in this map are treated as continuous
*
* E.g Map(0 -> 2, 4 -> 10) specifies that feature 0 is binary (taking values 0 or 1) and that feature 4 has 10 categories (values {0,1,..9}).
* Note that feature indices are 0-based: features 0 and 4 are the 1st and 5th elements of an instance's feature vector
*
* Note that you do not have to specify categoricalFeaturesInfo.
* The algorithm will still run and amy get reasonable results.
* However, performance should be better if categorical features are properly designated.
*
* 2. Stopping Criteria
* These parameters determine when the tree stops building (adding new nodes).
* When tuning these parameters, be careful to validate on held-out test data to avoid overfitting.
*
* - maxDepth: Maximum depth of a tree. Deeper trees are more expressive (potentially allowing higher accuracy),
* but they are also more costly to train and are more likely to overfit.
*
* - minInstancesPerNode: For a node to be split further, each of its children must receive at least this number of training instances.
* This is commonly used with RandomForest since those are often trained deeper than individual trees.
*
* - minInfoGain: For a node to be split further, the split must improve at least this much (in terms of information gain)
*
* 3. Tunable parameters
* These parameters may be tuned. Be careful to validate on held-out test data when tuning in order to avoid overfitting
*
* - maxBins: Number of bins used when discretizing continuous features
* Increasing maxBins allows the algorithm to consider more split candidates and make fine-grained split decisions.
* However it also increases computation and communication
* Note that the maxBins parameter must be at least the maximum number of categories M for any categorical feature
*
* - subsamplingRate: Fraction of the training data used for learning the decision tree.
* This parameter is most relevant for training ensembles of trees (using RandomForest and GradientBoostedTrees), where it can be useful to subsample the original data.
* For training a single decision tree, this parameter is less useful since the number of training instances is generally not the main constraint
*
* - impurity: Impurity measure used to choose between candidate splits. This measure must match the algo parameter
*
* Computation scales approximately linearly in the number of training instances, in the number of features, and in the maxBins parameter.
* Communication scales approximately linearly in the number of features and in maxBins
* */

/*
* Random Forest
*
* Random forests are ensembles of decision trees and are one of the most successful machine learning for classification and regression.
* They combine many decision trees in order to reduce the risk of overfitting.
* Like decision trees, random forests handle categorical features, extend to the multiclass classification setting, do not require feature scaling, and are able to capture non-linearities and feature interaction.
*
* Random forests train a set of decision trees separately, so the training can be done in parallel.
* The algorithm injects randomness into the training process so that each decision tree is a bit different.
* Combining the predictions from each tree reduces the variance of the predictions, improving the performance on test data.
*
* The randomness injected into the training process includes:
* - Subsampling the orignal dataset on each iteration to get a different training set (a.k.a bootstrapping)
* - Considering different random subsets of features to split on each tree node.
* Apart from these randomizations, decision tree training is done ins the same way as for individual decision trees.
*
* To make a predition on a new instance, a random forest must aggregate the predictions from its set of decision trees.
* - Classification: Majority vote.
* - Regression: Averaging.
*
* Parameters in Random Forest
* The first two parameters are the most important, and tuning them can often improve performance
*
* - numTrees: Number of trees in the forest
* Increasing the number of trees will decrease the variance in predictions, imporving the model's test-time accuracy.
* Tracing time increases roughly linearly in the number of trees
*
* - maxDepth: Maximum depth of each tree in the forest.
* Increasing the depth makes the model more expressive and powerful.
* However, deep trees take longer to train and are also more prone to overfitting.
* In general, it is acceptable to train deeper trees when using random forests than when using a single decision tree.
* One tree is more likely to overfit than a random forest (because the variance reduction from averaging multiple trees in the forest)
* */

case class RFAlgorithmParams(numTrees: Int, featureSubsetStrategy: String, impurity: String, maxDepth: Int, maxBins: Int, seed: Option[Int]) extends Params

class RFModel(val forest: RandomForestModel, val featureIndex: Map[String, Int], val featureCategoricalIntMap: Map[String, Map[String, Int]]) extends Serializable {
  override def toString = {
    s" forest: [$forest]" +
      s" featureIndex: $featureIndex" +
      s" featureCategoricalIntMap: $featureCategoricalIntMap"
  }
}

// extends P2LAlgorithm because the MLlib's RandomForestModel doesn't
// contain RDD.
class RFAlgorithm(params: RFAlgorithmParams) extends P2LAlgorithm[PreparedData, RFModel, Query, PredictedResult] {
  @transient private lazy val logger = Logger[this.type]

  /**
    * Train a RandomForestModel to predict the probability that the user may convert
    */
  def train(sc: SparkContext, data: PreparedData): RFModel = {
    val categoricalFeaturesInfo = data.featureCategoricalIntMap.map { case (f, m) => (data.featureIndex(f), m.size) }

    logger.info(s"categoricalFeaturesInfo: $categoricalFeaturesInfo")

    val forestModel = RandomForest.trainRegressor(
      input = data.labeledPoints,
      categoricalFeaturesInfo = categoricalFeaturesInfo,
      numTrees = params.numTrees,
      featureSubsetStrategy = params.featureSubsetStrategy,
      impurity = params.impurity,
      maxDepth = params.maxDepth,
      maxBins = params.maxBins,
      seed = params.seed.getOrElse(scala.util.Random.nextInt()))

    new RFModel(
      forest = forestModel,
      featureIndex = data.featureIndex,
      featureCategoricalIntMap = data.featureCategoricalIntMap
    )
  }

  /**
    * predict() function does the following:
    * 1. convert the Query to the required feature vector input
    * 2. use the RandomForestModel to predict the probability of conversion given this feature
    */
  def predict(model: RFModel, query: Query): PredictedResult = {
    def lookupCategoricalInt(feature: String, value: String, default: String = ""): Int =
      model.featureCategoricalIntMap(feature)
        .getOrElse(value, {
          logger.info(s"Unknown $feature $value. Default feature value will be used.")
          // use default feature value
          model.featureCategoricalIntMap(feature)(default)
        })

    // look up categorical feature Int for landingPageId
    val landingFeature = lookupCategoricalInt("landingPage", query.landingPageId).toDouble

    // look up categorical feature Int for referrerId
    val referrerFeature = lookupCategoricalInt("referrer", query.referrerId).toDouble

    // look up categorical feature Int for brwoser
    val browserFeature = lookupCategoricalInt("browser", query.browser).toDouble

    val featureIndex = model.featureIndex
    // create feature Array
    val feature = new Array[Double](model.featureIndex.size)
    feature(featureIndex("landingPage")) = landingFeature
    feature(featureIndex("referrer")) = referrerFeature
    feature(featureIndex("browser")) = browserFeature

    val score = model.forest.predict(Vectors.dense(feature))
    PredictedResult(score)
  }
}
