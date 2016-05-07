package common

import java.time.LocalDate

import org.joda.time.DateTime
import org.saddle.scalar.{NA, Scalar}
import org.saddle.{Series, _}

object implicits {
  import scala.language.implicitConversions

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isBefore _)

  implicit def javaLocalDate2JodaDateTime(localDate: LocalDate): DateTime = new DateTime(localDate.toEpochDay)

  implicit class SeriesExt[K : ST : ORD, A : ST](series: Series[K, A]) {
    def getPrev(key: K): Scalar[A] = series.index.prev(key).fold[Scalar[A]](NA)(series.get)

    def forall(predicate: A => Boolean): Boolean = series.findOne(a => !predicate(a)) == -1

    /**
      * slice `num` elements starting from left of given `key`
      *
      * eg. Series("a" -> 1, "b" -> 2, "c" -> 3, "d" -> 4, "f" -> 5).sliceLeftFrom("d", 2) // Series("b" -> 2, "c" -> 3)
      */
    def sliceLeftFrom(key: K, num: Int): Series[K, A] = series.splitBy(key)._1.tail(num)
  }
}
