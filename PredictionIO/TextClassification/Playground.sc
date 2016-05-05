import breeze.linalg._

val v1 = DenseVector(1,2,3)
val v2 = DenseVector(2,3,4)

v1 dot v2
cross(v1, v2)

math.sqrt(v1 dot v1)

v1.t

val m1 = DenseMatrix.create(2, 2, Array(1, 3, 2, 4))

//rank(m1)

