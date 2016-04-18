package engine

import dase.algorithm.NaiveBayesAlgorithm
import dase.data.{DataSource, DataPreparator}
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory}

case class Query(attr0: Double, attr1: Double, attr2: Double)
case class PredictedResult(label: Double)
case class ActualResult(label: Double)

object ClassificationEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map("" -> classOf[DataSource]),
      Map("" -> classOf[DataPreparator]),
      Map("naive" -> classOf[NaiveBayesAlgorithm]),
      Map("" -> classOf[Serving])
    )
}
