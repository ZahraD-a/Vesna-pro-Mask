"""
Plot Vesna-Pro-Mask training results.

Reads (from the project root):
  results/mask_norms.csv   - per-episode mask L2 norms  (learning curves)
  personality.json         - final learned mask trait vectors (personas)
  results/training_log.txt - per-decision trace          (action choices)

Writes PNGs into results/:
  plot_mask_evolution.png  - how each mask grows from 0
  plot_mask_personas.png   - the different learned persona per context
  plot_action_by_context.png - what the agent actually does per context (late run)

Run from the project root:  python scripts/plot_results.py
"""
import csv, json, re, os
from collections import defaultdict, Counter
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "results")

# Consistent, colourblind-friendly colour per context
CTX_COLOR = {"work": "#4C72B0", "home": "#DD8452", "concert": "#55A868", "default": "#8172B3"}
OCEAN = ["openness", "conscientiousness", "extraversion", "agreeableness", "neuroticism"]
ACTIONS = ["formal", "casual", "enthusiastic", "reserved"]

# ---------------------------------------------------------------- 1. evolution
episodes, norms = [], defaultdict(list)
with open(os.path.join(RES, "mask_norms.csv")) as f:
    reader = csv.DictReader(f)
    mask_cols = [c for c in reader.fieldnames if c.startswith("mask_")]
    for row in reader:
        episodes.append(int(row["episode"]))
        for c in mask_cols:
            norms[c].append(float(row[c]))

plt.figure(figsize=(8, 5))
for c in mask_cols:
    ctx = c.replace("mask_", "")
    plt.plot(episodes, norms[c], label=c, linewidth=2, color=CTX_COLOR.get(ctx, "#888"))
plt.axhline(0, color="#bbb", linewidth=0.8)
plt.title("Mask evolution: every mask starts at 0 and is learned by CFR")
plt.xlabel("Episode"); plt.ylabel("Mask magnitude  ||M||  (L2 norm)")
plt.legend(); plt.grid(alpha=0.25); plt.tight_layout()
plt.savefig(os.path.join(RES, "plot_mask_evolution.png"), dpi=130)
plt.close()

# ------------------------------------------------------------- 2. final personas
with open(os.path.join(ROOT, "personality.json")) as f:
    masks = json.load(f)["masks"]
contexts = ["work", "home", "concert", "default"]
plt.figure(figsize=(9, 5))
x = range(len(OCEAN))
w = 0.2
for i, ctx in enumerate(contexts):
    key = "mask_" + ctx
    vals = [masks.get(key, {}).get(t, 0.0) for t in OCEAN]
    plt.bar([xi + (i - 1.5) * w for xi in x], vals, width=w,
            label=ctx, color=CTX_COLOR[ctx])
plt.axhline(0, color="#333", linewidth=0.8)
plt.title("Same agent, different learned persona per context (final masks)")
plt.xticks(list(x), [t[:4].capitalize() for t in OCEAN])
plt.ylabel("Learned trait delta (added to frozen core)")
plt.legend(title="context"); plt.grid(axis="y", alpha=0.25); plt.tight_layout()
plt.savefig(os.path.join(RES, "plot_mask_personas.png"), dpi=130)
plt.close()

# --------------------------------------------------- 3. action choices per context
# Count how often each action is chosen per context, over the LAST 30 episodes,
# where the softmax temperature has annealed toward exploitation.
# Decisions appear BEFORE their "[EPISODE N] complete" marker, so we buffer per episode.
WINDOW = 30
per_ep = {}   # episode -> list of (action, ctx)
buf = []
done_re = re.compile(r"\[EPISODE (\d+)\] complete")
sel_re = re.compile(r"selected=(\w+) mask=mask_(\w+)")
with open(os.path.join(RES, "training_log.txt"), encoding="utf-8", errors="ignore") as f:
    for line in f:
        s = sel_re.search(line)
        if s:
            buf.append((s.group(1), s.group(2))); continue
        d = done_re.search(line)
        if d:
            per_ep[int(d.group(1))] = buf
            buf = []

max_ep = max(per_ep) if per_ep else 0
late_from = max(1, max_ep - WINDOW + 1)
counts = defaultdict(Counter)  # ctx -> Counter(action)
for ep, decisions in per_ep.items():
    if ep >= late_from:
        for action, ctx in decisions:
            counts[ctx][action] += 1

plot_ctx = [c for c in ["work", "home", "concert", "default"] if counts.get(c)]
plt.figure(figsize=(9, 5))
bottom = [0] * len(plot_ctx)
action_color = {"formal": "#4C72B0", "casual": "#55A868",
                "enthusiastic": "#DD8452", "reserved": "#937860"}
for action in ACTIONS:
    fracs = []
    for c in plot_ctx:
        tot = sum(counts[c].values()) or 1
        fracs.append(100.0 * counts[c][action] / tot)
    plt.bar(plot_ctx, fracs, bottom=bottom, label=action, color=action_color[action])
    bottom = [b + fr for b, fr in zip(bottom, fracs)]
plt.title(f"What the agent actually does per context (last {max_ep - late_from + 1} episodes)")
plt.ylabel("Share of responses chosen (%)"); plt.xlabel("context")
plt.legend(title="action", bbox_to_anchor=(1.02, 1), loc="upper left")
plt.tight_layout()
plt.savefig(os.path.join(RES, "plot_action_by_context.png"), dpi=130)
plt.close()

print("Wrote 3 plots to", RES)
for c in plot_ctx:
    tot = sum(counts[c].values())
    dist = {a: f"{100*counts[c][a]/tot:.0f}%" for a in ACTIONS}
    print(f"  {c:8s} (n={tot}): {dist}")
