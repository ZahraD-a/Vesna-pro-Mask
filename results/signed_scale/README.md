# Signed scale: first runs

Personalities are now stored on `[-1,1]`, the same scale as plan annotations. Every
personality, including the receivers' reaction plans, was converted with `2v - 1`.

Seed 1, 120 episodes, dot compatibility. One run per condition, so no error bars yet.

| run | mask_delta | work | home | conference |
|---|---|---|---|---|
| `no_mask` | 0.0 | -1.931 | -0.051 | -0.655 |
| `mask_delta_0.5` | 0.5 | -0.806 | -0.183 | -0.343 |
| `mask_delta_1.0` | 1.0 | -0.799 | -0.306 | -0.310 |

Mean outcome per interaction, from each run's `reward_components.csv`.

The mask helps at work and at the conference. At home it does worse than no mask in this
seed. Not yet confirmed across seeds.

`mask_delta_0.5` is also copied to `results/latest`. `no_mask` is the same agent with the
mask unable to move, which presents its core in every circumstance.

Learned presented personality, `mask_delta_0.5` (core: O +0.10, C -0.30, E 0.00, A +0.50, N -0.40):

| | O | C | E | A | N |
|---|---|---|---|---|---|
| work | +0.15 | +0.20 | -0.04 | 0.00 | 0.00 |
| home | +0.42 | +0.05 | +0.49 | +0.34 | -0.19 |
| conference | +0.42 | +0.20 | +0.37 | +0.38 | -0.16 |
