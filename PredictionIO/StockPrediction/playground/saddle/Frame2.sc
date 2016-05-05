import org.saddle._

val f = Frame("x" -> Series("a" -> 1, "b" -> 2), "y" -> Series("c" -> 3, "a" -> 4))

// use integer offsets to select our data
f.rowAt(2)
f.rowAt(1, 2)
f.rowAt(1 -> 2)

f.colAt(1)
f.colAt(0, 1)
f.colAt(0 -> 1)

f.at(0, 0)
f.at(Array(1,2), 0) // extract rows 1,2 of column 0
f.at(0 -> 1, 1) // extract rows 0, 1 of column 1
f.at(0 -> 1, 0 -> 1) // extract rows 0, 1 of columns 0, 1

f.colSlice(0, 1) // frame slice consisting of column 0
f.rowSlice(0, 3, 2) // row slice from 0 until 3, striding by 2

// use indexes to select our data
f.row("a")
f.col("x")
f.row("a", "c") // select two rows
f.sortedRIx.row("a" -> "c") // slice rows from a to c
f.sortedRIx.row(Vec("a", "c"))

f("a", "x") // extract one one-element frame by keys
f("a" -> "c", "x") // three-row, one-column frame
f(Vec("a", "c"), "x")

f.rowSliceBy("a", "c", inclusive = false)
f.colSliceBy("x", "y", inclusive = true)

// split
f.colSplitAt(1)
f.colSplitBy("y")

f.rowSplitAt(1)
f.rowSplitBy("b")

// operates on rows
f.head(2)
f.tail(2)

// operates on cols
f.headCol(1)
f.tailCol(1)

f.first("b")
f.last("b")
f.firstCol("x")
f.lastCol("y")

// operates on rows
f.rfilter(s => s.mean > 2.0)

// operates on cols
f.filter(s => s.mean > 2.0)
f.filterIx(s => s == "x")
f.where(Vec(false, true))

// In general, methods operate on a column-wise basis, whereas the r-counterpart does so on a row-wise basis
