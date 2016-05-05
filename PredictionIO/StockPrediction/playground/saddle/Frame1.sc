import org.saddle._

// A Frame combines a Mat with a row index and a column index which provides a way to index into the Mat.
// First, note a Mat[T] converts implicitly to a Frame[Int, Int, T]
val _: Frame[Int, Int, Double] = mat.rand(2, 2)

// A Frame is represented internally as a sequence of column Vec instances all sharing the same row index;
// additionally, a transpose of the data is created lazily if cross sections of data are requested.
val v1 = Vec(1,2)
val v2 = Vec(3,4)
val s1 = Series("a" -> 1, "b" -> 2)
val s2 = Series("b" -> 3, "c" -> 4)

Frame(v1, v2)
Frame("x" -> v1, "y" -> v2)
Frame(Seq(v1, v2), Index("a", "b"))
Frame(Seq(v1, v2), Index("a", "b"), Index("x", "y"))
Frame(s1, s2)
Frame(Seq(s1, s2), Index("x", "y"))

// Frame elements are all recognized as the same type by the compiler
// But if you want to work with frames whose columns contain heterogenous data, there are a few facilities to make it easier.
// You can construct Frame[_, _, Any] using the Panel() constructor, which mirrors the Frame() constructor
val p: Frame[Int, Int, Any] = Panel(Vec(1,2,3), Vec("a","b","c"))
p.colType[Int]
p.colType[Int, String]

p.emptyRow
p.emptyCol

val f = Frame("x" -> s1, "y" -> s2)

f.numRows
f.numCols

f.setRowIndex(Index(10, 20, 30))
f.setColIndex(Index("p", "q"))
f.resetRowIndex
f.resetColIndex

f.mapRowIndex(s => s + "!")
f.mapColIndex(s => s + "?")

// ... Frame2