package engine

import dase.algorithm.{ALSAlgorithm, CooccurrenceAlgorithm}
import dase.data.{DataSource, DataPreparator}
import dase.serving.Serving
import io.prediction.controller.{Engine, EngineFactory}

case class Query(items: List[String], num: Int, categories: Option[Set[String]], whiteList: Option[Set[String]], blackList: Option[Set[String]])
case class PredictedResult(itemScores: Array[ItemScore]) {
  override def toString: String = itemScores.mkString(",")
}

case class ItemScore(item: String, score: Double)

object SimilarProductEngine extends EngineFactory {
  def apply() =
    new Engine(
      Map("" -> classOf[DataSource]),
      Map("" -> classOf[DataPreparator]),
      Map(
        "als" -> classOf[ALSAlgorithm],
        "cooccurrence" -> classOf[CooccurrenceAlgorithm]
      ),
      Map("" -> classOf[Serving])
    )
}
