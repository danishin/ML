import org.saddle._

val v = Vec(1, na.to[Int], 2)

v.fillNA(i => 111)
v.contents
