package smile_example

import java.awt.Rectangle
import java.text.SimpleDateFormat
import java.time.Instant

import smile.plot.BarPlot

import scala.swing.MainFrame
import scala.util.Try


// TODO: Algorithm is not correct as it produce infinity but no way to verify whether it is correct or not from the first place. Let's just leave it at here.
/*
* Ranking emails based on their content (Recommendation System)
*
* We will be ranking emails based on the following features: 'sender', 'subject', 'common terms in subject' and 'common terms in email body'
* Note that these features are for you to be defined when you make your own recommendation system
* When building your own recommendation system, this is one of the hardest parts.
* Coming up with good features is not trivial, and when you finally selected features, the data might not be directly usable for these features
* */
object RecommendationExample extends /*SimpleSwingApplication with*/ util.Helper {
  private lazy val stopWords = files.read("resources/NaiveBayes/stopwords.txt", "latin1")
    .map(_.trim.toLowerCase)
    .toSet

  case class EmailData(sender: String, subject: String, date: Instant, body: String) {
    private def extractTerms(str: String): List[String] =
      str
        .trim
        .replaceAll("[^a-zA-Z ]", "")
        .toLowerCase
        .split(" ")
        .filter(word => word.nonEmpty && !stopWords.contains(word))
        .toList

    lazy val subjectTerms = extractTerms(subject)
    lazy val bodyTerms = extractTerms(body)
  }

  case class Features(weightedSenderFrequency: Map[String, Double], weightedMailThreadSubjectFrequency: Map[String, Double], weightedMailThreadSubjectTermFrequency: Map[String, Double], bodyTDM: Map[String, Double])

  def top = new MainFrame {
    title = "Recommendation System Example"

    val emailData = getEmailData

    val (trainingData, testingData) = emailData.splitAt(emailData.length / 2)

    val (subjects, weightedPoints) = feature.emailThreadsGroupedBySubjectWeightedByTimeFrame(trainingData).toArray.sortBy(_._2).unzip

    // Finding the right scale requires some insight.
    // This is why using the plotting library of Smile to make several plots on different scales can help a lot when performing this rescaling.
    val barPlot = BarPlot.plot("", weightedPoints, subjects)

    // Rotate the email addresses such that we can read them
    barPlot.getAxis(0).setRotation(Math.toRadians(-80))
    barPlot.setAxisLabel(0, "")
    barPlot.setAxisLabel(1, "Amount of mails per subject weighted by timeframe")

    peer.setContentPane(barPlot)

    bounds = new Rectangle(800, 600)
  }

  def main(args: Array[String]): Unit = {
    val emailData = getEmailData

    recommend(emailData)
  }

  private object feature {
    /*
    * Those who you receive more emails from should be ranked higher than ones you get less email from given the fact that spam is left out.
    * */
    def emailsGroupedBySender(emailData: List[EmailData]): Map[String, Double] =
      emailData
        .groupBy(_.sender)
        /*
        * Due to the 'huge' outliers, directly using this data would result in the highest 1 or 2 senders to be rated as very important whereas the rest would not be considered in the recommendation system.
        * In order to prvent this behavior, we will re-scale the data by taking log1p.
        * The log1p function takes the log of the value but beforehand adds 1 to the value.
        * This addition of 1 is to prevent trouble when taking the log value for senders who sent only 1 email
        * This range is much smaller, causing the outliers to not skew away the rest of the data. This data manipulation trick is very common in the field of machine learning
        * */
        .mapValues(emails => Math.log1p(emails.length))

