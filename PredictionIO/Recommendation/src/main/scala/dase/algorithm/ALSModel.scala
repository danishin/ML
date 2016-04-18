package dase.algorithm

import io.prediction.controller.{PersistentModel, PersistentModelLoader}
import io.prediction.data.storage.BiMap
import org.apache.spark.SparkContext
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel
import org.apache.spark.rdd.RDD

class ALSModel(override val rank: Int,
               override val userFeatures: RDD[(Int, Array[Double])],
               override val productFeatures: RDD[(Int, Array[Double])],
               userIDMap: BiMap[String, Int],
               itemIDMap: BiMap[String, Int])
  extends MatrixFactorizationModel(rank, userFeatures, productFeatures) with PersistentModel[ALSAlgorithmParams] {

  def save(id: String, params: ALSAlgorithmParams, sc: SparkContext): Boolean = {
    sc.parallelize(Seq(rank)).saveAsObjectFile(s"/tmp/$id/rank")
    userFeatures.saveAsObjectFile(s"/tmp/$id/userFeatures")
    productFeatures.saveAsObjectFile(s"/tmp/$id/productFeatures")
    sc.parallelize(Seq(userIDMap)).saveAsObjectFile(s"/tmp/$id/userStringIntMap")
    sc.parallelize(Seq(itemIDMap)).saveAsObjectFile(s"/tmp/$id/itemStringIntMap")

    true
  }

  private lazy val idItemMap = itemIDMap.inverse

  def getUserID(user: String): Option[Int] = userIDMap.get(user)
  def getItem(itemID: Int): Option[String] = idItemMap.get(itemID)

  override def toString =
    s"userFeatures: [${userFeatures.count()}]" +
      s"(${userFeatures.take(2).toList}...)" +
      s" productFeatures: [${productFeatures.count()}]" +
      s"(${productFeatures.take(2).toList}...)" +
      s" userStringIntMap: [${userIDMap.size}]" +
      s"(${userIDMap.take(2)}...)" +
      s" itemStringIntMap: [${itemIDMap.size}]" +
      s"(${itemIDMap.take(2)}...)"
}

object ALSModel extends PersistentModelLoader[ALSAlgorithmParams, ALSModel] {
  def apply(id: String, params: ALSAlgorithmParams, sc: Option[SparkContext]) =
    new ALSModel(
      rank = sc.get.objectFile[Int](s"/tmp/$id/rank").first,
      userFeatures = sc.get.objectFile(s"/tmp/$id/userFeatures"),
      productFeatures = sc.get.objectFile(s"/tmp/$id/productFeatures"),
      userIDMap = sc.get.objectFile[BiMap[String, Int]](s"/tmp/$id/userStringIntMap").first,
      itemIDMap = sc.get.objectFile[BiMap[String, Int]](s"/tmp/$id/itemStringIntMap").first
    )
}
