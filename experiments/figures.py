"""Summary figures, all from data already on disk. No new runs.

  fig_pirandello           one identity, three masks (radar)
  fig_mask_by_trait        per-trait mask evolution, one panel per circumstance
  fig_transfer             same mask, three partners, overlapping behaviour
  fig_seed_variance        8-seed means with standard error
  fig_identity_vs_adaptation  core vs presented, a learner against a fixed agent
  fig_pirandellian         paper: core vs presented per circumstance, signed scale
  fig_identity             paper: the core as logged at fixed episodes of one run
  fig_bob_baseline         paper: Bob's fixed personality

Usage: python experiments/figures.py [out_dir]
"""
import csv, io, math, os, re, sys
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



def fig_two_scenarios():
    """The same three circumstances, learned twice: once from partners, once from the environment."""
    social = os.path.join(LATEST, "mask_trajectory.csv")
    nonsoc = "results/nonsocial/latest/mask_trajectory.csv"
    if not os.path.exists(nonsoc):
        print("  skipped fig_two_scenarios (no non-social run)"); return
    fig, axes = plt.subplots(2, len(CIRCS), figsize=(4.3 * len(CIRCS), 7.2), sharey=True, sharex=True)
    for row, (path, label) in enumerate([(social, "social: outcomes from partners"),
                                         (nonsoc, "non-social: outcomes from the environment")]):
        data = rows(path)
        for col, c in enumerate(CIRCS):
            ax = axes[row][col]
            sub = sorted([r for r in data if r["mask"] == "mask_" + c],
                         key=lambda r: int(r["episode"]))
            if not sub:
                ax.set_visible(False); continue
            ep = [int(r["episode"]) for r in sub]
            for t, lab in zip(TRAITS, LABELS):
                ax.plot(ep, [float(r[t]) for r in sub], lw=1.8, label=lab)
            ax.axhline(0, color="0.55", ls="--", lw=0.9)
            ax.grid(alpha=0.25)
            if row == 0:
                ax.set_title("mask_" + c, fontsize=12)
            if row == 1:
                ax.set_xlabel("episode")
        axes[row][0].set_ylabel(label + "\n\nmask offset per trait", fontsize=9)
    axes[0][-1].legend(loc="center left", bbox_to_anchor=(1.02, 0.5), frameon=False)
    fig.suptitle("The same three circumstances, learned twice. Same code, same masks, "
                 "different source of feedback.", fontsize=12.5)
    fig.tight_layout()
    save(fig, "fig_two_scenarios")
def _jcm_core(agent, path="vesna.jcm"):
    """The five OCEAN values an agent is given in the .jcm. Needed for agents with no wardrobe:
    their presented personality is their core by definition, so nothing about them is ever logged."""
    txt = io.open(path, encoding="utf-8").read()
    m = re.search(r"agent\s+%s\s*:.*?temper:\s*temper\((.*?)\)\s*$" % agent, txt, re.S | re.M)
    if not m:
        return None
    body = m.group(1)
    out = {}
    for t in TRAITS:
        v = re.search(r"\b%s\(([-0-9.]+)\)" % t, body)
        if v:
            out[t] = float(v.group(1))
    return out if len(out) == len(TRAITS) else None


def _seed_masks(lo=1, hi=10, paths=None):
    """effective[circumstance][trait] -> one value per run, and the core, identical in all.
    Reads the seed sweep unless paths names the learned_masks.csv files to use instead."""
    eff = defaultdict(lambda: defaultdict(list))
    core = {}
    seeds = []
    if paths is None:
        paths = ["experiments/_sweep/masks_seed%d.csv" % s for s in range(lo, hi + 1)]
    for s, path in enumerate(paths, 1):
        if not os.path.exists(path):
            continue
        seeds.append(s)
        for r in rows(path):
            if r["circumstance"] == "default":
                continue
            eff[r["circumstance"]][r["trait"]].append(float(r["effective"]))
            core[r["trait"]] = float(r["core"])
    return core, eff, seeds


def _mean_se(v):
    m = sum(v) / len(v)
    if len(v) < 2:
        return m, 0.0
    sd = math.sqrt(sum((x - m) ** 2 for x in v) / (len(v) - 1))
    return m, sd / math.sqrt(len(v))