    /*
    * The frequency and timeframe in which subjects occur.
    * If a subject occurs more it is likely to be of higher importance.
    * Additionally we take into account the timespan of the thread.
    * So the frequency of a subject will be normalized with the timespan of the emails of this subject.
    * This makes highly active email threads come up on top.
    * Again this is an assumption we make on which emails should be ranked higher.
    *
    * Highest weights are given to emails which almost instantly got a follow up email response,
    * whereas the lowest weights are given to emails with very long timeframes.
    * This allows for emails with a very low frequency to be rated as very important based on the timeframe in which they were sent.
    * */
    def emailThreadsGroupedBySubjectWeightedByTimeFrame(emailData: List[EmailData]): Map[String, Double] =
      emailData
        .groupBy(_.subject)
        // filter non-thread emails (no reply)
        .collect { case (subject, emails) if emails.length > 1 =>
        val timeDifference = emails.maxBy(_.date).date.getEpochSecond - emails.minBy(_.date).date.getEpochSecond
        /*
        * We compute the weights by taking the frequency of a subject and dividing it by the time difference
        * Since this value is very small, we want to rescale it up a little, which is done by taking the log10.
        * This however causes our values to become negative, which is why we add a basic value of 10 to make every value positive
        *
        * We make sure that outliers do not largely influence the feature.
        * */
        val timeWeightedFrequency = Math.log10(emails.length.toDouble / timeDifference) + 10
        (subject, timeWeightedFrequency)
      }

    /*
    * This next feature will be based on the weight ranking that we just computed.
    * The idea is that new emails with different subjects will arrive.
    * However, chances are that they contain keywords that are similar to earlier received important subjects.
    * This will allow us to rank emails as important before a thread (multiple messages with the same subject) was started.
    * For that we specify the weight of keywords to be the weight of the subject in which the term occured.
    * If this term occurred in multiple threads, we take the highest weight as the leading one.
    *
    * These weights can be used to compute the weight of the subject of a new email even if the email is not a response to an existing thread
    * */
    def wordInEmailThreadSubjectWeights(emailData: List[EmailData], weightedMailThreadSubjectFrequency: Map[String, Double]): Map[String, Double] =
      emailData
        .flatMap(e => e.subjectTerms.map((_, weightedMailThreadSubjectFrequency.get(e.subject))))
        .collect { case (subjectTerm, Some(threadSubjectFrequency)) => (subjectTerm, threadSubjectFrequency) } // filter non-thread mails
        .groupBy(_._1)
        .mapValues(_.maxBy(_._2)._2)

    /*
    * As a fourth feature, we want to incorporate weighting based on the terms that are occuring with a high frequency in all the emails.
    * For this we build up a TDM, but this time the TDM is a bit different from the former examples, as we only log the frequency of the terms in all documents.
    * Furthermore, we take the log10 of the occurrence counts.
    * This allows to scale down the term frequencies such that the results do not get affected by possible outlier values.
    *
    * TDM - Term Document Matrix
    * tdm allows us to compute an importance weight for the email body of new emails based on historical data
    * */
    def tdmFromBody(emailData: List[EmailData]): Map[String, Double] =
      emailData
        .flatMap(_.body.split(" "))
        .filter(s => s.nonEmpty && !stopWords.contains(s))
        .groupBy(identity)
        .mapValues(repeated => Math.log10(repeated.length + 1))

    def getFeatures(emailData: List[EmailData]): Features = {
      val weightedSenderFrequency = emailsGroupedBySender(emailData)
      val weightedMailThreadSubjectFrequency = emailThreadsGroupedBySubjectWeightedByTimeFrame(emailData)
      val weightedMailThreadSubjectTermFrequency = wordInEmailThreadSubjectWeights(emailData, weightedMailThreadSubjectFrequency)
      val bodyTDM = tdmFromBody(emailData)

      Features(weightedSenderFrequency, weightedMailThreadSubjectFrequency, weightedMailThreadSubjectTermFrequency, bodyTDM)
    }
  }

