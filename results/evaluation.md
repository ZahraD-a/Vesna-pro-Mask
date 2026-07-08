# Vesna-Pro-Mask — Training Evaluation (true vanilla CFR)

The agent learns, per context, an optimal response policy using **vanilla CFR (regret
matching)**, and projects each converged policy into personality space as a **mask**.

**Algorithm.** Each context (work / home / concert / default) is one information set with four
actions {formal, casual, enthusiastic, reserved}. Per context we keep cumulative regret
`R(a)` and cumulative strategy `S(a)`. Each visit:

1. current strategy by regret matching: `sigma = normalize(max(0, R))` (uniform if all <= 0);
2. play `a ~ sigma`;
3. full-feedback utilities `u(a) = base(a) + shaping(context, a)` for **every** action
   (`HelpScenarioConfig.utility` — the single reward model shared with the environment);
4. `v = sum_a sigma(a) u(a)`; accumulate `R(a) += u(a) - v` and `S(a) += sigma(a)`.

The **output** is the average strategy `S / sum(S)` (guaranteed to converge), saved to
`average_strategy.csv`. The **mask** is a readout: `mask(ctx) = sum_a (avgStrategy(a) - 1/4) * OCEAN(a)`.

**Config:** 150 episodes, 10 interactions/context/episode, fixed seed 0.

## 1. Correctness — CFR converges to the provably optimal action
Utilities `u = base + shaping` per context (base: formal .3, casual .5, enthusiastic .6,
reserved .2):

| Context | formal | casual | enthusiastic | reserved | argmax | CFR converged to |
|---------|--------|--------|--------------|----------|--------|------------------|
| work    | **0.6**| 0.4    | 0.4          | 0.3      | formal | **formal 0.9995** ✓ |
| home    | 0.1    | **0.8**| 0.7          | 0.1      | casual | **casual 0.9987** ✓ |
| concert | 0.0    | 0.6    | **0.9**      | 0.0      | enthusiastic | **enthusiastic 0.9993** ✓ |
| default | 0.3    | 0.5    | **0.6**      | 0.2      | enthusiastic | **enthusiastic 0.9992** ✓ |

The average strategy converges to the best response in every context — this is the correctness
check for the regret-matching implementation. (See `average_strategy.csv`.)

## 2. Masks start at zero and become the per-context personas
`mask_norms.csv` row 0 = all masks at 0.000; they jump to the converged persona once CFR solves
each info set (this game is simple, so convergence is essentially within the first episode).

Final masks (readout of the average strategy, clipped to +/-0.5):

| Mask    | Persona (dominant traits)                    | = optimal action |
|---------|----------------------------------------------|------------------|
| work    | +conscientiousness, -extraversion, -openness | formal           |
| home    | +extraversion, +agreeableness, -conscientiousness | casual      |
| concert | +extraversion, +openness, -conscientiousness | enthusiastic     |
| default | +extraversion, +openness (= concert)         | enthusiastic     |

## 3. Same agent, different behaviour per context
Action actually played once converged (last 30 episodes): work 100% formal, home 100% casual,
concert 100% enthusiastic, default 100% enthusiastic. One frozen core identity, four
context-specific policies.

## Artifacts
- `mask_norms.csv` — per-episode mask norms (learning curves; row 0 = zero start).
- `average_strategy.csv` — converged average strategy per context (the CFR output).
- `training_log.txt` — full per-decision trace.
- `plot_mask_evolution.png`, `plot_mask_personas.png`, `plot_action_by_context.png`.
- `baseline_50ep/` — earlier gentler run, kept for comparison.

## Honest scope note
This is the **single-decision (normal-form) case of CFR**, where CFR reduces exactly to regret
matching (Hart & Mas-Colell 2000). There is no opponent, no hidden information, no game tree —
so there are no reach probabilities, and "convergence to Nash" here means convergence to the
best response. The contribution is the *mapping* from regret-matched policies to interpretable
per-context personality masks, not solving a hard game.
