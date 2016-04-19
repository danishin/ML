package dase.data

import engine.Query
import grizzled.slf4j.Logger
import io.prediction.controller._
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime

case class DataSourceParams(appName: String) extends Params

case class User()
case class Item(categories: Option[List[String]])
case class ViewEvent(user: String, item: String, time: DateTime)
case class LikeEvent(user: String, item: String, time: DateTime, like: Boolean)

case class TrainingData(users: RDD[(String, User)], items: RDD[(String, Item)], viewEvents: RDD[ViewEvent], likeEvents: RDD[LikeEvent]) extends SanityCheck {
  override def toString =
    s"users: [${users.count()} (${users.take(2).toList}...)] items: [${items.count()} (${items.take(2).toList}...)] viewEvents: [${viewEvents.count()}] (${viewEvents.take(2).toList}...) likeEvents: [${likeEvents.count()} (${likeEvents.take(2).toList})]"

  def sanityCheck(): Unit = {
    require(!users.isEmpty() && !items.isEmpty() && !viewEvents.isEmpty(), "users & items & viewEvents cannot be empty")
  }
}

class DataSource(params: DataSourceParams) extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, EmptyActualResult] {
  @transient private lazy val logger = Logger[this.type]

  def readTraining(sc: SparkContext): TrainingData = {
    // create a RDD of (entityID, User)
    val usersRDD =
      PEventStore
        .aggregateProperties(
          appName = params.appName,
          entityType = "user"
        )(sc)
        .map { case (entityId, _) => (entityId, User())}
        .cache()

    // create a RDD of (entityID, Item)
    val itemsRDD =
      PEventStore
        .aggregateProperties(
          appName = params.appName,
          entityType = "item"
        )(sc)
        .map { case (entityId, properties) => (entityId, Item(properties.getOpt[List[String]]("categories"))) }
        .cache()

    // get all "user" "view" "item" events
    val viewEventsRDD =
      PEventStore
        .find(
          appName = params.appName,
          entityType = Some("user"),
          eventNames = Some(List("view")),
          targetEntityType = Some(Some("item"))
        )(sc)
        .map(event => event.event match {
          case "view" => ViewEvent(
            user = event.entityId,
            item = event.targetEntityId.get,
            time = event.eventTime
          )
          case _ => throw new RuntimeException(s"Unexpected event $event is read.")
        })
        .cache()

    // get all "user" "like" and "dislike" "item" events
    val likeEventsRDD =
      PEventStore
      .find(
        appName = params.appName,
        entityId = Some("user"),
        eventNames = Some(List("like", "dislike")),
        targetEntityType = Some(Some("item"))
      )(sc)
      .map(event => event.event match {
        case "like" => LikeEvent(event.entityId, event.targetEntityId.get, event.eventTime, true)
        case "dislike" => LikeEvent(event.entityId, event.targetEntityId.get, event.eventTime, false)
        case _ => throw new RuntimeException(s"Unexpected event $event is read.")
      })

    TrainingData(usersRDD, itemsRDD, viewEventsRDD, likeEventsRDD)
  }
}

