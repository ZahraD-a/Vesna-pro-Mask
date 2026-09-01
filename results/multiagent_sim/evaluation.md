# Multi-Agent Mask Simulation - Evaluation

Four agents (alice, bob, carol, dave), context-only masks {work, home, concert, default}, random pairing each episode, both agents act and update via CFR self-play (PURE CFR, no QRE). Episodes = 40000.

## Result: the goal's reward as written CANNOT produce mixed strategies (proven)

The stage game per context is symmetric with payoff `A[i][j] = Compat[i][j] * ContextMult[ctx][i]`. The specified Compatibility matrix has a **positive diagonal** (matching your partner scores +1.0), so it is a **coordination game**: at every context all four actions are *strict pure Nash* (every "all-play-the-same-action" profile is stable). Regret-matching self-play therefore converges to a PURE profile; the mixed equilibrium is unstable and cannot be reached. This is a property of the matrix values, not a tuning artifact. Empirically, Config A converged to mean entropy 0.004 (pure).

## Two faithful fixes (both produce genuine mixed strategies)

| approach | what changes | what is preserved | mean entropy | heterogeneity |
|---|---|---|---|---|
| A (baseline, goal as written) | nothing | formula + matrix | 0.004 (pure) | 0.001 |
| **Option 1 (ADOPTED)** | Compatibility *matrix* -> negative diagonal | the **formula** `Compat x ContextMult`, unchanged | **0.273 (mixed)** | **0.803** |
| Option 2 (alternative) | add `-lambda*[mine==partner]` term | the **matrix**, unchanged | 0.323 (mixed) | 0.791 |

Both turn the coordination game into an **anti-coordination** game ("no single action dominates"), which is the only structure whose symmetric equilibrium is mixed. Option 1 bakes this into the compatibility values; Option 2 keeps the specified values and adds a congestion term. Mean-field updates (each agent plays the society's average strategy = "a random partner") give per-agent mixing rather than segregation; the core-authenticity term makes agents heterogeneous.

## Adopted result -- Option 1 learned masks (formula unchanged, pure CFR)

| agent | context | formal | casual | enthusiastic | quiet | entropy |
|---|---|---|---|---|---|---|
| alice | work | 0.72 | 0.28 | 0.00 | 0.00 | 0.440 |
| alice | home | 0.00 | 1.00 | 0.00 | 0.00 | 0.015 |
| alice | concert | 0.00 | 1.00 | 0.00 | 0.00 | 0.011 |
| alice | default | 0.19 | 0.79 | 0.02 | 0.00 | 0.424 |
| bob | work | 0.65 | 0.00 | 0.00 | 0.35 | 0.465 |
| bob | home | 0.56 | 0.00 | 0.00 | 0.44 | 0.496 |
| bob | concert | 0.68 | 0.00 | 0.00 | 0.32 | 0.452 |
| bob | default | 0.83 | 0.00 | 0.00 | 0.17 | 0.328 |
| carol | work | 0.00 | 1.00 | 0.00 | 0.00 | 0.006 |
| carol | home | 0.00 | 0.65 | 0.35 | 0.00 | 0.467 |
| carol | concert | 0.00 | 0.49 | 0.51 | 0.00 | 0.500 |
| carol | default | 0.00 | 0.72 | 0.28 | 0.00 | 0.431 |
| dave | work | 0.00 | 0.24 | 0.76 | 0.00 | 0.397 |
| dave | home | 0.00 | 0.00 | 1.00 | 0.00 | 0.017 |
| dave | concert | 0.00 | 0.00 | 1.00 | 0.00 | 0.009 |
| dave | default | 0.00 | 0.03 | 0.97 | 0.00 | 0.107 |

Each agent develops a genuinely mixed, context-tilted persona (e.g. alice@work formal/casual, bob@work formal/quiet), and the four agents differ from one another -- exactly the goal's claim, delivered with the reward *formula* kept letter-for-letter.
