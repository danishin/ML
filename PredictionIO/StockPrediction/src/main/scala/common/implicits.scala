package common

import java.time.LocalDate

import org.joda.time.DateTime
import org.saddle.scalar.{NA, Scalar}
import org.saddle.{Series, _}

object implicits {
  import scala.language.implicitConversions

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isBefore _)

  implicit def javaLocalDate2JodaDateTime(localDate: LocalDate): DateTime = new DateTime(localDate.toEpochDay)

//  implicit class LocalDateExt(date: LocalDate) {
//    def toJodaDateTime: DateTime = javaLocalDate2JodaDateTime(date)
//  }

  implicit class SeriesExt[K : ST : ORD, A : ST](series: Series[K, A]) {
    def getPrev(key: K): Scalar[A] = series.index.prev(key).fold[Scalar[A]](NA)(series.get)

    def forall(predicate: A => Boolean): Boolean = series.findOne(a => !predicate(a)) == -1
  }
}
