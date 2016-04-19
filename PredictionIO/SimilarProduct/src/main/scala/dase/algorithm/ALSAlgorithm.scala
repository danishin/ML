package dase.algorithm

import dase.data.{Item, PreparedData}
import engine.{ItemScore, PredictedResult, Query}
import grizzled.slf4j.Logger
import io.prediction.controller.{P2LAlgorithm, Params}
import io.prediction.data.storage.BiMap
import org.apache.spark.SparkContext
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.rdd.RDD

case class ALSAlgorithmParams(rank: Int, numIterations: Int, lambda: Double, seed: Option[Long]) extends Params

class ALSModel(val productFeatures: Map[Int, Array[Double]], val itemStringIntMap: BiMap[String, Int], val items: Map[Int, Item]) extends Serializable {
  @transient lazy val itemIntStringMap = itemStringIntMap.inverse

  override def toString =
    s" productFeatures: [${productFeatures.size}]" +
      s"(${productFeatures.take(2).toList}...)" +
      s" itemStringIntMap: [${itemStringIntMap.size}]" +
      s"(${itemStringIntMap.take(2).toString}...)]" +
      s" items: [${items.size}]" +
      s"(${items.take(2).toString}...)]"
}

/**
  * Use ALS to build item x feature matrix
  *
  * This algorithm uses user-to-item view events as training data.
  * However, your application may have more than one type of events which you want to take into account, such as buy, rate and like events.
  * One way to incorporate other types of events to improve the system is to add another algorithm to process these events, build a separated model and then combine the outputs of multiple algorithms during Serving.
  */
trait ALSAlgorithm extends P2LAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {
  protected def params: ALSAlgorithmParams

  def calculateRatings(data: PreparedData, userStringIntMap: BiMap[String, Int], itemStringIntMap: BiMap[String, Int]): RDD[Rating]

  @transient lazy val logger = Logger[this.type]

  /*
  * ViewEvent object is an implicit event that does not have an explicit rating value.
  * ALS.trainImplicit() supports implicit preference.
  * If the MLlibRating has higher rating value, it means higher confidence that the user prefers the item.
  * Hence we can aggregate how many times the user has viewed the item to indicate the confidence level that the user may prefer the item.
  *
  * ALS.trainImplicit() then returns a MatrixFactorizationModel which contains two RDDs: userFeatures and productFeatures.
  * They correspond to the user X latent features matrix and item X latent features matrix, respectively.
  * In this case, we will make use of the productFeatures matrix to find similar products by comparing the similarity of the latent features.
  * */
  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    // create User and item's String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val itemStringIntMap = BiMap.stringInt(data.items.keys)

    // collect Item as Map and convert ID to Int index
    val items = data.items
      .map { case (id, item) => (itemStringIntMap(id), item) }
      .collectAsMap
      .toMap

    val mllibRatings = calculateRatings(data, userStringIntMap, itemStringIntMap)

    // MLLib ALS cannot handle empty training data.
    require(!mllibRatings.isEmpty(), "mllibRatings cannot be empty. Please check if your events contain valid user and item ID.")

    val m = ALS.trainImplicit(
      ratings = mllibRatings,
      rank = params.rank,
      iterations = params.numIterations,
      lambda = params.lambda,
      blocks = -1,
      alpha = 1.0,
      seed = params.seed.getOrElse(System.nanoTime)
    )

    new ALSModel(
      productFeatures = m.productFeatures.collectAsMap.toMap,
      itemStringIntMap = itemStringIntMap,
      items = items
    )
  }

  /**
    * We can use the productFeatures stored in ALSModel to calculate the similarity between the items in query and other items.
    * Cosine Similarity is used in this case.
    *
    * predict() function first calculates the similarities score of the queries items in query versus all other items and then filtering items satisfying the isCandidate() condition.
    * Then we take the top N items.
    */
  def predict(model: ALSModel, query: Query): PredictedResult = {
    // convert items to Int index
    val queryList = query.items.flatMap(model.itemStringIntMap.get).toSet

    val queryFeatures = queryList.toVector.flatMap(model.productFeatures.get)

    val indexScores =
      model.productFeatures
        .par // convert to parallel collection
        .mapValues(f => queryFeatures.map(qf =>  cosineSimilarity(qf, f)).sum)
        .filter(_._2 > 0) // keep items with score > 0
        .seq // convert back to sequential collection
        .toArray

    val isCandidateItem: Int => Boolean = {
      val whiteList = query.whiteList.map(_.flatMap(model.itemStringIntMap.get))
      val blackList = query.blackList.map(_.flatMap(model.itemStringIntMap.get))

      itemInt =>
        whiteList.forall(_.contains(itemInt)) &&
        blackList.forall(!_.contains(itemInt)) &&
        (!queryList.contains(itemInt)) && // discard items in query as well
        query.categories.forall(category => model.items(itemInt).categories.exists(_.toSet.intersect(category).nonEmpty))
    }

    val filteredScore =
      indexScores
        .view
        .filter { case (itemInt, _) => isCandidateItem(itemInt) }

    val topScores = filteredScore
      .sortBy(_._2)(Ordering[Double].reverse)
      .take(query.num)
      .toArray

    val itemScores = topScores.map { case (itemInt, score) => ItemScore(model.itemIntStringMap(itemInt), score) }

    PredictedResult(itemScores)
  }

  /**
    * Cosine Similarity is a measure of similarity between two vectors of an inner product space that measures the cosine of the angle between them.
    * The cosine of 0 is 1 and it is less than 1 for any other angle.
    * It is thus a judgement of orientation and not magnitude: two vectors with the same orientation have a cosine similarity of 1, two vectors at 90 degree have a similiarity of 0, and two vectors diametrically opposed have a similarity of -1, independent of their magnitude.
    * Cosine similarity is particularly used in positive space, where the outcome is neatly bounded in [0, 1]
    *
    * Note that these bounds apply for any number of dimensions, and cosine similarity is most commonly used in high dimensional positive spaces.
    * For example, in information retriveal and text mining, each term is notionally assigned a different dimension and a document is characterized by a vector where the value of each dimension corresponds to the number of times that term appears in the document.
    * Cosine similiarity then gives a useful measure of how similar two documents are likely to be in terms of their subject matter.
    *
    * cosine similarity os 0 means no similarity whatsoever and 1 meaning that both sequences are exactly the same.
    */
  private def cosineSimilarity(vectorA: Array[Double], vectorB: Array[Double]): Double = {
    require(vectorA.length == vectorB.length, "Two vector must be of the same length to ")
    var dotProduct = 0.0
    var normA = 0.0
    var normB = 0.0

    vectorA.indices.foreach { i =>
      dotProduct += vectorA(i) * vectorB(i)
      normA += math.pow(vectorA(i), 2)
      normB += math.pow(vectorB(i), 2)
    }
    val n1n2 = math.sqrt(normA) * math.sqrt(normB)
    if (n1n2 == 0) 0 else dotProduct / n1n2
  }
}
