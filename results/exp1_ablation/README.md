# Experiment 1 — ablation

**Claim.** Letting the mask move improves the outcome over a frozen personality.

**Method.** 60 runs: 2 conditions x 3 compatibility measures x 10 seeds, 120 episodes each.
The conditions differ in exactly one parameter, `mask_delta`. At `0.0` every mask is clipped
to zero and can never move, so the agent keeps its core personality everywhere while running
the identical code path and writing the identical logs. This ablates the mask, not the learner.

    bash experiments/exp1_ablation/run.sh 1 10
    python experiments/exp1_ablation/analyze.py

## Result

Mean outcome +/- stderr over 10 seeds. `gain` is masked minus fixed, in units of its own
combined standard error.

| measure | condition | work | home | conference |
|---|---|---|---|---|
| dot | fixed | -1.2750 +/- 0.0119 | -0.6903 +/- 0.0160 | -0.4343 +/- 0.0211 |
| dot | masked | **-0.6979 +/- 0.0172** | **-0.5111 +/- 0.0270** | **-0.3194 +/- 0.0160** |
| | *gain* | *+0.577 (27.5x se)* | *+0.179 (5.7x se)* | *+0.115 (4.3x se)* |
| l1 | fixed | -1.3030 +/- 0.0180 | -1.1241 +/- 0.0156 | -1.0176 +/- 0.0155 |
| l1 | masked | -1.1634 +/- 0.0187 | -1.1069 +/- 0.0240 | -0.9887 +/- 0.0191 |
| | *gain* | *+0.140 (5.4x se)* | *+0.017 (0.6x se)* | *+0.029 (1.2x se)* |
| cosine | fixed | -1.1572 +/- 0.0209 | -0.8368 +/- 0.0151 | -0.6708 +/- 0.0208 |
| cosine | masked | **-0.6662 +/- 0.0177** | **-0.6421 +/- 0.0241** | -0.6167 +/- 0.0114 |
| | *gain* | *+0.491 (17.9x se)* | *+0.195 (6.8x se)* | *+0.054 (2.3x se)* |

Masks help at work under all three measures. Under dot and cosine they help in every
circumstance. Under l1 the gain at home (0.6x se) and conference (1.2x se) cannot be
separated from seed noise.

## Why l1 behaves differently

A personality trait is in [0,1] but a plan annotation is in [-1,1]. Under dot and cosine a
plan opposed to the agent scores negative and is never chosen. Under l1 the per-trait term
is `1 - |a-b|`, which over five traits stays positive even for the most opposed plan, so
nothing is ever excluded.

Scores against Alice's core:

| plan | dot | l1 | cosine |
|---|---|---|---|
| pair_up | +1.235 | +3.850 | +0.842 |
| polite_decline | **-0.390** | +2.050 | **-0.461** |
| ignore | **-1.390** | +0.150 | **-0.841** |

What the agent actually plays, averaged over 10 seeds:

| | dot fixed | dot masked | l1 fixed | l1 masked | cosine masked |
|---|---|---|---|---|---|
| polite_decline | 0.0% | 0.5% | 8.7% | 8.6% | 0.7% |
| ignore | 0.0% | 0.0% | 0.6% | 1.6% | 0.0% |
| **out of character** | **0.0%** | **0.5%** | **9.3%** | **10.2%** | **0.7%** |

Under l1 about a tenth of interactions go to styles that are out of character, and learning
does not reduce that -- it rises slightly. Those styles are rejected in most circumstances,
which is both why the l1 baseline is worse and why the mask has less left to gain.

**This is a finding, not a robustness check.** Identity preservation -- the agent cannot act
against its own character until a mask makes it able to -- holds under dot and cosine and
does not hold under l1 in this mixed-range setting. The paper should say which measure the
identity claim depends on rather than presenting the three as interchangeable.

## Known limitation

Every mask drives agreeableness to the `-0.5` clip in every circumstance, because all three
receivers treat warm-adjacent styles as improper at work and conference. The learned personas
are therefore all *reductions* of the core rather than movements in different directions,
which is visible in `results/figures/fig_pirandello.pdf` as three polygons nested inside the
core. A receiver population that rewarded warmth somewhere would produce richer mask geometry.
This is a property of the scenario, not of the mechanism.

`figures/figure_ablation.pdf`, `tables/table_ablation.csv`, `summary.csv` (one row per
condition x measure x seed x circumstance), and `runs/` with the raw outputs.
