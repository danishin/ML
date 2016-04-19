package dase.algorithm

import dase.data.PreparedData
import io.prediction.data.storage.BiMap
import org.apache.spark.mllib.recommendation.Rating
import org.apache.spark.rdd.RDD

class UserViewItemAlgorithm(protected val params: ALSAlgorithmParams) extends ALSAlgorithm {
  def calculateRatings(data: PreparedData, userStringIntMap: BiMap[String, Int], itemStringIntMap: BiMap[String, Int]): RDD[Rating] =
    data.viewEvents
      .flatMap(viewEvent => for {
        uindex <- userStringIntMap.get(viewEvent.user)
        iindex <- itemStringIntMap.get(viewEvent.item)
      } yield ((uindex, iindex), 1))
      .reduceByKey(_ + _) // aggregate all view events of same user-item pair
      .map { case ((u, i), v) => Rating(u, i, v) } // MLlibRating requires integer index for user and item
      .cache()
}
