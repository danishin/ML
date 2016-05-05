package dase

import java.time.LocalDate

import dase.data.StockTimeSeries.Daily
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, _}
import org.saddle.scalar.{NA, Scalar}
import org.saddle.{Frame, Index, ORD, ST, Series, Vec}

package object data {
  import common.implicits._

  case class Daily(close: Double, adjClose: Double, adjReturn: Double, volume: Double, active: Boolean)
  object Daily {
    def empty = Daily(0.0, 0.0, 0.0, 0.0, false)
  }

  type StockTimeSeries = Series[LocalDate, Daily]

  implicit class StockTimeSeriesExt(timeSeries: StockTimeSeries) {
    /**
      * align `other` series with this series (non-existing keys in `other` series are filled with NA) and
      * fill `NA` values in `other` series with either default / previous value (closing value).
      */
    def alignAndFill(other: StockTimeSeries): Series[LocalDate, Daily] = {
      val alignedOther = timeSeries.align(other)._2

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

  object StockTimeSeries {
    def empty(timeIndex: Index[LocalDate]): StockTimeSeries =
      Series(Vec(Array.fill(timeIndex.length)(Daily.empty)), timeIndex)

    def from(json: JObject, fromDate: LocalDate, toDate: LocalDate, checkContinuous: Boolean): StockTimeSeries = {
      implicit val formats = DefaultFormats

      val closeList = (json \ "close").extract[Array[Double]]
      val adjCloseList = (json \ "adjclose").extract[Array[Double]]
      val volumeList = (json \ "volume").extract[Array[Double]]
      val tList = (json \ "t").extract[Array[Long]].map(t => LocalDate.ofEpochDay(t))

      val arr = tList.indices
        .drop(1)
        .map { idx =>
          if (checkContinuous)
            require(tList(idx - 1).plusDays(1) == tList(idx),
              s"Time must be continuous. There is a gap between ${tList(idx - 1)} and ${tList(idx)}. Please import data to cover the gap or use a shorter range."
            )

          val adjReturn = (adjCloseList(idx) / adjCloseList(idx - 1)) - 1

          val daily = Daily(
            close = closeList(idx),
            adjClose = adjCloseList(idx),
            adjReturn = adjReturn,
            volume = volumeList(idx),
            active = true
          )

          tList(idx) -> daily
        }

      Series(arr: _*)
        .sortedIx
        .sliceBy(fromDate, toDate)
    }
  }
}
