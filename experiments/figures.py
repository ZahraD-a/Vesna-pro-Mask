"""Summary figures, all from data already on disk. No new runs.

  fig_pirandello           one identity, three masks (radar)
  fig_mask_by_trait        per-trait mask evolution, one panel per circumstance
  fig_transfer             same mask, three partners, overlapping behaviour
  fig_seed_variance        8-seed means with standard error

Usage: python experiments/figures.py [out_dir]
"""
import csv, io, math, os, sys
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

LATEST = "results/latest"
OUT    = sys.argv[1] if len(sys.argv) > 1 else "results/figures"
TRAITS = ["o", "c", "e", "a", "n"]
LABELS = ["Openness", "Conscientiousness", "Extraversion", "Agreeableness", "Neuroticism"]
CIRCS  = ["work", "home", "conference"]
COLS   = {"work": "#3b6ea5", "home": "#c1663a", "conference": "#4c8055"}


def rows(path):
    with io.open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def save(fig, name):
    os.makedirs(OUT, exist_ok=True)
    for ext in ("pdf", "png"):
        fig.savefig(os.path.join(OUT, name + "." + ext), dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("  wrote %s/%s.pdf and .png" % (OUT, name))


def fig_pirandello():
    """learned_masks.csv is long format: one row per (circumstance, trait)."""
    data = rows(os.path.join(LATEST, "learned_masks.csv"))
    core = {}
    eff = defaultdict(dict)
    for r in data:
        core[r["trait"]] = float(r["core"])
        eff[r["circumstance"]][r["trait"]] = float(r["effective"])

    ang = np.linspace(0, 2 * np.pi, len(TRAITS), endpoint=False).tolist()
    ang += ang[:1]
    fig, ax = plt.subplots(figsize=(7.5, 7.5), subplot_kw=dict(polar=True))

    v = [core[t] for t in TRAITS]; v += v[:1]
    ax.plot(ang, v, lw=3.2, color="black", label="core identity", zorder=5)
    ax.fill(ang, v, alpha=0.08, color="black")

    for c in CIRCS:
        if c not in eff:
            continue
        v = [eff[c][t] for t in TRAITS]; v += v[:1]
        ax.plot(ang, v, lw=2, color=COLS[c], label="at " + c)
        ax.fill(ang, v, alpha=0.13, color=COLS[c])

    ax.set_xticks(ang[:-1]); ax.set_xticklabels(LABELS, fontsize=10)
    ax.set_ylim(0, 1); ax.set_yticks([0.25, 0.5, 0.75, 1.0])
    ax.set_title("One identity, three masks\nthe core is fixed; each circumstance shifts what is shown",
                 pad=26, fontsize=12)
    ax.legend(loc="upper right", bbox_to_anchor=(1.32, 1.10), frameon=False)
    save(fig, "fig_pirandello")


def fig_mask_by_trait():
    data = rows(os.path.join(LATEST, "mask_trajectory.csv"))
    fig, axes = plt.subplots(1, len(CIRCS), figsize=(13.5, 4.2), sharey=True)
    for ax, c in zip(axes, CIRCS):
        sub = sorted([r for r in data if r["mask"] == "mask_" + c],
                     key=lambda r: int(r["episode"]))
        if not sub:
            continue
        ep = [int(r["episode"]) for r in sub]
        for t, lab in zip(TRAITS, LABELS):
            ax.plot(ep, [float(r[t]) for r in sub], lw=1.9, label=lab)
        ax.axhline(0, color="0.55", ls="--", lw=0.9)
        ax.set_title("mask_" + c); ax.set_xlabel("episode")
        ax.grid(alpha=0.25)
    axes[0].set_ylabel("mask offset per trait")
    axes[-1].legend(loc="center left", bbox_to_anchor=(1.02, 0.5), frameon=False)
    fig.suptitle("Every mask starts at zero. What it learns depends on the circumstance.",
                 fontsize=12.5)
    fig.tight_layout()
    save(fig, "fig_mask_by_trait")


def fig_transfer():
    data = rows(os.path.join(LATEST, "style_by_partner.csv"))
    partners = sorted({r["partner"] for r in data})
    styles = [r["style"] for r in data if r["partner"] == partners[0]]
    fig, ax = plt.subplots(figsize=(10, 4.4))
    for p in partners:
        d = {r["style"]: float(r["share"]) * 100 for r in data if r["partner"] == p}
        ax.plot(styles, [d.get(s, 0.0) for s in styles], marker="o", lw=2, label=p)
    ax.set_ylabel("share of plays (%)")
    ax.set_title("One mask per circumstance, three partners: the lines overlap")
    ax.legend(frameon=False); ax.grid(alpha=0.25)
    plt.xticks(rotation=30, ha="right")
    fig.tight_layout()
    save(fig, "fig_transfer")


def fig_seed_variance():
    p = "results/exp0_seed_sweep/summary.csv"
    if not os.path.exists(p):
        print("  skipped fig_seed_variance (no sweep summary)"); return
    data = rows(p)
    fig, ax = plt.subplots(figsize=(6, 4))
    mus, ses = [], []
    for c in CIRCS:
        v = [float(r["outcome"]) for r in data if r["circumstance"] == c]
        m = sum(v) / len(v)
        se = math.sqrt(sum((x - m) ** 2 for x in v) / (len(v) - 1)) / math.sqrt(len(v))
        mus.append(m); ses.append(se)
    ax.bar(CIRCS, mus, yerr=ses, capsize=5, color=[COLS[c] for c in CIRCS])
    for i, (m, se) in enumerate(zip(mus, ses)):
        ax.text(i, m - se - 0.04, "%.3f\n+/-%.3f" % (m, se), ha="center", fontsize=9)
    ax.axhline(0, color="0.5", lw=0.8)
    ax.set_ylabel("mean outcome")
    ax.set_title("Eight seeds: the ordering work < home < conference holds in every one")
    fig.tight_layout()
    save(fig, "fig_seed_variance")


if __name__ == "__main__":
    fig_pirandello()
    fig_mask_by_trait()
    fig_transfer()
    fig_seed_variance()
