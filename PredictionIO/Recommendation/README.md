# NOTES
1. PredictionIO 0.9.5 MUST be used with Spark 1.4.X - This wont be much problem in production as you will use either pre-built docker image or EC2 image. Only problematic in workstation where you install things yourself.

2. Spark depends on Scala 2.10.X, which means PredictionIO also does (in fact, it doesn't even provide `_2_11`).

