# Theory — what is provable

This note states the theoretical guarantees behind the Vesna-Pro-Mask model, for the paper's
theory section. Two results are rigorous; a third is a clean design property.

## Setup / notation
- Agents `i = 1..n`, each with a fixed core personality `A_core^i in [-1,1]^5` (OCEAN).
- A finite set of **contexts** `C`. Each context `c` is an **information set**: the agent's
  observable state at a decision.
- A finite action set `A` (response styles). Each action `a` has an OCEAN signature `phi(a)`.
- A **mask** `M^i_c` is the personality overlay agent `i` wears in context `c`;
  effective personality `A_eff = clip(A_core + M, -1, 1)`.

Single-agent case: `reward(c,a)` depends only on own action.
Multi-agent case: `reward_i(c, a_i, a_{-i})` depends on all agents' actions (a general-sum game
per context).

## Theorem 1 — Regret matching converges (Hart & Mas-Colell 2000)
Each agent updates a per-context strategy by regret matching:
`sigma_c(a) = max(0, R_c(a)) / sum_b max(0, R_c(b))`, accumulating counterfactual regret
`R_c(a) += u_c(a) - sum_b sigma_c(b) u_c(b)`.

**Claim.** The cumulative external regret at each information set grows sublinearly:
`R_c^T <= Delta * sqrt(|A| * T)` (Δ = payoff range). Hence the **average strategy**
`bar sigma_c = (1/T) sum_t sigma_c^t` has vanishing regret, and:

- **Single-agent (full feedback):** `bar sigma_c` converges to a best response — i.e. puts all
  mass on `argmax_a u_c(a)`. *Empirically confirmed:* work→formal, home→casual,
  concert→enthusiastic, default→enthusiastic (see `results/average_strategy.csv`).
- **Multi-agent self-play:** all agents having no external regret implies the empirical
  distribution of joint play converges to a **coarse-correlated equilibrium (CCE)**.
  *Empirically confirmed:* average external regret 0.91 → 2.3e-5 over 50k iters
  (`results/multiagent/convergence.csv`). For the 2-player zero-sum specialisation this
  sharpens to convergence to a **Nash equilibrium**.

Reference: S. Hart & A. Mas-Colell, "A Simple Adaptive Procedure Leading to Correlated
Equilibrium," *Econometrica* 68(5), 2000. (The regret-matching / no-regret ⇒ CCE result.)

## Theorem 2 — Identity preservation (by construction)
Each mask trait is clipped to `[-delta, +delta]`. Therefore for every context and every stage of
learning:

`|| A_eff - A_core ||_inf = || M ||_inf <= delta.`

**Interpretation.** The agent adapts its expressed behaviour per context and per partner, yet is
*provably* never more than `delta` from its true identity in any trait — a bounded-drift guarantee
on personality. The core is frozen (never updated by CFR); only masks move. This separates *who
the agent is* (invariant) from *how it presents* (learned, bounded).

## Corollary — the mask is the equilibrium policy in personality space
The mask is defined as the projection of the converged average strategy onto trait space:
`M_c = sum_a (bar sigma_c(a) - 1/|A|) * phi(a)`. Since `bar sigma_c` converges (Theorem 1), each
mask converges to a well-defined equilibrium persona. Thus "learned persona" = "equilibrium
strategy, read out as a Big-Five vector."

## Scope / honesty
- The single-agent task is a contextual decision problem where CFR reduces to regret matching;
  CFR's *game-theoretic* content (CCE/Nash) only appears in the multi-agent game.
- Convergence rates are the standard regret-matching bounds; we demonstrate them empirically
  rather than re-deriving them.
- CCE (not Nash) is the guarantee for the general-sum multi-agent game; Nash requires the
  zero-sum specialisation.
