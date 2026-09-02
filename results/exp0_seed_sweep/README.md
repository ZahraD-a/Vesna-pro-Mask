# Seed sweep

**Question.** How far apart must two conditions be before the difference can be told
from seed noise?

**Method.** Eight runs of the standard scenario, identical except for the seed
(`experiments/seed_sweep.sh 1 8`), aggregated by `experiments/analyze_sweep.py`.

**Result.**

| circumstance | mean outcome | stderr | mask norm | rejected |
|---|---|---|---|---|
| work | -0.6956 | 0.0196 | 0.8642 | 44.7% |
| home | -0.4922 | 0.0181 | 0.8262 | 38.5% |
| conference | -0.3223 | 0.0169 | 0.6969 | 36.7% |

Largest stderr is **0.0196**, under the 0.03 threshold, so eight seeds already separate
effects of about 0.06 or larger. Ten seeds per condition is enough for the ablation and
transfer experiments; there is no need to double the budget.

**Why this exists.** Before seeding, repeated runs of identical code gave work outcomes
between -0.64 and -0.83. Any single-run number was indistinguishable from noise. Runs are
now reproducible: two runs at the same seed produce byte-identical `report.txt`,
`learned_masks.csv`, `episode_log.csv` and `reward_components.csv`, while a different seed
differs.

`runs/` holds the per-seed reward components and learned masks. `summary.csv` has one row
per (seed, circumstance).