def fig_identity_vs_adaptation(delta=0.5):
    """Core against presented personality, for a learner and for a fixed agent.

    Two rows, one property, two mechanisms. Alice wears a learned mask, so what she presents
    moves with the circumstance while her core does not. Bob has no wardrobe, so what he presents
    is his core everywhere. Both keep their identity: Bob because he never adapts, Alice because
    every mask she learns is bounded by delta.
    """
    core, eff, seeds = _seed_masks()
    if not seeds:
        print("  skipped fig_identity_vs_adaptation (no sweep data)")
        return
    bob = _jcm_core("bob")
    if bob is None:
        print("  skipped fig_identity_vs_adaptation (no bob temper in vesna.jcm)")
        return

    C_CORE, C_SHOWN = "#3b6ea5", "#b5372e"
    SHORT = ["O", "C", "E", "A", "N"]
    x = np.arange(len(TRAITS))
    w = 0.36

    fig, axes = plt.subplots(2, len(CIRCS), figsize=(4.5 * len(CIRCS), 7.6),
                             sharey=True, sharex=True)

    print("  Alice, presented personality, mean +/- se over %d seeds (core in brackets):" % len(seeds))
    for col, c in enumerate(CIRCS):
        ax = axes[0][col]
        stats = [_mean_se(eff[c][t]) for t in TRAITS]
        mus = [m for m, _ in stats]
        ses = [e for _, e in stats]
        cor = [core[t] for t in TRAITS]

        # What delta permits. Every red bar has to land inside this band, whatever it learns:
        # the bound of Corollary 3.11 drawn as a ceiling instead of asserted in the text.
        lo = [max(0.0, v - delta) for v in cor]
        hi = [min(1.0, v + delta) for v in cor]
        ax.bar(x, [h - l for l, h in zip(lo, hi)], bottom=lo, width=0.94,
               color="#dfe4ea", edgecolor="#9aa4b1", lw=0.7, alpha=0.55, zorder=0,
               label=r"reachable within $\delta$" if col == 0 else None)

        ax.bar(x - w / 2, cor, w, color=C_CORE, zorder=3,
               label="core identity" if col == 0 else None)
        ax.bar(x + w / 2, mus, w, yerr=ses, capsize=3, color=C_SHOWN, zorder=3,
               error_kw=dict(lw=1.1, ecolor="0.25"),
               label="presented (core + mask)" if col == 0 else None)

        # A value at a bound draws as a bar of no height, so label every one. Bold marks the
        # ones sitting exactly on the delta floor or on the [0,1] clip -- learning that wanted
        # to go further and was stopped.
        for xi, (m, e, l) in enumerate(zip(mus, ses, lo)):
            at_bound = abs(m - l) < 5e-4
            ax.text(xi + w / 2, m + e + 0.035, "%.2f" % m, ha="center", fontsize=7.6,
                    color="#7a1d16" if at_bound else "0.25",
                    fontweight="bold" if at_bound else "normal", zorder=4)
        ax.set_title("at " + c, fontsize=12)
        ax.grid(axis="y", alpha=0.25)
        ax.set_axisbelow(True)

        print("    %-11s" % c + "  ".join("%s %.3f+/-%.3f [%.2f]" % (t, m, e, k)
                                          for t, m, e, k in zip(SHORT, mus, ses, cor)))

    print("  Bob, fixed: presented == core in every circumstance, and in every seed:")
    print("    " + "  ".join("%s %.2f" % (sh, bob[t]) for sh, t in zip(SHORT, TRAITS)))

    for col, c in enumerate(CIRCS):
        ax = axes[1][col]
        v = [bob[t] for t in TRAITS]
        ax.bar(x - w / 2, v, w, color=C_CORE, zorder=3)
        ax.bar(x + w / 2, v, w, color=C_SHOWN, zorder=3)
        ax.grid(axis="y", alpha=0.25)
        ax.set_axisbelow(True)
        ax.set_xticks(x)
        ax.set_xticklabels(SHORT)
        ax.set_xlabel("O openness   C conscientiousness   E extraversion\n"
                      "A agreeableness   N neuroticism", fontsize=7.5)

    axes[0][0].set_ylabel("Alice\nlearns one mask per circumstance\n\ntrait value", fontsize=9.5)
    axes[1][0].set_ylabel("Bob\nno wardrobe, fixed personality\n\ntrait value", fontsize=9.5)
    axes[0][0].set_ylim(0, 1)

    handles, labels = axes[0][0].get_legend_handles_labels()
    order = [labels.index(l) for l in
             ["core identity", "presented (core + mask)", r"reachable within $\delta$"]
             if l in labels]
    fig.legend([handles[i] for i in order], [labels[i] for i in order],
               loc="upper center", bbox_to_anchor=(0.5, 0.945), ncol=3, frameon=False,
               fontsize=10)
    fig.suptitle("Identity is kept two ways: Bob never adapts, Alice adapts within a bound",
                 fontsize=13, y=0.995)
    fig.tight_layout(rect=[0, 0, 1, 0.9])
    save(fig, "fig_identity_vs_adaptation")