  /*
  * With these preparations for our 4 features, we can make our actual ranking calculation for the trianing data.
  * For this we need to find the
  * - senderWeight (representing the weight of the sender)
  * - threadSubjectWeight (representing the thread weight)
  * - threadSubjectTermWeight (representing the weight of the terms in the subject)
  * - bodyCommonTermsWeight (representing the weight of the body of the email)
  * for each email and multiply them to get the final ranking.
  *
  * Due to the fact that we multiply, and not add the values, we need to take care of values that are < 1.
  * Say for example, someone sent 1 email, then the senderWeight would be 0.69, which would be unfair in comparison to someone who didn't send any email before yet, because then the senderWeight would be 1.
  * This is why we take the Math.max(value, 1) for each feature that we can have values below 1
  * */
  private def recommend(emailData: List[EmailData]) = {
    def normalizedAverageWeight(weights: List[Double]): Double =
      if (weights.nonEmpty) Math.max(weights.sum / weights.length, 1.0)
      else 1.0

    val features = feature.getFeatures(emailData)

    val trainingRanks = emailData
      .map { mail =>
        val senderWeight = features.weightedSenderFrequency(mail.sender)
        val threadSubjectWeight = features.weightedMailThreadSubjectFrequency.getOrElse(mail.subject, 1.0) // Mail might not be a thread
        val threadSubjectTermsWeight = normalizedAverageWeight(mail.subjectTerms.collect(Function.unlift(term => features.weightedMailThreadSubjectTermFrequency.get(term))))
        val bodyTermsWeight = normalizedAverageWeight(mail.bodyTerms.map(term => features.bodyTDM(term)))

        val rank = senderWeight * threadSubjectWeight * threadSubjectTermsWeight * bodyTermsWeight

        (mail, rank)
      }
      .sortBy(_._2)

    val median = trainingRanks(trainingRanks.length / 2)._2
    val mean = trainingRanks.map(_._2).sum / trainingRanks.length

    val priorityEmails = trainingRanks.filter(_._2 >= mean)

    val df = new java.text.DecimalFormat("#.##")
    println("|Date | Sender  | Subject  | Rank|")
    println("| :--- | : -- | :--  | :-- | :-- |  :-- |  :-- |  :-- |  ")
    trainingRanks.take(10).foreach(x => println("| " + x._1.date + " | " + x._1.sender + " | " + x._1.subject + " | " + df.format(x._2)))

    println(s"${priorityEmails.length} ranked as priority out of ${trainingRanks.length} with median: $median and mean $mean")
  }

  private def getEmailData: List[EmailData] = {
    val EmailRegex1 = """From: .*?<([\w@.-]+?)>""".r
    val EmailRegex2 = """From: ([\w@.-]+).+""".r
    val SubjectRegex = """Subject: (?:Re: )?(.+)""".r
    val DateRegex = """Date: (.+)""".r

    object ParseInstant {
      def unapply(string: String): Option[Instant] =
        List(
          "EEE MMM dd HH:mm:ss yyyy",
          "EEE, dd MMM yyyy HH:mm",
          "dd MMM yyyy HH:mm:ss",
          "EEE MMM dd yyyy HH:mm"
        ).collectFirst(Function.unlift(format => Try(new SimpleDateFormat(format).parse(string).toInstant).toOption))
    }

    files.listFiles("resources/NaiveBayes/easy_ham")
      .map(f => files.read(f, "latin1"))
      .map { lines =>
        val sender = lines.find(_.startsWith("From: ")).get match {
          case EmailRegex1(email) => email.trim.toLowerCase
          case EmailRegex2(email) => email.trim.toLowerCase
        }
        val subject = lines.find(_.startsWith("Subject: ")).get match { case SubjectRegex(subject) => subject.trim.toLowerCase }
        val date = lines.find(_.startsWith("Date: ")).get match { case DateRegex(ParseInstant(date)) => date }
        val messageBody = lines.mkString("\n").takeFrom("\n\n").replace("\n", " ").replaceAll("[^a-zA-Z ]", "").toLowerCase

        EmailData(sender, subject, date, messageBody)
      }
      .sortBy(_.date)
  }
}
