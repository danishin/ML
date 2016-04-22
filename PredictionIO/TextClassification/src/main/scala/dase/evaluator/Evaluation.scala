package dase.evaluator

import dase.algorithm.{LRAlgorithmParams, NBAlgorithmParams}
import dase.data.{DataSourceParams, DataPreparatorParams}
import engine.{ActualResult, PredictedResult, Query, TextClassificationEngine}
import io.prediction.controller._

class Accuracy extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult): Double =
    if (predicted.category == actual.category) 1.0 else 0.0
}

object AccuracyEvaluation extends Evaluation {
  engineMetric = (
    TextClassificationEngine(),
    new Accuracy
  )
}
