"""
Plot the multi-agent self-play CFR results (results/multiagent/).

  convergence.csv           -> plot_convergence.png       (regret -> 0: the CCE guarantee)
  equilibrium_strategy.csv  -> plot_personas_heatmap.png  (heterogeneity: Alice vs Bob)
  partner_dependence.csv    -> plot_partner_dependence.png (same Alice, different partner)

Run from the project root:  python scripts/plot_multiagent.py
"""
import csv, os
from collections import defaultdict
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MA = os.path.join(ROOT, "results", "multiagent")
ACTIONS = ["formal", "casual", "enthusiastic", "reserved"]
ACT_COLOR = {"formal": "#4C72B0", "casual": "#55A868", "enthusiastic": "#DD8452", "reserved": "#937860"}

# ---------------------------------------------------------------- 1. convergence
it, reg = [], []
with open(os.path.join(MA, "convergence.csv")) as f:
    for row in csv.DictReader(f):
        it.append(int(row["iter"])); reg.append(float(row["avg_external_regret"]))
plt.figure(figsize=(8, 5))
plt.loglog(it, reg, color="#4C72B0", linewidth=2)
plt.title("Self-play CFR converges: average external regret → 0")
plt.xlabel("iteration"); plt.ylabel("average external regret")
plt.grid(alpha=0.3, which="both")
plt.tight_layout(); plt.savefig(os.path.join(MA, "plot_convergence.png"), dpi=130); plt.close()

# ---------------------------------------------------------------- 2. personas heatmap
rows, mat = [], []
with open(os.path.join(MA, "equilibrium_strategy.csv")) as f:
    for row in csv.DictReader(f):
        rows.append(f"{row['player']} @ {row['context']}")
        mat.append([float(row[a]) for a in ACTIONS])
mat = np.array(mat)
plt.figure(figsize=(7, 0.6 * len(rows) + 1.5))
plt.imshow(mat, cmap="viridis", aspect="auto", vmin=0, vmax=1)
plt.xticks(range(len(ACTIONS)), ACTIONS)
plt.yticks(range(len(rows)), rows)
plt.colorbar(label="equilibrium probability")
for i in range(mat.shape[0]):
    for j in range(mat.shape[1]):
        if mat[i, j] > 0.02:
            plt.text(j, i, f"{mat[i,j]:.2f}", ha="center", va="center",
                     color="white" if mat[i, j] < 0.6 else "black", fontsize=8)
plt.title("Heterogeneous equilibria:\ndifferent personalities → different personas")
plt.tight_layout(); plt.savefig(os.path.join(MA, "plot_personas_heatmap.png"), dpi=130); plt.close()

# ---------------------------------------------------------------- 3. partner dependence
data = defaultdict(dict)  # context -> partner -> [probs]
contexts = []
with open(os.path.join(MA, "partner_dependence.csv")) as f:
    for row in csv.DictReader(f):
        ctx, partner = row["context"], row["partner"]
        if ctx not in contexts: contexts.append(ctx)
        data[ctx][partner] = [float(row[a]) for a in ACTIONS]
partners = ["bob", "cara"]
fig, axes = plt.subplots(1, len(contexts), figsize=(4 * len(contexts), 4.2), sharey=True)
if len(contexts) == 1: axes = [axes]
for ax, ctx in zip(axes, contexts):
    x = np.arange(len(partners)); bottom = np.zeros(len(partners))
    for k, a in enumerate(ACTIONS):
        vals = np.array([data[ctx][p][k] * 100 for p in partners])
        ax.bar(x, vals, bottom=bottom, color=ACT_COLOR[a], label=a)
        bottom += vals
    ax.set_title(f"context: {ctx}")
    ax.set_xticks(x); ax.set_xticklabels([f"Alice\nwith {p.capitalize()}" for p in partners])
    ax.set_ylim(0, 100)
axes[0].set_ylabel("Alice's response mix (%)")
axes[-1].legend(title="response", bbox_to_anchor=(1.02, 1), loc="upper left")
fig.suptitle("Partner-dependence: the same agent wears a different persona per partner", y=1.02)
plt.tight_layout(); plt.savefig(os.path.join(MA, "plot_partner_dependence.png"), dpi=130, bbox_inches="tight"); plt.close()

print("Wrote 3 plots to", MA)
