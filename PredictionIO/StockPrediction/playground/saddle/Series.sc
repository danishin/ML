import org.saddle._

val s = Series("a" -> 1, "b" -> 2, "c" -> 3, "a" -> 4)

s.values
s.index

s.at(1,2,3)
s.keyAt(1,2,3)

s.sorted
s.sortedIx

s("a", "b")
s("b", "a")

// you cannot reindex with "a" because it isn't unique
s.reindex("e", "c", "d")
//s.reindex("a", "c", "d")

s("a", "d")

s.resetIndex
s.setIndex(Index(11,22,33,44))

s.sortedIx.sliceBy("b", "c")
s.sortedIx.sliceBy("b" -> "c")
s.sortedIx.sliceBy(* -> "c")

s.slice(0, 2)
s.head(3)

val m = s.mask(s.values > 2)

m.hasNA
m.dropNA
m.pad

s.rolling(2, _.minKey)

s.groupBy.combine(_.sum)
s.groupBy.transform(v => v - v.mean)

val a = Series(Vec(1,4,2,3), Index("a", "b", "c", "d"))
val b = Series(Vec(5,2,1,8,7), Index("b", "c", "d", "e", "f"))

// because there is missing observation in each label a, e and f, the summation is ont done and instead an NA value is inserted into the result
// Generally, a full-outer join is performed
a + b

a.join(b, index.LeftJoin)
a.join(b, index.RightJoin)
a.join(b, index.InnerJoin)
a.join(b, index.OuterJoin)

// Finally let's look at a multiply indexed series
val t = Series(Vec(1,2,3,4), Index((1,1), (1,2), (2,1), (2,2)))

// Sometimes you want to move the innermost row label to be a column label instead.
val p = t.pivot

// This is how you get back the original Series
p.melt

// This generalizes to tuples of higher order

a align b
b align a