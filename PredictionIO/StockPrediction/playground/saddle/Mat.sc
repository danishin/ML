import org.saddle._

val m = Mat(Array(1,3), Array(2,4))

mat.ident(10)

mat.ones(5,5)
mat.zeros(5, 5)
mat.diag(Vec(1,2))

// element-wise multiplication
m * m

// matrix multiplication; note implicit conversion to Double
m dot m
m mult m

// matrix-vector multiplication
m dot Vec(2,1)

m * 2

m.T
m.transpose

m.numCols
m.numRows
m.isSquare
m.isEmpty

m.at(0, 1)
m.raw(0, 1)
m.takeRows(0)
m.withoutCols(0)
m.takeCols(0)
m.col(0)
m.row(0)
m.rows()
m.cols()

val mm = Mat(2,2, Array(1,2, na.to[Int], 4))

mm.rowsWithNA
mm.dropRowsWithNA
mm.reshape(1,4).toVec

mat.rand(2,2).roundTo(2)

mm.print(2, 2)