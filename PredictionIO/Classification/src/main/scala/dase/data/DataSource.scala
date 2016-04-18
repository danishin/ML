package dase.data

import engine.{ActualResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{EmptyEvaluationInfo, PDataSource, Params}
import io.prediction.data.storage.DataMapException
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.rdd.RDD

/**
  * K-fold cross-validation
  *
  * In k-fold cross-validation, the original sample is randomly partitioned into k equal sized subsamples.
  * Of the k subsamples, a single subsample is retained as the validation data for testing the model, and the remaining k-1 samples are used as training data.
  * The cross-validation process is then repeated k times (the folds), with each of the k subsamples used exactly once as the validation data.
  * The k results from the folds can then be averaged (or otherwise combined) to produce a single estimation.
  * The advantage of this method over repeated random sub-sampling is that all observations are used for both training and validation and each observation is used for validation exactly once.
  * 10-fold cross-validation is commonly used, but in general x remains an unfixed parameter.
  */

/**
  * 2-fold cross-validation
  *
  * This is the simplest variation of k-fold cross-validation.
  * Also called holdout method.
  * For each fold, we randomly assign data points to two sets so that both sets are equal size (this is usually implemented by shuffling the data array and then splitting in two)
  * We then train on d0 and test on d1, followed by training on d1 and testing on d0.
  *
  * This has the advantage that our training and test sets are both large, and each data point is used for both training and validations on each fold.
  */

// define the k-fold parameter.
case class DataSourceParams(appName: String, evalK: Option[Int]) extends Params

case class TrainingData(labeledPoints: RDD[LabeledPoint])

class DataSource(val params: DataSourceParams) extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, ActualResult] {
  @transient private lazy val logger = Logger[this.type]

  private def getLabeledPoints(sc: SparkContext): RDD[LabeledPoint] =
    PEventStore
      .aggregateProperties(
        appName = params.appName,
        entityType = "user",
        required = Some(List("plan", "attr0", "attr1", "attr2"))
      )(sc)
      .map { case (entityId, properties) =>
        try {
          LabeledPoint(properties.get[Double]("plan"),
            Vectors.dense(Array(
              properties.get[Double]("attr0"),
              properties.get[Double]("attr1"),
              properties.get[Double]("attr2")
            ))
          )
        } catch {
          case e: DataMapException =>
            logger.error(s"Failed to get properties $properties of $entityId. Exception: $e.")
            throw e
        }
      }
      .cache()

  def readTraining(sc: SparkContext): TrainingData = new TrainingData(getLabeledPoints(sc))

  /**
    * Evaluation Data Generation
    *
    * In evaluation data generation, the goal is to generate a sequence of (training, validation) data tuple.
    * A common way to use is a k-fold generation process.
    * The data set is split into k folds.
    * We generate k tuples of training and validation sets, for each tuple, the training set takes k-1 of the folds and the validation takes the remaining fold.
    *
    * To enable evaluation data generation, we need to define the actual result and implement the method for generating the (training, validation) data tuple.
    *
    */
  override def readEval(sc: SparkContext): Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] =
    MLUtils.kFold(getLabeledPoints(sc), params.evalK.get, 0)
      .map { case (training, testing) =>
        (
          new TrainingData(training),
          new EmptyEvaluationInfo(),
          testing.map(p => (new Query(p.features(0), p.features(1), p.features(2)), new ActualResult(p.label)))
        )
      }
}
