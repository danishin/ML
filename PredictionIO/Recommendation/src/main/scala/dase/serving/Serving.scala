package dase.serving

import dase.{PredictedResult, Query}
import io.prediction.controller.LServing

class Serving extends LServing[Query, PredictedResult] {

  /**
    * Process predicted result.
    * It is responsible for combining multiple predicted results into one if you have more than one predictive model.
    * `Serving` then returns the final predicted result.
    *
    * An engine can train multiple models if you specify more than one `Algorithm` component in `RecommendationEngine`
    *
    * PredictionIO will convert it to a JSON response automatically
    */
  def serve(query: Query, predictedResults: Seq[PredictedResult]): PredictedResult =
    predictedResults.head
}
