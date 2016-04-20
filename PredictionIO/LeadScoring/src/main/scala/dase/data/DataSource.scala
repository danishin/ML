package dase.data

import engine.Query
import grizzled.slf4j.Logger
import io.prediction.controller.{EmptyActualResult, EmptyEvaluationInfo, PDataSource, Params}
import io.prediction.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

case class DataSourceParams(appName: String) extends Params

case class Session(landingPageId: String, referrerId: String, browser: String, buy: Boolean)
case class TrainingData(session: RDD[Session])

class DataSource(params: DataSourceParams) extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, EmptyActualResult] {
  @transient lazy val logger = Logger[this.type]


  /*
  * PEventStore.find() specifies the events that you want to read.
  * In this case, "user view page" and "user buy item" events are read and then each is mapped to tuple of (sessionId, event).
  * The events are then "cogrouped" by sessionId to find out the information in the session, such as first page view (landing page view) and whether the user converts (buy event), to create a RDD of Session as TrainingData
  *
  * You could modify the DataSource to read other events if the defintion of conversion is not "buy item" event
  * */
  def readTraining(sc: SparkContext): TrainingData = {
    val viewPage = PEventStore
      .find(
        appName = params.appName,
        entityType = Some("user"),
        eventNames = Some(Seq("view")),
        targetEntityType = Some(Some("page"))
      )(sc)
      .map(event => (event.properties.get[String]("sessionId"), event))

    val buyItem = PEventStore
      .find(
        appName = params.appName,
        entityType = Some("user"),
        eventNames = Some(Seq("buy")),
        targetEntityType = Some(Some("item"))
      )(sc)
      .map(event => (event.properties.get[String]("sessionId"), event))

    val session = (viewPage cogroup buyItem)
      .map { case (sessionId, (viewPages, buyItems)) =>
        // the first view event of the session is the landing event
        val landing = viewPages.reduce((a, b) => if (a.eventTime isBefore b.eventTime) a else b)

        // any buy after landing
        val buy = buyItems.exists(_.eventTime isAfter landing.eventTime)

        Session(landing.targetEntityId.get, landing.properties.getOrElse("referrerId", ""), landing.properties.getOrElse("browser", ""), buy)
      }
      .cache()

    TrainingData(session)
  }
}


