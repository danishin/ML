import org.saddle._
import org.saddle.time._
import RRules._

// Library provides an implementation of recurrence rules (RRule).

Index.make(bizEoms, datetime(2013, 1, 1), datetime(2013, 5, 1))

/*
* RRule object allows for complex recurrence rule generation following the RFC 2445 standard.
* (The implementation utilizes google-rfc-2445 under the hood)
* RRules can be combined with the Index.make to instantiate indexes as above, or can be used independently do complex date/time math
* For example, the counting method of RRule provides syntactic sugar to do date offset calculation
* */

import scala.language.reflectiveCalls
RRule(MONTHLY).withInterval(2).counting(5).from(datetime(2013, 1, 1))

// you may also generate iterators of DateTimes using RRule
weeklyOn(FR)
  .withInterval(2)
  .from(datetime(2013, 1, 1))
  .take(5)
  .toList

// you may conform a datetime instance forward or backward using the conform method
conform(weeklyOn(FR), datetime(2013, 1, 1), forward = false)