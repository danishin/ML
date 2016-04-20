package engine

import dase.algorithm.RFAlgorithm
import dase.data.{DataSource, DataPreparator}
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory}

case class Query(landingPageId: String, referrerId: String, browser: String)
case class PredictedResult(score: Double)

object LeadScoringEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map("" -> classOf[DataSource]),
      Map("" -> classOf[DataPreparator]),
      Map("randomforest" -> classOf[RFAlgorithm]),
      Map("" -> classOf[Serving])
    )
}
