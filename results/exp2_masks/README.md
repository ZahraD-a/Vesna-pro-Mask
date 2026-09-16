# Experiment 2 -- masks under two sources of feedback

The runs behind `results/figures/fig_pirandellian*.pdf`. Same core in every run:
O +0.40, C -0.35, E +0.25, A +0.75, N -0.60. Same delta (0.5), same step size (0.005), same
learning rule (regret weighted by choice probability), 120 episodes, seeds 1-3.

| runs | scenario | mask |
|---|---|---|
| `social_masked_seed1-3` | Alice with Bob, Carol and Dave; outcomes are their replies | one per circumstance |
| `social_nomask_seed1` | same, `mask_delta 0`: plain Pro-AgentSpeak(L) | none |
| `nonsocial_masked_seed1-3` | Alice alone; outcomes are task results | one per circumstance |
| `nonsocial_nomask_seed1` | same, `mask_delta 0` | none |

The two scenarios differ in the plans available to Alice as well as in what returns as an
outcome; see `PlanCatalog.java`.

Each run directory holds the files listed in `results/README.md`, plus `episode_outcomes.csv`
(mean outcome per circumstance per episode) and, for the non-social runs, `mask_steps.csv`
(the worn mask after every single update).

Mean outcome per interaction:

| | work | home | conference |
|---|---|---|---|
| social, no mask | -1.715 | -0.104 | -0.447 |
| social, masked (3 seeds) | -1.261 | -0.036 | -0.175 |
| non-social, no mask | see run | | |
| non-social, masked (3 seeds) | see runs | | |

Three seeds, and several social masks are still moving at episode 120. Not final numbers.

    bash experiments/exp2_masks/run.sh NAME DELTA SHARED SHIFT SEED_LO SEED_HI [WEIGHTING] [ETA]
    bash experiments/exp2_masks/run_nonsocial.sh NAME DELTA SEED_LO SEED_HI [WEIGHTING] [ETA]
    python experiments/figures.py
