# Non-social instantiation

**Claim.** The mask mechanism does not depend on where reward comes from. It learns the same
way when the outcome is reported by the environment as when it is reported by a partner.

**Method.** A second MAS, `vesna_energy.jcm`: one agent, no partners, no messages. Alice has
to keep her own energy up. Energy drains each round; below 40 she is `depleted`, above it
`rested`. She chooses between three ways of recovering, and the environment reports back
`goal_achieved`, `delayed` or `failed`.

    ./gradlew runEnergy

Writes to `results/nonsocial/latest/`. The social scenario is untouched and still writes to
`results/latest/`.

## What is shared, and what is not

Shared, unchanged: `Temper`, `MaskLearner`, `RewardMachine`, `Mask`, every internal action,
and `mask_rules.asl`. The reward machine needed no modification at all -- `goal_achieved`,
`delayed` and `failed` were already in its outcome table beside `accepted`, `tolerated` and
`rejected`.

Different: the plan library (three recovery styles instead of nine social ones, selected by
`domain: energy`), the agent file, and `env_outcome`, which stands where a partner stands.

`mask_rules.asl` is deliberately one file for both. The social and non-social rules have the
same shape:

    circumstance(depleted)    :- energy(E) & E <  40.
    wearable(mask_work)       :- circumstance(work).
    wearable(mask_depleted)   :- circumstance(depleted).

Whether a circumstance is social or environmental depends only on what puts it in the belief
base. The mask machinery cannot tell the difference, which is the property being demonstrated.

## Result

Both masks start at zero and converge, and they converge differently.

| mask | O | C | E | A | N | norm |
|---|---|---|---|---|---|---|
| mask_depleted | -0.05 | **+0.45** | -0.50 | -0.35 | -0.50 | 0.910 |
| mask_rested | -0.35 | **+0.25** | -0.40 | -0.45 | -0.50 | 0.893 |

Conscientiousness rises in both but nearly twice as far when depleted, which is what the
environment rewards: only the slow, thorough recovery gets her back over the line from a low
start, while from a high start the cheaper option is enough.

The behaviour follows:

| circumstance | outcome | push_through | patient_recovery |
|---|---|---|---|
| depleted | -1.301 (76% failed) | 11.1% -> **0.0%** | 50.0% -> **64.3%** |
| rested | +2.500 (100% achieved) | -- | steady_recovery -> 100% |

Note the contrast with the social runs. Here the environment has an unambiguous best action in
each circumstance and the policy converges to it, in `rested` to a pure strategy. The social
scenario has no such action and its policy stays mixed at about 2.3 bits of entropy. Same
mechanism, different reward landscape.

`../figures/fig_generality.pdf` shows all five masks together, three learned from partners and
two from the environment.

## Limitation

One run, one seed. This is a demonstration that the mechanism is reward-source-agnostic, not a
measured experiment: there is no ablation, no seed sweep and no error bar here. The social
results in `results/exp1_ablation/` carry that weight.
