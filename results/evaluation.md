# Vesna-Pro-Mask — Training Evaluation (CFR + bounded-rational masks)

The agent learns, per context, a response policy using **vanilla CFR (regret matching)**, then
plays it as a **bounded-rational quantal response** (logit QRE, temperature `tau`) and projects the
resulting per-context policy into personality space as a **mask**.

**Algorithm.** Each context (work / home / concert / default) is one information set with four
actions {formal, casual, enthusiastic, reserved}. Per context we keep cumulative regret `R(a)` and
cumulative strategy `S(a)`. Each visit:

1. advantages by regret matching: `adv(a) = R(a) / visits` (converges to `u(a) - u(best) <= 0`);
2. **played policy** `pi(a) = softmax(adv(a) / tau)` — the bounded-rational (human-like) response;
3. play `a ~ pi`;
4. full-feedback utilities `u(a) = base(a) + shaping(context, a)` for **every** action
   (`HelpScenarioConfig.utility` — the single reward model shared with the environment);
5. `v = sum_a sigma_RM(a) u(a)`; accumulate `R(a) += u(a) - v` and `S(a) += pi(a)`.

The learning (regret matching, step 5) is untouched vanilla CFR. The temperature `tau` only controls
how sharply the learned policy is **played**: `tau -> 0` recovers the pure best response (argmax,
~100% one action); `tau > 0` keeps secondary actions alive, giving a nuanced, still context-tilted
distribution (McKelvey & Palfrey 1995, logit QRE — the standard model of human choice).

The **output** is the played policy per context, saved to `average_strategy.csv`. The **mask** is a
readout: `mask(ctx) = sum_a (pi(a) - 1/4) * OCEAN(a)`.

**Config:** 150 episodes, 10 interactions/context/episode, fixed seed 0, `policy_temperature = 0.15`.

## 1. Correctness — CFR ranks the actions correctly; QRE tilts toward the best one
Utilities `u = base + shaping` per context (base: formal .3, casual .5, enthusiastic .6, reserved .2):

| Context | formal | casual | enthusiastic | reserved | argmax | played policy (tau=0.15) |
|---------|--------|--------|--------------|----------|--------|--------------------------|
| work    | **0.6**| 0.4    | 0.4          | 0.3      | formal | formal .60, casual .16, enth .16, reserved .08 |
| home    | 0.1    | **0.8**| 0.7          | 0.1      | casual | casual .65, enth .34, formal .01, reserved .01 |
| concert | 0.0    | 0.6    | **0.9**      | 0.0      | enthusiastic | enth .88, casual .12 |
| default | 0.3    | 0.5    | **0.6**      | 0.2      | enthusiastic | enth .58, casual .30, formal .08, reserved .04 |

The policy peaks at the CFR-optimal action in every context (correctness check), but keeps
context-appropriate secondary mass instead of collapsing to 100% — human-like, not robotic.
Setting `policy_temperature: 0` reproduces the pure best response (formal/casual/enth ~0.999).
(See `average_strategy.csv`.)

## 2. Masks start at zero and become the per-context personas
`mask_norms.csv` row 0 = all masks at 0.000; they grow as CFR solves each info set and the QRE
policy is projected into trait space.

Final masks (readout of the played policy, clipped to +/-0.5):

| Mask    | Persona (dominant traits)                    | leans toward |
|---------|----------------------------------------------|--------------|
| work    | +conscientiousness, -extraversion, -openness | formal       |
| home    | +extraversion, +agreeableness, -conscientiousness | casual  |
| concert | +extraversion, +openness, -conscientiousness | enthusiastic |
| default | +extraversion, +openness (= concert-like)    | enthusiastic |

## 3. Same agent, different (nuanced) behaviour per context
One frozen core identity, four context-specific policies. The agent is *mostly* formal at work but
still occasionally casual/enthusiastic; *mostly* casual at home with a strong enthusiastic streak;
*mostly* enthusiastic at concert. Different behaviour per context, without collapsing to a single
robotic response.

## Multi-agent (`results/multiagent/`) — the real game
In `SelfPlayCFR`, two personality agents interact and each reward depends on the partner
(`SocialGame`: context-fit + rapport + authenticity). With the same QRE read-out:

- **Heterogeneity.** Alice (warm, agreeable) and Bob (reserved, conscientious) converge to
  *different* personas from the *same* contexts: Alice work {casual .50, enth .42}, Bob work
  {formal .92, reserved .08}. (`equilibrium_strategy.csv`)
- **Partner-dependence.** The *same* Alice behaves differently with different partners: work with
  Bob -> casual 50%, with Cara (exuberant extravert) -> enthusiastic 58%. This coupling is why it
  is a game and not a bandit. (`partner_dependence.csv`)

## Artifacts
- `average_strategy.csv` — played (QRE) policy per context.
- `mask_norms.csv` — per-episode mask norms (learning curves; row 0 = zero start).
- `training_log.txt` — full per-decision trace.
- `multiagent/` — self-play equilibrium strategies, masks, convergence, partner-dependence.

## Honest scope note
Single-agent, this is the **normal-form case of CFR**, where CFR reduces to regret matching
(Hart & Mas-Colell 2000): no opponent, no hidden information, so "convergence" means convergence to
the best response — which is *why* pure regret matching collapses to ~100% one action. Two
contributions make the behaviour non-trivial: (1) a **bounded-rational (logit-QRE) read-out** that
turns the hard best response into a human-like, tunable, still context-tilted policy; and (2) the
**multi-agent self-play**, where the reward genuinely couples the agents, so the per-context masks
depend on *both* the context and the partner.
