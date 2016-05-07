package dase.data

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

import common.implicits._
import org.json4s.{DefaultFormats, _}
import org.saddle.Series

object time_series {
  case class Daily(close: Double, adjClose: Double, adjReturn: Double, volume: Double, active: Boolean) extends Serializable

  type StockTimeSeries = Series[LocalDate, Daily]

  implicit class StockTimeSeriesExt(series: StockTimeSeries) {
    /**
      * align `other` series with this series (non-existing keys in `other` series are filled with NA) and
      * fill `NA` values in `other` series with either default / previous value (closing value).
      */
    def alignAndFill(other: StockTimeSeries): StockTimeSeries = {
      val alignedOther = series.align(other)._2

      alignedOther
        .fillNA(date => Daily(
          close = alignedOther.getPrev(date).map(_.close).getOrElse(0.0),
          adjClose = 0.0,
          adjReturn = 0.0,
          volume = 0.0,
          active = false
        ))
    }
  }

  // ticker, open, high, low, close, adjclose, volume, t
  case class YahooData(close: List[Double], adjclose: List[Double], volume: List[Double], t: List[Double]) {
    def transpose: List[List[Double]] = {
      require(
        close.length == adjclose.length && adjclose.length == volume.length && volume.length == t.length,
        s"all columns must have the same length: close: ${close.length} adjClose: ${adjclose.length} volume: ${volume.length}, t: ${t.length}")

      List(close, adjclose, volume, t).transpose
    }
  }
  object StockTimeSeries {
    private def LocalDateFromEpochSeconds(seconds: Int)(implicit timezone: ZoneId): LocalDate =
      LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), timezone).toLocalDate

    implicit val formats = DefaultFormats

    def from(json: JObject, checkContinuous: Boolean)(implicit timezone: ZoneId): StockTimeSeries = {
      val arr = json.extract[YahooData]
        .transpose
        .sliding(2)
        .map { case List(List(prevClose, prevAdjClose, prevVolume, prevT), List(close, adjClose, volume, t)) =>
          val prevDate = LocalDateFromEpochSeconds(prevT.toInt)
          val date = LocalDateFromEpochSeconds(t.toInt)

//          if (checkContinuous)
//            require(prevDate.plusDays(1) == date,
//              s"Time must be continuous. There is a gap between $prevDate and $date. Please import data to cover the gap or use a shorter range."
//            )

          val adjReturn = (adjClose / prevAdjClose) - 1
          date -> Daily(close, adjClose, adjReturn, volume, active = true)
        }
        .toArray

//      println(arr.deep)

      Series(arr: _*)
        .sortedIx
//        .sliceBy(fromDate, toDate)
    }
  }
}
