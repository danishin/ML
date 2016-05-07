package dase.serving

import engine.{PredictedResult, Query}
import io.prediction.controller.LServing

class Serving extends LServing[Query, PredictedResult] {
  def serve(query: Query, predictions: Seq[PredictedResult]): PredictedResult =
    predictions.head
}
