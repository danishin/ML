package engine

import dase.algorithm.ALSAlgorithm
import dase.data.{DataPreparator, DataSource}
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory}

object RecommendationEngine extends EngineFactory {
  def apply() = new Engine(
    Map("" -> classOf[DataSource]),
    Map("" -> classOf[DataPreparator]),
    Map("als" -> classOf[ALSAlgorithm]),
    Map("" -> classOf[Serving])
  )
}
