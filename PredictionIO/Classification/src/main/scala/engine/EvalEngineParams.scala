package engine

import dase.algorithm.AlgorithmParams
import dase.data.DataSourceParams
import io.prediction.controller.{EngineParams, EngineParamsGenerator}

object EvalEngineParams extends EngineParamsGenerator {
  private[this] val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(
      appName = "Classification",
      evalK = Some(5) // 5-fold cross-validation
    )
  )

  engineParamsList = Seq(0.01, 0.1, 1.0, 10.0, 100.0, 1000.0)
    .map(lambda => baseEP.copy(algorithmParamsList = Seq(("naive", AlgorithmParams(lambda)))))
}