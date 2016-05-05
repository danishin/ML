package dase.data

import com.github.nscala_time.time.Imports._
import org.apache.spark.broadcast.Broadcast
import org.saddle._
import org.saddle.index.IndexTime

// A data view of RawData from [idx - maxWindowSize + 1 : idx]
// Notice that the last day is *inclusive*.
// This clas takes the whole RawData reference, hence should *not* be serialized
case class DataView(rawData: RawData, timeIdx: Int) {
  def priceFrame(windowSize: Int = 1): Frame[DateTime, String, Double] =
    rawData._priceFrame.rowSlice(timeIdx - windowSize + 1, timeIdx + 1)

  def activeFrame(windowSize: Int = 1): Frame[DateTime, String, Boolean] =
    rawData._activeFrame.rowSlice(timeIdx - windowSize + 1, timeIdx + 1)

  override def toString: String = priceFrame().toString
}