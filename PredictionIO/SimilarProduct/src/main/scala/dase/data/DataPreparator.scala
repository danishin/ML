package dase.data

import io.prediction.controller.{PPreparator, SanityCheck}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

case class PreparedData(users: RDD[(String, User)], items: RDD[(String, Item)], viewEvents: RDD[ViewEvent]) extends SanityCheck {
  def sanityCheck(): Unit = {
    require(!users.isEmpty() && !items.isEmpty() && !viewEvents.isEmpty(), "users & items & viewEvents cannot be empty")
  }
}

class DataPreparator extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData =
    PreparedData(trainingData.users, trainingData.items, trainingData.viewEvents)
}

