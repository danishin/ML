package smile_example

import java.awt.{Color, Dimension}

import smile.plot.ScatterPlot
import smile.regression.OLS

import scala.swing.{MainFrame, SimpleSwingApplication}

/* Model
*
* When one talks about machine learning, often the term model is mentioned.
* The model is the result of any machine learning method and the algorithm used within this method.
* This model can be used to make predictions in supervised or to retrieve clusterings in unsupervised learning.
* Chances are high that you will encounter the terms online and offline training of the model in the field
* The idea behind online training is that you add training data to an already existing model
* whereas with offline training you generate new model from scratch.
* For performance reasons, online training would be the most preferable method. However for some algorithms this is not possible
* */

/* Prior - classification
*
* The prior value that belongs to a classifier given a datapoint represents the likelihood that this datapoint belongs to this classifier.
* In practice, this means that when you get a prediction for a datapoint, the prior value that is given with it,
* represents how 'convinced' the model is regarding the classification given to that datapoint.
* */

/*
* Predicting weight based on height - Ordinary Least Squares (Linear Regression)
*
* Ordinary Least Squares is a form of linear regression.
* The idea behind linear regression is that an 'optimal' regression line is fitted on your training datapoints.
* Note that this only works if your data is linear, and does not have huge outliers.
* If this is not the case you could try to manipulate your data until this is the case, like by taking the sqrt or log over the data
* */
object LinearRegressionExample extends SimpleSwingApplication with util.Helper {
  object ParseGender {
    def unapply(str: String): Option[Double] = str match {
      case "\"Male\"" => Some(0.0)
      case "\"Female\"" => Some(1.0)
      case _ => None
    }
  }

  def top = new MainFrame {
    title = "Linear Regression Example"

    val (x, y) = getPreparedData

    val plot = {
      val (genders, heightAndWeights) = (x zip y).map{ case (Array(gender, height), weight) => (gender.toInt, Array(height, weight)) }.unzip

      val plot = ScatterPlot.plot(heightAndWeights, genders, '@', Array(Color.blue, Color.green))
      plot.setTitle("Weight and heights for male and females")
      plot.setAxisLabel(0, "Heights")
      plot.setAxisLabel(1, "Weights")
      plot
    }

    peer.setContentPane(plot)
    size = new Dimension(400, 400)

    /*
    * Now that we have seen the data and see that indeed we can come up with a linear regresison line to fit this data, it is time to train a model.
    * */
    val olsModel = new OLS(x, y)

    println(s"Prediction for Male of 1.7M: ${olsModel.predict(Array(0.0, 170.0))}")
    println(s"Prediction for Female of 1.7M: ${olsModel.predict(Array(1.0, 170.0))}")
    println(s"Model Error: ${olsModel.error()}")
    println(s"RSquared: ${olsModel.RSquared()}")
    println(s"Adjusted RSquared: ${olsModel.adjustedRSquared()}")

    /*
    * If you recall from the classification algorithms, there was a prior value to say something about the performance of your model.
    * Since regression is a stronger statistical method, you have an actual error value now.
    * This value represents how far off the fitted regression line is in average, such that you can say that for this model, the prediction for a male of 1.70m is 79.15KG +/- the error of 4.54kg.
    * Note that if you would remove the distinction between males and females, this error would increase to 5.5428.
    * In other words, adding the distinction between male and female, increases the model accuracy by +/- 1kg in its prediction
    *
    * Finally Smile also provides you with some statistical information regarding your model.
    * The method RSquared gives you the root-mean-square-error (RMSE) from the model divided by the RMSE from the mean.
    * This value will always be between 0 and 1.
    * If your model predicts every datapoint perfectly, RSquared will be 1, and if the model does not perform better than the mean function, the value will be 0
    * In the field, this measure is often multiplied by 100 and then used asd representation of how accurate the model is.
    * Because this is a normalized value, it can be used to compare the performance of different models.
    * */
  }

  def getPreparedData: (Array[Array[Double]], Array[Double]) =
    files.read("resources/LinearRegression/OLS_Regression_Example_3.csv")
      .drop(1)
      .map(_.split(",") match {
        // convert from imperial system to metric system
        case Array(ParseGender(gender), ParseDouble(height), ParseDouble(weight)) => (Array(gender, height * 2.54), weight * 0.45359)
      })
      .toArray
      .unzip
}
