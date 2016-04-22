package engine

import dase.algorithm.{LRAlgorithm, LRAlgorithmParams, NBAlgorithm, NBAlgorithmParams}
import dase.data.{DataPreparator, DataPreparatorParams, DataSource, DataSourceParams}
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory, EngineParams, EngineParamsGenerator}

case class Query(text: String) extends Serializable
case class PredictedResult(category: String, confidence: Double)

// true class value
case class ActualResult(category: String)

object TextClassificationEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map("" -> classOf[DataSource]),
      Map("" -> classOf[DataPreparator]),
      Map(
        "nb" -> classOf[NBAlgorithm],
        "lr" -> classOf[LRAlgorithm]
      ),
      Map("" -> classOf[Serving])
    )
}

object EngineParamsList extends EngineParamsGenerator {
  // Set data source and preparator parameters.
  private[this] val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(appName = "TextClassification", evalK = Some(3)),
    preparatorParams = DataPreparatorParams(nGram = 2, numFeatures = 500)
  )

  // Set the algorithm params for which we will assess an accuracy score.
  engineParamsList = Seq(
    baseEP.copy(algorithmParamsList = Seq(("nb", NBAlgorithmParams(0.25)))),
    baseEP.copy(algorithmParamsList = Seq(("nb", NBAlgorithmParams(1.0)))),
    baseEP.copy(algorithmParamsList = Seq(("lr", LRAlgorithmParams(0.5)))),
    baseEP.copy(algorithmParamsList = Seq(("lr", LRAlgorithmParams(1.25))))
  )
}