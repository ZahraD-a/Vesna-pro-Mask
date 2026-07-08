# Vesna-Pro-Mask — Training Evaluation

Run: 50 episodes, 3 contexts with a dedicated mask (work, home, concert) + 1 undefined
context (party) that falls back to the shared default mask. 10 interactions per context
per episode. Fixed seed 0 (reproducible).

Artifacts in this folder:
- `training_log.txt` — full per-decision console trace of the run.
- `mask_norms.csv` — one row per episode: `episode, total_reward, ||mask||` for each mask.
- `evaluation.md` — this report.

---

## 1. Masks start at zero and are learned

At startup every mask is the zero vector (no modification to Alice's core identity):

```
mask_default ||M||=0.0000
mask_work    ||M||=0.0000
mask_home    ||M||=0.0000
mask_concert ||M||=0.0000
```

They only change through CFR. Norm growth over the run (from `mask_norms.csv`):

| Mask     | episode 1 | episode 50 |
|----------|-----------|------------|
| default  | 0.0010    | 0.0390     |
| work     | 0.0005    | 0.0704     |
| home     | 0.0021    | 0.0730     |
| concert  | 0.0024    | 0.0892     |

Growth is monotonic — the learning is stable (no oscillation or collapse).

## 2. Each mask specialises in the correct direction

Final learned masks (trait deltas added on top of the frozen core personality):

| Mask     | Dominant learned shifts                     | ≈ Response style | Context reward favours |
|----------|---------------------------------------------|------------------|------------------------|
| work     | conscientiousness **+0.048**, extraversion −0.033, openness −0.034 | **formal**       | formal        |
| home     | extraversion **+0.064**, conscientiousness −0.030 | **casual**       | casual        |
| concert  | extraversion **+0.073**, openness +0.033, conscientiousness −0.038 | **enthusiastic** | enthusiastic  |
| default  | extraversion +0.032, openness +0.016 (from `party`) | enthusiastic/casual | no shaping (base reward only) |

Each context's mask moves toward the OCEAN profile of the response its reward function
rewards most. This is the core result: **one identity, different learned personas per context.**

## 3. Reward improves as masks steer behaviour

Mean total reward per episode (from `mask_norms.csv`):

| Episodes | Mean total reward |
|----------|-------------------|
| 1–10     | 13.07             |
| 41–50    | 14.35             |
| Δ        | **+1.28 (~+10%)** |

Reward rises because the learned masks bias action selection toward the context-appropriate
response, and the softmax temperature anneals from exploration toward exploitation over the run.
The signal is noisy (there is a ~30% "neutral" outcome per interaction and softmax exploration),
so the trend is gradual rather than sharp.

## 4. Default-mask fallback and routing

`party` has no dedicated mask, so it is routed to the shared default mask, which itself learns:

```
context 'work'    -> mask_work
context 'home'    -> mask_home
context 'concert' -> mask_concert
context 'party'   -> mask_default  (no dedicated mask -> learned default)
Undefined contexts trained onto the default mask: [party]
```

Because `party` has no reward shaping, its rewards are the raw base rewards, so the default mask
drifts toward the globally-highest-base-reward responses (enthusiastic/casual) — a sensible
"true self" fallback.

## 5. Notes / next steps

- Masks are still far below the `delta_threshold` (0.6) after 50 episodes (largest ≈ 0.089).
  The directions are correct; to make them cross the threshold for a stronger demo, raise
  `max_episodes` in `main.asl` or the CFR learning rate in `Temper.java`.
- `personality.json` writes a `masks` block that is never read back — write-only persistence.
  Every run genuinely starts from zero masks.
