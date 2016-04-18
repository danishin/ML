package dase

import dase.data.TrainingData
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

// Don't put classes inside package object.
// https://issues.scala-lang.org/browse/SI-4515
case class Query(user: String, num: Int)

case class PredictedResult(itemScores: Array[PredictedResult.ItemScore])
object PredictedResult {
  case class ItemScore(item: String, score: Double)
}

case class ActualResult(ratings: Array[TrainingData.Rating])

object implicits {
  implicit class RDDOps[A : ClassTag](rdd: RDD[A]) {
    def mapPartition[B : ClassTag](p: A => Boolean)(m: A => B): (RDD[B], RDD[B]) = {
      val pass = rdd.filter(p).map(m)
      val fail = rdd.filter(e => !p(e)).map(m)
      (pass, fail)
    }
  }
}
