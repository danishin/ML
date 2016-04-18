package smile_example

import java.awt.{Color, Dimension}

import smile.classification.KNN
import smile.plot.ScatterPlot
import smile.validation.CrossValidation

import scala.swing.MainFrame

case class Datum(xCoord: Double, yCoord: Double, classifier: Int)

/*
* Labeling ISPs based on their down/upload speed - KNNN
*
* The idea behind KNN is as follows:
* Given a set of points that are classified, you can classify the new point by looking at its K neighbours (K being a positive integer).
*
* The idea is that you find the K-neighbours by looking at the euclidean distance between the new point and its surrounding points
* For these neighbours, you then look at the biggest representative class and assign that class to the new point.
* */
object KNNExample extends util.Helper /*with SimpleSwingApplication*/ {
  /*
  * The idea behind plotting the data is to verify whether K-NN is a fitting Machine Learning algorithm for this specific set of data.
  *
  * Since the groups are mixed the K-NN algorithm is a good choice,
  * as fitting a linear decision boundary would cause a lot of false classifications in the mixed area
  * */
  // Use with `SimpleSwingApplication` to visualize data points
  def top = new MainFrame {
    title = "KNN Example"

    val (xs, ys) = getDataFromCSV
    val plot = ScatterPlot.plot(xs, ys, '@', Array(Color.red, Color.blue))

    peer.setContentPane(plot)

    size = new Dimension(400, 400)
  }

  def main(args: Array[String]) = {
    val (xs, ys) = getDataFromCSV

    validate(xs, ys)

    predict(xs, ys)(Array(5, 9.11))
  }

  private def getDataFromCSV: (Array[Array[Double]], Array[Int]) =
    files.read("resources/KNN/KNN_Example_1.csv")
      .drop(1)
      .map(_.split(",") match {
        case Array(downloadSpeed, uploadSpeed, label) => (Array(downloadSpeed.toDouble, uploadSpeed.toDouble), label.toInt)
      })
      .toArray
      .unzip

  /*
  * In Machine Learning, there are 2 key parts: Prediction and Validation.
  *
  * First we look at the validation, as using a model without any validation is never a good idea.
  * The main reason to validate the model here is to prevent overfitting.
  * However, before we even can do validation, a correct K should be chosen.
  *
  * There is no golden rule for finding the correct K.
  * However, finding a good K that allows for most datapoints to be classified correctly can be done by looking at the data.
  * Additionally, the K should be picked carefully to prevent undecidability by the algorithm.
  * Rule of thumb that K should be the squre root of the number of features.
  *
  * We stick with K = 3 here as picking 1 will lead to higher false-classifications around decision boundaries and picking 2 results in the error regarding our two labels
  *
  * For this example, we do 2-fold Cross Validation.
  * In general, 2-fold cross validation is a rather weak method of model validation, as it splits the dataset in half and only validates twice, which still allows for overfitting.
  * */
  private def validate(xs: Array[Array[Double]], ys: Array[Int]) = {
    val cv = new CrossValidation(ys.length, 2)

    def zipXY(indexList: Array[Int]): (Array[Array[Double]], Array[Int]) =
      indexList
        .map(index =>
          (xs zip ys)
            .zipWithIndex
            .collectFirst{ case (xy, `index`) => xy }
            .get
        )
        .unzip

    (cv.train.map(zipXY) zip cv.test.map(zipXY))
      .foreach {
        case ((trainXs, trainYs), (testXs, testYs)) =>
          //          println(Array(trainXs.deep, trainYs.deep, testXs.deep, testYs.deep).mkString("\n============================\n"))

          val knn = KNN.learn(trainXs, trainYs, 3)

          // For each test data point, make a prediction with the model
          val predictions = testXs.map(x => knn.predict(x)).zipWithIndex
          val error = predictions.count{ case (predictedY, index) => predictedY != testYs(index) }

          println(s"False prediction rate: ${(error / predictions.length) * 100}%")
      }

  }

  private def predict(xs: Array[Array[Double]], ys: Array[Int])(unknownX: Array[Double]) = {
    val knn = KNN.learn(xs, ys, 3)
    knn.predict(unknownX) match {
      case 0 => println("Internet Service Provider Alpha")
      case 1 => println("Internet Service Provider Beta")
      case _ => sys.error("Unexpected Prediction")
    }
  }
}
