package smile_example

import java.awt.{Color, Dimension}
import java.io.File

import smile.classification.SVM
import smile.math.kernel.GaussianKernel
import smile.plot.ScatterPlot

import scala.swing.{MainFrame, SimpleSwingApplication}

/*
* Hyperplane
*
* In geometry, a hyperplane is a subspace of one dimension less than its ambient space.
* If a space is 3-dimensional then its hyperplanes are the 2-dimensional planes,
* while if the space is 2-dimensional, its hyperplanes are the 1-dimensional lines.
* This notion can be used in any general space in which the concept of the dimension of a subspace is defined
* */

/*
* Kernel
*
* In both statistics (kernel density estimation or kernel smoothing) and machine learning (kernel methods) literature,
* kernel is used as a measure of similarity.
* In particular, the kernel function k(x,..) defines the distribution of similarities of points around a given point x.
* k(x, y) denotes the similarity of point x with another given point y.
* */

/*
* Using Support Vector Machines (SVM)
*
* The basic SVM is a binary classifier that divides a dataset into 2 parts by picking a hyperplane that represents the largest separation between the datapoints
* A SVM takes a so called 'correction rate' value.
* If there is no perfect split, the correction rate allows for picking a hyperplane that still splits as well as possible within that error rate.
* Thus the correction rate allows the hyperplane to be fit even when there are some points in the way.
* This means that we cannot come up with a 'standard' correction rate for every case.
* However when there is no overlap in the data, lower values should perform better than higher values.
*
* I just explained the basic SVM, which is a binary classifier, but this same idea can be used with more classes as well.
* However, for now we will stick with 2 classes as there is enough to address already with just 2 classes
* */
object SVMExample extends SimpleSwingApplication with util.Helper {
  def top = new MainFrame {
    title = "SVM Examples"

    val (data, classifiers) = getTrainingData("resources/SVM/SVM_Example_1.csv")

    val plot = ScatterPlot.plot(data, classifiers, '@', Array(Color.blue, Color.green))

    peer.setContentPane(plot)

    size = new Dimension(400, 400)

    // Here we do our SVM fine tuning with possibly different kernels
    /*
    * It is clear from the plot that a linear regression line would not work.
    * Instead, we will use a SVM to make predictions.
    *
    * GuassianKernel with a sigma of 0.01, a margin penalty of 1.0 and the amount of classes of 2 to SVM
    *
    * GuassianKernel
    * This kernel represents the way in which the SVM will calculate the similarity over pairs of datapoints in the system
    * For the GaussianKernel, the variance in the euclidian distance is used.
    * The reason for picking the GuassianKernel specifically is because the data does not contain a clear structure such as a linear, polynomial or hyperbolic function.
    * Instead the data is clustered in 3 groups
    *
    * The parameter we pass in the constructor of the GaussianKernel is the sigma.
    * This sigma value represents a smoothness value of the kernel.
    * We will show how changing this parameter affects the predictions.
    *
    * As margin penalty we pass 1.
    * This parameter defines the margin of the vectors in the system, thus making this value lower results in more bounded vectors.
    * We will show with a set of runs and their results what kind of effect this has in practice.
    * Note that the s: stands for sigma and the c: stands for the correction penalty.
    *
    * Unfortunately, there is no golden rule for finding the right sigma for every dataset.
    * Possibly one of the best approaches is to calculate the sigma for your data, which is the root of variance, and then take steps around that value to see which sigma performs well
    * Since the variance in this data was between 0.2 and 0.5, we took this as center and explored several values at each side of this center to see the performance of the SVM with the gaussian kernel in our case
    *
    * When we look at the results and their false prediction percentages, it shows that the best performance is with a very low sigma (0.001) and a correction rate of 1.0 or up.
    * However, if we would use this model in practice with new data, it might be overfitted.
    * This is why you should be careful when testing the model against its own training data.
    * A better approach would be to perform cross validation or verify against future data.
    * */
    val svm = new SVM(new GaussianKernel(0.01), 1.0, 2)
    svm.learn(data, classifiers)
    svm.finish()

    // Calculate how well the SVM predicts on the training set
    val predictions = data.map(svm.predict) zip classifiers

    println(s"${predictions.count(x => x._1 != x._2).toDouble / predictions.length * 100}% false predicted")
  }

  def getTrainingData(file: File): (Array[Array[Double]], Array[Int]) =
    files.readCSV(file)
      .drop(1)
      .map { case List(x, y, label) => (Array(x.toDouble, y.toDouble), label.toInt) }
      .toArray
      .unzip
}
