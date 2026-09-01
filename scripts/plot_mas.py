#!/usr/bin/env python3
"""
Plots for the four-agent mask experiment (results/mas/ -> results/mas/*.png).

    python scripts/plot_mas.py

Four figures, one per claim the experiment has to support:

  1. mask_trajectory   -- the masks start at zero and move apart. If they stayed
                          flat, nothing was learned; if they all moved together,
                          the circumstance is not doing any work.
  2. entropy           -- entropy per circumstance stays well above zero. This is
                          the collapse test: 0 bits means one plan always.
  3. style_shift       -- which plans gained and lost share between the first and
                          last fifth of the run, per circumstance.
  4. partner_mix       -- the control. Masks are indexed by circumstance and by
                          nothing else, so the style mix should come out roughly
                          the SAME for every partner. Flat bars here are the
                          scalability property made visible: one mask covers a
                          whole group instead of one mask per agent. If these
                          bars ever diverge, something is leaking partner
                          identity into selection.
"""

import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

RESULTS = Path("results/mas")
# Outputs use the OCEAN initials -- five full trait names side by side is unreadable.
TRAITS = ["O", "C", "E", "A", "N"]
TRAIT_NAMES = {"O": "O  openness", "C": "C  conscientiousness", "E": "E  extraversion",
               "A": "A  agreeableness", "N": "N  neuroticism"}


def _save(fig, name, top=1.0):
    out = RESULTS / name
    fig.tight_layout(rect=(0, 0, 1, top))
    fig.savefig(out, dpi=140)
    plt.close(fig)
    print(f"wrote {out}")


def mask_trajectory():
    df = pd.read_csv(RESULTS / "mask_trajectory.csv")
    masks = sorted(df["mask"].unique())

    fig, axes = plt.subplots(1, len(masks), figsize=(4.2 * len(masks), 3.6), sharey=True)
    if len(masks) == 1:
        axes = [axes]
    for ax, mask in zip(axes, masks):
        sub = df[df["mask"] == mask]
        for t in TRAITS:
            ax.plot(sub["episode"], sub[t], label=TRAIT_NAMES[t], linewidth=1.6)
        ax.axhline(0, color="0.6", linewidth=0.8)
        ax.set_title(mask)
        ax.set_xlabel("episode")
    axes[0].set_ylabel("mask offset applied to the core trait")
    axes[-1].legend(fontsize=7, loc="best")
    fig.suptitle("Every mask starts at zero. Where they end up is learned.")
    _save(fig, "plot_mask_trajectory.png", top=0.90)


def entropy():
    df = pd.read_csv(RESULTS / "episode_log.csv")
    fig, ax = plt.subplots(figsize=(7.5, 3.8))
    for c in ["work", "home", "conference"]:
        col = f"entropy_{c}"
        if col in df:
            ax.plot(df["episode"], df[col].rolling(5, min_periods=1).mean(), label=c, linewidth=1.7)
    ax.axhline(0, color="crimson", linestyle="--", linewidth=1,
               label="0 bits = collapsed to one plan")
    ax.set_xlabel("episode")
    ax.set_ylabel("entropy of the plan choice (bits)")
    ax.set_title("The policy stays mixed inside every circumstance")
    ax.legend(fontsize=8)
    _save(fig, "plot_entropy.png")


def style_shift():
    df = pd.read_csv(RESULTS / "style_shift.csv")
    circs = [c for c in ["work", "home", "conference", "default"] if c in set(df["circumstance"])]

    fig, axes = plt.subplots(1, len(circs), figsize=(4.6 * len(circs), 4.4), sharex=True)
    if len(circs) == 1:
        axes = [axes]
    for ax, c in zip(axes, circs):
        sub = df[df["circumstance"] == c].sort_values("shift")
        colours = ["#c0392b" if v < 0 else "#27ae60" for v in sub["shift"]]
        ax.barh(sub["style"], sub["shift"] * 100, color=colours)
        ax.axvline(0, color="0.4", linewidth=0.8)
        ax.set_title(c)
        ax.set_xlabel("share change, first fifth -> last fifth (pp)")
        ax.tick_params(axis="y", labelsize=8)
    fig.suptitle("What the mask changed about how the same goal gets achieved")
    _save(fig, "plot_style_shift.png", top=0.93)


def partner_mix():
    df = pd.read_csv(RESULTS / "style_by_partner.csv")
    pivot = df.pivot(index="style", columns="partner", values="share").fillna(0.0)
    pivot = pivot.loc[pivot.max(axis=1) > 0.02]

    fig, ax = plt.subplots(figsize=(7.5, 4.4))
    pivot.plot(kind="barh", ax=ax, width=0.8)
    ax.set_xlabel("share of interactions with that partner")
    ax.set_ylabel("")
    ax.set_title("Control: circumstance masks are deliberately partner-blind\n"
                 "(bars should match across partners -- that is what makes them scale)",
                 fontsize=10)
    ax.legend(fontsize=8, title="partner")
    _save(fig, "plot_partner_mix.png")


def main():
    if not RESULTS.exists():
        sys.exit(f"{RESULTS} not found -- run `gradlew run` first")
    for fn in (mask_trajectory, entropy, style_shift, partner_mix):
        try:
            fn()
        except FileNotFoundError as e:
            print(f"skipped {fn.__name__}: {e}")


if __name__ == "__main__":
    main()
