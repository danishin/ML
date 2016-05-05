import org.saddle._

val f = Frame("x" -> Series("a" -> 1, "b" -> 2), "y" -> Series("c" -> 3, "a" -> 4))

f.dropNA
f.rdropNA

// When two frames are involved, they are re-indexed along both axes to match the outer join of their indices,
// but any mising observation in either will carry through the calculations
f + 1
f * f
f + f
f - f

// you can effectively supply your own binary frame operation using joinMap, which lets you control the join style on rows and columns
f.joinMap(f, rhow = index.LeftJoin, chow = index.LeftJoin) { case (a, b) => a + b }

// if you want simply to align one frame to another without performing an operation
val (f1, f2) = f.align(f, rhow=index.LeftJoin, chow=index.OuterJoin)

f.toMat

f.sortedRIx
f.sortedCIx
f.sortedRows(0, 1)
f.sortedCols(1, 0)

f.sortedRowsBy(s => s.at(0))
f.sortedColsBy(s => s.at(0))

f.mapValues(i => i + 1)
f.mapVec(v => v.demeaned)
f.reduce(s => s.mean)
f.transform(s => s.reversed)

f.mask(_ > 2)

// columns (rows) containing ONLY NA values can be dropped
f.mask(Vec(false, true, true)).rsqueeze

f.groupBy(_ == "a").combine(_.count)
f.groupBy(_ == "b").transform(_.demeaned)

f.melt.mapRowIndex(_.swap).colAt(0).pivot
f.mapColIndex(i => (1, i)).stack

f.toSeq