# ----------------------------------------------------------------- paper figures, section 4
# Traits are stored on the paper's signed [-1,+1] scale, so these three plot them as read.

P_CORE, P_SHOWN, P_BOB = "#0F766E", "#EA580C", "#475569"
P_PANEL, P_ERR = "#F1F5F9", "#334155"
SIGNED = [-1, -0.5, 0, 0.5, 1]


def _paper_axes(ax):
    ax.set_facecolor(P_PANEL)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color("#94A3B8")
    ax.grid(axis="y", color="white", lw=1.2)
    ax.set_axisbelow(True)
    ax.axhline(0, color="#1E293B", lw=0.9, zorder=2)
    ax.set_ylim(-1.08, 1.08)
    ax.set_yticks(SIGNED)
    ax.set_xticks(np.arange(len(TRAITS)))
    ax.set_xticklabels([t.upper() for t in TRAITS], fontweight="bold")
    ax.tick_params(length=0)


def _zero_stubs(ax, xs, vals, width, **kw):
    """A bar of value 0 has no height and reads as missing data; mark it on the baseline."""
    for xi, v in zip(xs, vals):
        if abs(v) < 0.01:
            ax.plot([xi - width / 2, xi + width / 2], [0, 0], lw=3.5, solid_capstyle="butt",
                    zorder=4, **kw)


def _save_paper(fig, name):
    os.makedirs(OUT, exist_ok=True)
    fig.savefig(os.path.join(OUT, name + ".pdf"), bbox_inches="tight")
    fig.savefig(os.path.join(OUT, name + ".png"), dpi=200, bbox_inches="tight")
    plt.close(fig)
    print("  wrote %s/%s.pdf and .png" % (OUT, name))


def fig_paper_pirandellian(paths=None, baseline=None):
    """Alice's core against what she presents, one panel per circumstance. Presented values are
    the mean over the runs read, with standard error; the core is fixed, so it has none.

    baseline names the learned_masks.csv of a run with mask_delta 0: the same agent with the mask
    unable to move, i.e. plain Pro-AgentSpeak(L). It is drawn first, as the reference the three
    masked panels are read against. Its presented personality is read from that run, not assumed."""
    core, eff, seeds = _seed_masks(paths=paths)
    if not seeds:
        print("  skipped fig_pirandellian (no sweep data)")
        return

    panels = []
    if baseline is not None and os.path.exists(baseline):
        _, beff, _ = _seed_masks(paths=[baseline])
        # With no mask every circumstance presents the same personality. Check rather than assume.
        flat = all(abs(beff[c][t][0] - beff[CIRCS[0]][t][0]) < 1e-9 for c in CIRCS for t in TRAITS)
        if not flat:
            print("  WARNING: baseline presents differently across circumstances")
        panels.append(("Alice without mask\n(Pro-AgentSpeak(L), any circumstance)",
                       [_mean_se(beff[CIRCS[0]][t]) for t in TRAITS], 1))
    for c in CIRCS:
        panels.append(("Alice with mask\nat " + c.capitalize(),
                       [_mean_se(eff[c][t]) for t in TRAITS], len(seeds)))

    x = np.arange(len(TRAITS))
    w = 0.38
    # A narrow empty column separates the reference panel from the three masked ones.
    ratios = [1, 0.12] + [1] * len(CIRCS) if baseline else [1] * len(CIRCS)
    fig, axs = plt.subplots(1, len(ratios), figsize=(7.2 if baseline else 7, 2.9), sharey=True,
                            gridspec_kw=dict(width_ratios=ratios))
    if baseline:
        axs[1].set_visible(False)
        axs = [axs[0]] + list(axs[2:])

    cor = [core[t] for t in TRAITS]
    print("  Pirandellian, mean +/- se over %d run(s):" % len(seeds))
    for ax, (title, stats, n) in zip(axs, panels):
        mus = [m for m, _ in stats]
        ses = [e for _, e in stats]
        ax.bar(x - w / 2 - 0.01, cor, w, color=P_CORE, zorder=3,
               label="Core (immutable identity)")
        _zero_stubs(ax, x - w / 2 - 0.01, cor, w, color=P_CORE)
        # One run has no spread to show, so draw no error bars rather than empty ones.
        err = dict(yerr=ses, capsize=1.8, error_kw=dict(lw=0.8, ecolor=P_ERR, capthick=0.8)) if n > 1 else {}
        ax.bar(x + w / 2 + 0.01, mus, w, color=P_SHOWN, zorder=3,
               label="Presented personality (core + worn mask)", **err)
        _zero_stubs(ax, x + w / 2 + 0.01, mus, w, color=P_SHOWN)
        _paper_axes(ax)
        ax.tick_params(labelsize=8)
        ax.set_title(title, fontsize=8.5)
        print("    %-26s" % title.replace("\n", " ")[:26] + "  ".join(
            "%s %+.2f(%+.2f)" % (t.upper(), m, k) for t, m, k in zip(TRAITS, mus, cor)))
    axs[0].set_ylabel("trait value", fontsize=9)
    h, l = axs[-1].get_legend_handles_labels()
    fig.legend(h, l, loc="upper center", bbox_to_anchor=(0.5, 1.02), ncol=2, frameon=False,
               fontsize=8.5)
    fig.tight_layout(rect=[0, 0, 1, 0.9])
    _save_paper(fig, "fig_pirandellian")


