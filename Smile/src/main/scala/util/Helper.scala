package util

import java.io.File

import com.github.tototoshi.csv.CSVReader

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.io.{BufferedSource, Codec}
import scala.util.matching.Regex

trait Helper {
  import scala.language.implicitConversions
  import scala.language.higherKinds

  implicit def str2File(path: String): File = new File(path)

  object files {
    def read(file: File, codec: Codec = Codec.UTF8): List[String] = {
      val source = scala.io.Source.fromFile(file)(codec)
      val lines = source.getLines().toList
      source.close()
      lines
    }

    def readCSV(file: File): List[List[String]] = {
      val reader = CSVReader.open(file)
      val data = reader.all()
      reader.close()
      data
    }

    def slurp(file: File, codec: Codec = Codec.UTF8): String =
      read(file, codec).mkString("\n")

    def listFiles(directory: File): List[File] = {
      require(directory.isDirectory)

      directory
        .listFiles()
        .filter(f => f.isFile && !f.getName.contains(".DS_Store") && !f.getName.contains("cmds"))
        .toList
    }
  }


  implicit class TraversableExt[A, F[X] <: TraversableLike[X, F[X]]](traversable: F[A]) {
    def debug[That](name: String)(implicit bf: CanBuildFrom[F[A], A, That]): That =
      traversable.map{ a => println(name + ": " + a); a }(bf)
  }

  implicit class StringExt(string: String) {
    def takeFrom(str: String): String = {
      val index = string.indexOf(str)
      string.substring(index)
    }
  }

  object ParseDouble {
    def unapply(str: String): Option[Double] =
      try Some(str.toDouble)
      catch { case _: Throwable => None }
  }
}
