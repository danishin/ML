package dase.serving

import engine.{PredictedResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.LServing

class Serving extends LServing[Query, PredictedResult] {
  @transient lazy val logger = Logger[this.type]

  override def serve(query: Query, predictedResults: Seq[PredictedResult]): PredictedResult =
    predictedResults.head
}
