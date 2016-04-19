package dase.algorithm

import dase.data.PreparedData
import io.prediction.data.storage.BiMap
import org.apache.spark.mllib.recommendation.Rating
import org.apache.spark.rdd.RDD

/*
* In the original ALSAlgorithm, the train() function calculates the number of items that the user has viewed the same item and then map it to MLlibRating object.
* However, like/dislike event is boolean and one time preference, so it doesn't makes sense to aggregate the events if the user has multiple like/dislike events on the same item.
* However, a user may change her mind so we use latest like or dislike.
*
* MLlib ALS can handle negative preference with ALS.trainImplicit().
* Hence we can map a dislike to rating of -1 and like to 1.
*
* Note that Negative preference does not work if use ALS.train() instead, which is for explicit rating such as "rate" event
* */
class UserLikeItemAlgorithm(protected val params: ALSAlgorithmParams) extends ALSAlgorithm {
  def calculateRatings(data: PreparedData, userStringIntMap: BiMap[String, Int], itemStringIntMap: BiMap[String, Int]): RDD[Rating] =
    data.likeEvents
      .flatMap(likeEvent => for {
        uindex <- userStringIntMap.get(likeEvent.user)
        iindex <- itemStringIntMap.get(likeEvent.item)
      } yield ((uindex, iindex), (likeEvent.like, likeEvent.time.getMillis)))
      .reduceByKey { case (a @ (_, timeA), b @ (_, timeB)) => if (timeA > timeB) a else b } // reduce to the latest like
      .map { case ((u, i), (like, _)) => Rating(u, i, if (like) 1 else -1) }
      .cache()
}
