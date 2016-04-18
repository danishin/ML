package dase.algorithm

import dase.data.PreparedData
import engine.{PredictedResult, Query}
import io.prediction.controller.{P2LAlgorithm, Params}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{NaiveBayes, NaiveBayesModel}
import org.apache.spark.mllib.linalg.Vectors

// lambda is a smoothing parameter
case class AlgorithmParams(lambda: Double) extends Params

// extends P2LAlgorithm because the MLlib's NaiveBayesModel doesn't contain RDD.
class NaiveBayesAlgorithm(params: AlgorithmParams) extends P2LAlgorithm[PreparedData, NaiveBayesModel, Query, PredictedResult] {
  def train(sc: SparkContext, data: PreparedData): NaiveBayesModel = {
    require(!data.labeledPoints.isEmpty(), "RDD[labeledPoints] in PreparedData cannot be empty. Please check if DataSource generates TrainingData and Preparator generates PreparedData correctly.")

    NaiveBayes.train(data.labeledPoints, params.lambda)
  }

  def predict(model: NaiveBayesModel, query: Query): PredictedResult = {
    val label = model.predict(Vectors.dense(Array(query.attr0, query.attr1, query.attr2)))
    new PredictedResult(label)
  }
}
