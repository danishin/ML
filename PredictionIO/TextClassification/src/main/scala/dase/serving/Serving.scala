package dase.serving

import engine.{PredictedResult, Query}
import io.prediction.controller.LServing

class Serving extends LServing[Query, PredictedResult] {
  def serve(query: Query, predictedResults: Seq[PredictedResult]): PredictedResult =
    predictedResults.maxBy(_.confidence)
}
