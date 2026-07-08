# Multi-Agent Social Game — Self-Play CFR (AAMAS results)

Two (or more) personality agents meet in a context and simultaneously choose a response
style. Each agent's reward couples all three social forces:

```
reward_i(context, own, other) =  1.0 * contextFit(context, own)    // is it appropriate here?
                              +  0.5 * rapport(own, other)          // does it click with the partner?  (COUPLING)
                              +  1.8 * authenticity(core_i, own)    // is it true to who I am?
```

Each agent learns by **regret matching** (CFR), one information set per context, in **self-play**.
This is a genuine general-sum game: the rapport term makes each agent's best response depend on
the other's — so CFR is justified (not a bandit), and its equilibrium theory applies.

Run: `./gradlew runGame` (50000 iterations). Plots: `python scripts/plot_multiagent.py`.

## Result 1 — Convergence to equilibrium  (`plot_convergence.png`)
Average external regret falls from **0.91 → 2.3e-5** over 50k iterations (≈ O(1/√T)).
No external regret ⇒ the empirical average joint strategy is a **coarse-correlated equilibrium**
(Hart & Mas-Colell 2000). This is the guarantee that makes CFR the right tool here.

## Result 2 — Heterogeneous equilibria  (`plot_personas_heatmap.png`)
Different core personalities converge to **different** context-personas:

| Agent | core | work | home | concert |
|-------|------|------|------|---------|
| Alice | warm, open, agreeable | casual | casual | enthusiastic |
| Bob   | reserved, conscientious, introverted | formal | formal | formal |

Alice adapts expressively across contexts; Bob stays professional everywhere — his conscientious,
introverted core makes expressive personas too *inauthentic* to adopt, even under context and
social pressure. Personality drives how much an agent adapts.

## Result 3 — Partner-dependence / the chameleon effect  (`plot_partner_dependence.png`)
The **same** Alice learns a **different** persona depending on **who** she interacts with:

| context | Alice with Bob (introvert) | Alice with Cara (exuberant) |
|---------|----------------------------|-----------------------------|
| **work**| **casual**                 | **enthusiastic**            |
| home    | casual                     | casual                      |
| concert | enthusiastic               | enthusiastic                |

At work, Alice tones down to *casual* with reserved Bob but ramps up to *enthusiastic* with
exuberant Cara. This is the multi-agent signature: an agent's learned mask is a function of its
social partner, not just the context — precisely what a single-agent bandit cannot capture.

## Artifacts
- `convergence.csv`, `equilibrium_strategy.csv`, `equilibrium_masks.csv`, `partner_dependence.csv`
- `plot_convergence.png`, `plot_personas_heatmap.png`, `plot_partner_dependence.png`
- Code: `src/agt/vesna/SocialGame.java` (reward model), `src/agt/vesna/SelfPlayCFR.java` (driver).
