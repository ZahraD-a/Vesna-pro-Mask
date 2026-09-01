# Results

`latest/` is the output of the most recent `./gradlew run`. It is overwritten
each run; copy it into `archive/` before a run you want to keep.

| file | contents |
|---|---|
| `report.txt` | end-of-run summary: learned masks, reward breakdown, style shift, transfer |
| `learned_masks.csv` | final mask vector per circumstance |
| `mask_trajectory.csv` | mask vector per episode -- the learning curve |
| `episode_log.csv` | per-episode reward and policy entropy |
| `reward_components.csv` | outcome / authenticity / cost split per circumstance |
| `style_shift.csv` | plan mix, first 24 episodes vs last 24 |
| `style_by_partner.csv` | plan mix per partner -- the transfer check |

## archive/

Kept for the range ablation. All four are 120-episode runs of the same
scenario; only the selector and the trait range differ.

| run | selector | traits | mean circumstance differentiation |
|---|---|---|---|
| `1_truncation_bug` | broken roulette | all [0,1] | 11.3% |
| `2_selector_fixed` | fixed | all [0,1] | 11.7% |
| `3_signed_personality` | fixed | personality [-1,1] | 34.9% |
| `../latest` | fixed | personality [0,1], annotations [-1,1] | 34.6% |

`latest` is the shipped configuration: it matches the original VEsNA-Pro
personality range and reaches the same differentiation as widening it.
