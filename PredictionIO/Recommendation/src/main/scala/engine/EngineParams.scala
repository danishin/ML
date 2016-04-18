package engine

import dase.algorithm.ALSAlgorithmParams
import dase.data.{DataPreparatorParams, DataSourceParams}
import io.prediction.controller.{EngineParams, EngineParamsGenerator}

sealed trait BaseEngineParamsList extends EngineParamsGenerator {
  protected val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(
      appName = "Recommendation",
      evalParams = Some(DataSourceParams.EvalParams(kFold = 5, queryNum = 10))
    ),
    preparatorParams = DataPreparatorParams(
      excludeItemsFilePath = None
    )
  )
}

object EvalEngineParamsList extends BaseEngineParamsList {
  engineParamsList = for {
    rank <- Seq(5, 10, 20)
    numIterations <- Seq(1, 5, 10)
  } yield baseEP.copy(algorithmParamsList = Seq(("als", ALSAlgorithmParams(rank, numIterations, 0.01, Some(3)))))
}
