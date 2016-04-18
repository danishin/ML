package smile_example

import smile.regression.LASSO
import smile.validation.CrossValidation

import scala.reflect.ClassTag

/*
* Curses of Dimensionality
*
* The curse of dimensionality is a collection of problems that can occur when your data size is lower than the amount of features (dimensions) you are trying to use to create your machine learning model.
* An example of dimensionality curse is a matrix rank deficiency.
* When using Ordinary Least Squares (OLS), the underlying algorithm solves a linear system in order to build up a model.
* However, if you have more columns than you have rows, coming up with a single solution for this system is not possible.
* If this is the case, the best solution would be to get more datapoints or reduce the feature set
* */

/*
* An attempt at rank prediction for top selling books - Text Regression
*
* In the example of predicting weights based on heights and gender, we introduced the notion of linear regression
* However, sometimes one would want to apply regression on non-numeric data such as text
*
* Note that for this particular case it doesn't work out to use text regression as the data simply does not contain a signal for our test data.
* */
object TextRegressionExample extends util.Helper {
  case class Document(title: String, rank: Int, description: String)

  case class DTMRecord(documentTitle: String, documentRank: Int, occurences: Map[String, Int])
  /*
  * When we want to do regression of some form, we need numeric data.
  * This is why we build a Document Term Matrix (DTM).
  * Note that this DTM is similar to the Term Document Matrix (TDM) that we built in the spam classification example.
  * Its difference is that we store document records containing which terms are in that document
  * in contrast to the TDM where we store records of words, containing a list of documents in which this term is available
  * */
  class DTM {
    private var records = Vector[DTMRecord]()
    private var wordList = Vector[String]()

    private lazy val stopWords = files.read("resources/NaiveBayes/stopwords.txt", "latin1")

    def addDocumentToRecords(document: Document) = {
      require(!records.exists(_.documentTitle == document.title))

      val wordRecords = document.description
        .toLowerCase
        .split(" ")
        .groupBy(identity)
        .mapValues(_.length)

      wordList = wordList ++ wordRecords.keys.filter(word => !stopWords.contains(word))
      records = records :+ new DTMRecord(document.title, document.rank, wordRecords)
    }

    /*
    * First Parameter
    * A matrix in which each row represents a document and each column represents one of the words from the complete vocabulary of the DTM's documents.
    * Note that the doubles in the first table represents the number of occurrences of the words.
    *
    * Second Parameter
    * An array containing all the ranks belonging to the records from the first table.
    * */
    def getNumericRepresentationForRecords: (Array[Array[Double]], Array[Double]) =
      records.foldLeft((Array[Array[Double]](), Array[Double]())) { case ((dtmNumeric, ranks), dtmRecord) =>
        val newRanks = ranks :+ dtmRecord.documentRank.toDouble

        val newDtmNumeric = dtmNumeric :+ wordList.foldLeft(Array[Double]()) { case (dtmNumericRecord, word) =>
          dtmRecord.occurences.get(word) match {
            case Some(occurrence) => dtmNumericRecord :+ occurrence.toDouble
            case None => dtmNumericRecord :+ 0.0
          }
        }

        (newDtmNumeric, newRanks)
      }
  }

  def main(args: Array[String]): Unit = {
    val testData = getTestData

    val documentTermMatrix = new DTM()

    testData.foreach(documentTermMatrix.addDocumentToRecords)

    val (dataPoints, classifiers) = documentTermMatrix.getNumericRepresentationForRecords
    val cv = new CrossValidation(testData.length, 2)

    validate(dataPoints, classifiers, cv)
  }


  def getTestData =
    files.readCSV("resources/TextRegression/TextRegression_Example_1.csv")
      .drop(1)
      .map { case List(_, title, _, rank, description) => Document(title, rank.toInt, description) }

  /*
  * With this conversion from text to numeric value, we can open our regression toolbox.
  * We used Ordinary Least Squares (OLS) in the previous example,
  * however this time we will use Least Absolute Shrinkage and Selection Operator (LASSO) regression
  * This is because we can give this regression method a certain lambda, which represents a penalty value.
  * This penalty value allows the LASSO algorithm to select relevant features (words) while discarding some of the other features (words).
  *
  * This feature selection that Lasso performs is very useful in our case due to the large set of words that is used in the documents description
  * Lasso will try to come up with an ideal subset of those words as features, whereas when applying the OLS, all words would be used, and the runtime would be extremely high.
  * Additionally, the OLS implementation of Smile detects rank deficiency.
  * This is one of the curses of dimensionality
  *
  * We need to find an optimal lambda however, thus we should try for several lambda's using cross validation
  * */
  def validate(dataPoints: Array[Array[Double]], classifiers: Array[Double], cv: CrossValidation) = {
    (0 until cv.k).foreach { i =>
      def get[A : ClassTag](arr: Array[A], isTraining: Boolean): Array[A] =
        arr.zipWithIndex.filter{ case (_, index) =>
          val exists = cv.test(i).contains(index)
          if (isTraining) exists
          else !exists
        }.map(_._1)

      val trainDataPoints = get(dataPoints, true)
      val trainClassifiers = get(classifiers, true)
      val testDataPoints = get(dataPoints, false)
      val testClassifiers = get(classifiers, false)

      /*
      * Differences in lambda are in this case not noticeable.
      * However when using this in practice, you should be careful when picking the lambda value:
      * The higher the lambda you pick, the lower the amount of features for the algorithm becomes.
      * This is why cross validation is important to see how the algorithm performs on different lambda's
      *
      * These are the lambda values we will verify against
      * */
      val lambdas = List(0.1, 0.25, 0.5, 1.0, 2.0, 5.0)

      lambdas.foreach { lambda =>
        // Define a new model based on the training data and one of the lambda
        val lassoModel = new LASSO(trainDataPoints, trainClassifiers, lambda)

        // Compute the RMSE for this model with this lambda
        val results = testDataPoints.map(lassoModel.predict) zip testClassifiers
        val RMSE = Math.sqrt(results.map{ case (predicted, expected) => Math.pow(predicted - expected, 2) }.sum / results.length)

        println(s"Lambda: $lambda RMSE: $RMSE")

        /*
        * The data may not contain the answer.
        * The combination of some data and an aching desire for an answer does not ensure that a reasonable answer can be extracted from a given body of data
        * */
      }
    }
  }
}
