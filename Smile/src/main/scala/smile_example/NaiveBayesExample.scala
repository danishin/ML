package smile_example

import java.io.File

import smile.classification.NaiveBayes
import smile.feature.Bag

/*
* Classifying email as spam or ham - Naive Bayes
*
* Naive Bayes algorithm calculates the probability for an object for each possible class and then returns the class with the highest probability.
* For this probability calculation, the algorithm uses features.
* The reason it's called 'Naive' Bayes is because it does not incorporate any correlation between features.
* In other words, each feature counts the same
*
* For spam classification, Naive Bayes works well, as spam or ham cannot be classified purely based on one feature (word)
*
* In this example, we will use the content of email as feature.
* By this, we mean we will select the features (words in this case) from the bodies of the emails in the training set.
* In order to be able to do this, we need to build a Term Document Matrix (TDM)
* */
object NaiveBayesExample extends util.Helper {
  // Tweak these two variables to achieve better rate. - 400 is max for each
  val NumberOfSamplesPerSet = 400
  val NumberOfFeaturesToTake = 400

  case class TrainingData(spamEmails: List[(File, String)], hamEmails: List[(File, String)], stopWords: List[String])
  case class TestData(spamEmailMessages: List[String], hamEmailMessages: List[String])
  case class PreparedData(data: Array[Array[Double]], classifiers: Array[Int], dimensionality: Int)

  def main(args: Array[String]): Unit = {
    val (trainingData, testingData) = getTrainingData

    val (preparedData, bagOfWords) = getPreparedData(trainingData)

    val bayes = new NaiveBayes(NaiveBayes.Model.MULTINOMIAL, 2, preparedData.dimensionality)

    bayes.learn(preparedData.data, preparedData.classifiers)

    val testSpamFeatureVectors = testingData.spamEmailMessages.map(s => bagOfWords.feature(s.split(" ")))
    val testHamFeatureVectors = testingData.hamEmailMessages.map(s => bagOfWords.feature(s.split(" ")))

    validate(bayes, testSpamFeatureVectors)
    validate(bayes, testHamFeatureVectors)
  }

  private def getTrainingData: (TrainingData, TestData) = {
    def getMessage(f: File): String =
      files.slurp(f, "latin1")
        .takeFrom("\n\n") // Find the first line break in the email as this indicates the message body
        .replace("\n", " ")
        .replaceAll("[^a-zA-Z ]", "") // Filter only text from a-z and space
        .toLowerCase

    val (trainSpamFiles, testSpamFiles) = files.listFiles("resources/NaiveBayes/spam").take(NumberOfSamplesPerSet + 100).splitAt(NumberOfSamplesPerSet)
    val (trainHamFiles, testHamFiles) = files.listFiles("resources/NaiveBayes/easy_ham").take(NumberOfSamplesPerSet + 100).splitAt(NumberOfSamplesPerSet)

    val trainingData = TrainingData(
      trainSpamFiles.map(f => (f, getMessage(f))),
      trainHamFiles.map(f => (f, getMessage(f))),
      files.read("resources/NaiveBayes/stopwords.txt", "latin1")
    )

    val testingData = TestData(
      testSpamFiles.map(getMessage),
      testHamFiles.map(getMessage)
    )

    (trainingData, testingData)
  }

  /*
  * We will build 2 TDM's
  * The TDM will contain ALL words which are contained in the bodies of the training set, including frequency rate.
  * However, since frequency might not be the best measure (as 1 email which contains 100000 times the word 'cake' would mess up the complete table),
  * we will also compute the occurrence rate.
  * By this we mean, the amount of documents that contain that specific term.
  *
  * With insights in what 'spammy' words and what typical 'ham-words' are, we can decide on building a feature-set which we can then use in the Naive Bayes algorithm for creating the classifier.
  * Note: it is always better to include more features, however performance might become an issue when having all words as features.
  * This is why in the field, developers tend to drop features that do not have a significant impact, purely for performance reasons.
  * Alternatively machine learning is done running complete Hadoop clusters.
  * */
  private def getPreparedData(trainingData: TrainingData): (PreparedData, Bag[String]) = {
    def getFeatures(emails: List[(File, String)]) = {
      def buildTDM =
        emails
          .flatMap { case (file, message) =>
            message
              .split(" ")
              .filter(s => s.nonEmpty && !trainingData.stopWords.contains(s))
              .map((file.getName, _))
              .groupBy(_._2)
              .mapValues(_.groupBy(_._1).mapValues(_.length))
          }

      buildTDM
        // Sort the words by occurence rate descending (amount of times the word occurs among all documents)
        .sortBy(- _._2.size.toDouble / trainingData.spamEmails.length)
        .take(NumberOfFeaturesToTake)
        .map(_._1)
    }

    // Initialize a bag of words that takes the top x features from both spam and ham and combines them
    val (bagOfWords, dimensionality) = {
      val (spamFeatures, hamFeatures) = (getFeatures(trainingData.spamEmails).toSet, getFeatures(trainingData.hamEmails).toSet)

      // We group them and then remove the intersecting features, as these are noise
      val data = (spamFeatures union hamFeatures) diff (spamFeatures intersect hamFeatures)

      (new Bag(data.toArray), data.size)
    }

    val preparedData = {
      def getData(emails: List[(File, String)]) = emails.map(a => bagOfWords.feature(a._2.split(" "))).toArray

      // Initialize the classifier array with first a set of 0 (spam) and then a set of 1 (ham) values that represent the emails
      val classifiers = Array.fill(NumberOfSamplesPerSet)(0) ++ Array.fill(NumberOfSamplesPerSet)(1)

      PreparedData(getData(trainingData.spamEmails) ++ getData(trainingData.hamEmails), classifiers, dimensionality)
    }

    (preparedData, bagOfWords)
  }

  /*
  * Given this feature bag and a set of training data, we can start training the algorithm.
  * For this we can choose a few different models: General, Multinomial and Bernoulli.
  *
  * The General model needs to have a distribution defined, which we do not know on beforehand, so this is not a good option.
  * The difference between the Multinomial and Bernoulli is the way in which they handle occurence of words.
  * The Bernoulli model only verifies whether a feature is there (binary 1 or 0), thus leaves out the statistical data of occurences,
  * whereas the Multinomial model incorporates the occurences (represented by the value).
  *
  * This causes Bernoulli model to perform bad on longer documents in comparison to the Multinomial model.
  * Since we will be rating emails, and we want to use the occurence, we focus on the multinomial but feel free to try out the Bernoulli model as well
  * */
  private def validate(trainedBayesModel: NaiveBayes, testFeatureVectors: List[Array[Double]]) = {
    val spams = testFeatureVectors
      .map(x => trainedBayesModel.predict(x))
//      .map {i => i match {
//        // In case the algorithm could not decide which category the email belongs to, it give a -1 (unknown) rather than a 0 (spam) or 1 (ham)
//        case -1 => println("Classification Unknown")
//        case 0 => println("Classified as spam")
//        case 1 => println("Classified as ham")
//      }; i }
      .count(_ == 0)

    println(s"Prediction: Spams - ${spams * 100 / testFeatureVectors.length}%")
  }
}















