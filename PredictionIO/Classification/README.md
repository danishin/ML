## Hyperparameter Tuning

`pio eval` outputs the best parameters found during its evaluation to `best.json`
Simply use this best engine variant `best.json` in place of arbitrarily chosen `engine.json`

1. `pio eval dase.evaluator.AccuracyEvaluation engine.EvalEngineParams`

2. `pio train best.json`

3. `pio deploy best.json`