def fig_paper_identity(path=LATEST + "/core_samples.csv"):
    """Alice's core as logged at fixed episodes during one run. Every sample is a real read of
    the core the learner holds, not a copy of the configured value."""
    if not os.path.exists(path):
        print("  skipped fig_identity (no %s; rerun the MAS)" % path)
        return
    samples = rows(path)
    x = np.arange(len(TRAITS))
    n = len(samples)
    w = 0.8 / n
    alphas = np.linspace(0.4, 1.0, n)
    last = int(samples[-1]["episode"])
    fig, ax = plt.subplots(figsize=(7, 3.0))
    for i, s in enumerate(samples):
        ep = int(s["episode"])
        name = "episode %d" % ep + (" (start)" if ep == 0 else " (end)" if ep == last else "")
        v = [float(s[t]) for t in TRAITS]
        ax.bar(x - 0.4 + w * (i + 0.5), v, w * 0.92, color=P_CORE, alpha=alphas[i],
               zorder=3, label=name)
        _zero_stubs(ax, x - 0.4 + w * (i + 0.5), v, w * 0.92, color=P_CORE, alpha=alphas[i])
    _paper_axes(ax)
    ax.set_ylabel("trait value", fontsize=9)
    ax.set_title("Alice's core sampled across the run — identity does not move", fontsize=10)
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.1), ncol=n, frameon=False, fontsize=8)
    spread = max(max(float(s[t]) for s in samples) - min(float(s[t]) for s in samples)
                 for t in TRAITS)
    print("  identity: %d samples, largest change in any trait %.5f" % (n, spread))
    fig.tight_layout()
    _save_paper(fig, "fig_identity")


def fig_paper_bob_baseline():
    """Bob's configured personality. He wears no mask, so this is what he presents everywhere."""
    bob = _jcm_core("bob")
    if bob is None:
        print("  skipped fig_bob_baseline (no bob temper in vesna.jcm)")
        return
    fig, ax = plt.subplots(figsize=(7, 2.8))
    ax.bar(np.arange(len(TRAITS)), [bob[t] for t in TRAITS], 0.6, color=P_BOB, zorder=3)
    _paper_axes(ax)
    ax.set_ylabel("trait value", fontsize=9)
    ax.set_title("Bob — fixed Pro-AgentSpeak(L) baseline", fontsize=10)
    fig.tight_layout()
    _save_paper(fig, "fig_bob_baseline")


if __name__ == "__main__":
    fig_pirandello()
    fig_mask_by_trait()
    fig_transfer()
    fig_seed_variance()
    fig_two_scenarios()
    fig_identity_vs_adaptation()
    fig_paper_pirandellian(["results/signed_scale/mask_delta_0.5/learned_masks.csv"],
                           baseline="results/signed_scale/no_mask/learned_masks.csv")
    fig_paper_identity()
    fig_paper_bob_baseline()
