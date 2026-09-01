# Multi-Agent Social Game — Self-Play CFR (AAMAS results)

Two or more personality agents meet in a context and simultaneously choose a response style. Each
agent's reward couples to its partner, so this is a genuine **game**, not a bandit — which is where
CFR does real work. Each agent runs regret matching (one info set per context) and plays a
**bounded-rational quantal response** (logit QRE, temperature `tau`, default 0.15). This document
reports the equilibrium behaviour and the quantitative masking analysis.

## Reward model (`SocialGame`)
`reward_i = w_context * contextFit(ctx, own) + w_rapport * rapport(own, other) + w_authentic *
authenticity(core_i, own)`. Defaults `w_context=1.0, w_rapport=0.5, w_authentic=1.8`. The rapport
term couples the two agents (makes it a game); the authenticity term makes equilibria depend on the
agent's frozen OCEAN core.

## 1. Convergence
`convergence.csv` / `plot_convergence.png`: average external regret -> 0 (the no-regret guarantee,
Hart & Mas-Colell 2000). Learning is untouched by the QRE read-out.

## 2. Heterogeneity — different cores -> different personas
`equilibrium_strategy.csv` / `plot_personas_heatmap.png`. Same three contexts, different agents:

| agent | work | home | concert |
|-------|------|------|---------|
| alice (warm, agreeable) | casual .50, enth .42 | casual .70, enth .30 | enth .86, casual .14 |
| bob (reserved, conscientious) | formal .92, reserved .08 | formal .58, reserved .38 | formal .56, reserved .36 |

## 3. Partner-dependence — same agent, different partner
`partner_dependence.csv` / `plot_partner_dependence.png`. Alice @ work: with Bob -> casual 50%,
with Cara (exuberant extravert) -> enthusiastic 58%. The coupling is why this is a game.

## 4. Masking analysis over a society (`MaskingAnalysis`, `./gradlew runMasking`)
A society of five diverse agents (alice, bob, cara, dan = anxious introvert, eve = disciplined,
driven) plays round-robin. We measure **masking effort** = total-variation distance between an
agent's equilibrium persona and its **authentic** persona (softmax over `authenticity(core, .)`,
i.e. who it would be caring only about staying true to itself), in [0, 1].

**4a. Who masks, and where** (`masking_effort.csv` / `plot_masking_effort.png`).
Rigid personalities mask most, and the strain is context-specific:

| agent | work | home | concert |
|-------|------|------|---------|
| alice | 0.07 | 0.27 | 0.45 |
| bob   | 0.38 | 0.08 | 0.10 |
| cara  | 0.10 | 0.14 | 0.36 |
| dan   | 0.42 | 0.56 | 0.61 |
| eve   | 0.37 | 0.64 | **0.78** |

Alice barely masks at work (her warm core already fits) but strains at a concert; eve (disciplined)
strains hardest at the expressive concert (0.78). The mask is now a *measured* quantity, not a label.

**4b. Masking grows with social pressure** (`social_pressure_sweep.csv` / `plot_social_pressure.png`).
Sweeping the rapport weight `w_rapport` from 0 to 1.5, mean masking effort rises monotonically
0.31 -> 0.52 and social sensitivity (persona shift across partners) rises 0.00 -> 0.26. More social
pressure -> more masking.

**4c. Coupling ablations** (`ablation.csv` / `plot_ablation.png`). Turning each reward term off
isolates what it causes:

| condition | partner-dependence | heterogeneity |
|-----------|--------------------|---------------|
| full            | 0.11 | 0.54 |
| no rapport      | **0.00** | 0.57 |
| no authenticity | 0.00 | **0.00** |

Without **rapport**, partner-dependence vanishes (masks stop depending on who you're with) but
agents still differ by personality. Without **authenticity**, heterogeneity also collapses — every
agent adopts the same persona. Each phenomenon is caused by exactly one coupling term.

## Artifacts
- `equilibrium_strategy.csv`, `partner_dependence.csv`, `convergence.csv` — core self-play results.
- `masking_effort.csv`, `social_pressure_sweep.csv`, `ablation.csv` — society-level masking study.
- `plot_convergence.png`, `plot_personas_heatmap.png`, `plot_partner_dependence.png`,
  `plot_masking_effort.png`, `plot_social_pressure.png`, `plot_ablation.png`.

## Why this is the AAMAS contribution
The single-agent case is a bandit (CFR collapses to argmax; QRE softens it into a human-like
policy). The multi-agent case is a genuine game whose equilibria produce **context- and
partner-dependent masks** from a frozen personality core, with the *amount* of masking measured,
shown to grow with social pressure, and causally attributed to specific reward couplings.
