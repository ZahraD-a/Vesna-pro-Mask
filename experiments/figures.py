"""Figures for the paper, all from data already on disk. No new runs.

  fig_pirandellian                    core vs presented personality per circumstance, with a
                                      no-mask reference panel (social scenario)
  fig_pirandellian_social_vs_nonsocial  the same, one row per source of feedback
  fig_transfer                        one mask per circumstance, three partners, same style mix

Also defined, for the learning experiments as they are run: fig_paper_learning,
fig_paper_learning_compare, fig_paper_learning_single, fig_paper_interference, fig_paper_delta.

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


def _seed_masks(paths):
    """effective[circumstance][trait] -> one value per run, and the core, identical in all.
    paths names the learned_masks.csv files to read."""
    eff = defaultdict(lambda: defaultdict(list))
    core = {}
    seeds = []
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
    """A bar of value 0 has no height and reads as missing data; label it 0 instead."""
    color = kw.get("color", "0.2")
    for xi, v in zip(xs, vals):
        if abs(v) < 0.005:
            ax.text(xi, 0.04, "0", ha="center", va="bottom", fontsize=7, fontweight="bold",
                    color=color, zorder=4)


def _save_paper(fig, name):
    os.makedirs(OUT, exist_ok=True)
    fig.savefig(os.path.join(OUT, name + ".pdf"), bbox_inches="tight")
    fig.savefig(os.path.join(OUT, name + ".png"), dpi=200, bbox_inches="tight")
    plt.close(fig)
    print("  wrote %s/%s.pdf and .png" % (OUT, name))


def fig_paper_pirandellian(paths, baseline=None):
    """Alice's core against what she presents, one panel per circumstance. Presented values are
    the mean over the runs read, with standard error; the core is fixed, so it has none.

    baseline names the learned_masks.csv of a run with mask_delta 0: the same agent with the mask
    unable to move, i.e. plain Pro-AgentSpeak(L). It is drawn first, as the reference the three
    masked panels are read against. Its presented personality is read from that run, not assumed."""
    core, eff, seeds = _seed_masks(paths)
    if not seeds:
        print("  skipped fig_pirandellian (no sweep data)")
        return

    panels = []
    if baseline is not None and os.path.exists(baseline):
        _, beff, _ = _seed_masks([baseline])
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


TRAIT_COLS = {"o": "#2a78d6", "c": "#eb6834", "e": "#1baf7a", "a": "#eda100", "n": "#e87ba4"}


def _trajectories(run_dirs):
    """mask[trait] -> array (runs x episodes). A mask is logged only once its circumstance has been
    entered; before that it is the zero mask by construction, so missing episodes are filled with 0."""
    per_run = []
    for d in run_dirs:
        data = rows(os.path.join(d, "mask_trajectory.csv"))
        last_ep = max(int(r["episode"]) for r in data)
        tr = {c: {t: np.zeros(last_ep + 1) for t in TRAITS} for c in CIRCS}
        for r in data:
            c = r["mask"].replace("mask_", "")
            if c in tr:
                for t in TRAITS:
                    tr[c][t][int(r["episode"])] = float(r[t])
        # The last logged row must be the mask the run reported at the end, or the plot is wrong.
        final = os.path.join(d, "learned_masks.csv")
        if os.path.exists(final):
            for r in rows(final):
                if r["circumstance"] in tr:
                    got = tr[r["circumstance"]][r["trait"]][-1]
                    assert abs(got - float(r["mask_offset"])) < 1e-3, (d, r, got)
        per_run.append(tr)
    n_ep = min(len(tr[CIRCS[0]][TRAITS[0]]) for tr in per_run)
    return {c: {t: np.array([tr[c][t][:n_ep] for tr in per_run]) for t in TRAITS} for c in CIRCS}


def _learning_panel(ax, tr, c, n, delta, label_prefix=""):
    """One circumstance: mean mask per trait over n runs, standard-error band, end labels, +/-delta."""
    ep = np.arange(tr[c][TRAITS[0]].shape[1])
    ends = []
    for t, lab in zip(TRAITS, LABELS):
        m = tr[c][t].mean(axis=0)
        if n > 1:
            se = tr[c][t].std(axis=0, ddof=1) / math.sqrt(n)
            ax.fill_between(ep, m - se, m + se, color=TRAIT_COLS[t], alpha=0.18, lw=0, zorder=2)
        ax.plot(ep, m, color=TRAIT_COLS[t], lw=1.6, zorder=3, label=lab)
        ends.append([m[-1], t])
    # Direct labels at the right end. Traits that finish on the same value share one label, so a
    # label never sits at a height no line reaches; distinct labels are then spread apart.
    ends.sort()
    groups = []
    for y, t in ends:
        if groups and abs(y - groups[-1][1]) < 0.03:
            groups[-1][2].append(t.upper())
        else:
            groups.append([y, y, [t.upper()]])
    for i in range(1, len(groups)):
        groups[i][0] = max(groups[i][0], groups[i - 1][0] + 0.085)
    for y, _, names in groups:
        ax.text(ep[-1] + 3, y, " ".join(names), va="center", fontsize=7.5, fontweight="bold",
                color="#1E293B")
    for b in (-delta, delta):
        ax.axhline(b, color="#64748B", lw=0.9, ls=(0, (4, 3)), zorder=1)
    _style_axes(ax)
    ax.axhline(0, color="#1E293B", lw=0.8, zorder=2)
    ax.set_xlim(0, ep[-1] + 12)
    ax.set_ylim(-delta - 0.12, delta + 0.12)
    ax.set_yticks([-delta, -delta / 2, 0, delta / 2, delta])
    print("  learning %s%-11s final " % (label_prefix, c) + "  ".join(
        "%s %+.2f" % (t.upper(), tr[c][t].mean(axis=0)[-1]) for t in TRAITS))


def _learning_legend(fig, ax, y=1.03):
    h, l = ax.get_legend_handles_labels()
    h.append(plt.Line2D([], [], color="#64748B", lw=0.9, ls=(0, (4, 3))))
    l.append(r"mask bound $\pm\delta$")
    fig.legend(h, l, loc="upper center", bbox_to_anchor=(0.5, y), ncol=6, frameon=False,
               fontsize=7.5, handlelength=1.6, columnspacing=1.0)


def fig_paper_learning(run_dirs, delta=0.5):
    """The three masks as they are learned: one panel per circumstance, one line per trait, mean over
    runs with a standard-error band. Every mask starts at zero and stays inside +/-delta."""
    run_dirs = [d for d in run_dirs if os.path.exists(os.path.join(d, "mask_trajectory.csv"))]
    if not run_dirs:
        print("  skipped fig_learning (no runs)")
        return
    tr = _trajectories(run_dirs)
    fig, axes = plt.subplots(1, len(CIRCS), figsize=(7.2, 2.7), sharey=True)
    for ax, c in zip(axes, CIRCS):
        _learning_panel(ax, tr, c, len(run_dirs), delta)
        ax.set_title("mask at " + c.capitalize(), fontsize=9.5)
        ax.set_xlabel("episode", fontsize=8.5)
    axes[0].set_ylabel("mask offset", fontsize=9)
    _learning_legend(fig, axes[0])
    fig.tight_layout(rect=[0, 0, 1, 0.9])
    _save_paper(fig, "fig_learning")


def fig_paper_learning_compare(groups, delta=0.5, name="fig_learning_social_vs_nonsocial"):
    """groups: [(row label, [run_dirs]), ...]. One row per source of feedback, same layout as
    fig_learning, so the same circumstance can be read down a column."""
    groups = [(lab, [d for d in ds if os.path.exists(os.path.join(d, "mask_trajectory.csv"))])
              for lab, ds in groups]
    if not all(ds for _, ds in groups):
        print("  skipped %s (missing runs)" % name)
        return
    fig, axes = plt.subplots(len(groups), len(CIRCS), figsize=(7.2, 2.3 * len(groups) + 0.5),
                             sharey=True, sharex=True)
    for row, (lab, dirs) in enumerate(groups):
        tr = _trajectories(dirs)
        for col, c in enumerate(CIRCS):
            ax = axes[row][col]
            _learning_panel(ax, tr, c, len(dirs), delta, label_prefix=lab[:10] + " ")
            if row == 0:
                ax.set_title("mask at " + c.capitalize(), fontsize=9.5, pad=18)
            if row == len(groups) - 1:
                ax.set_xlabel("episode", fontsize=8.5)
        axes[row][0].set_ylabel("mask offset", fontsize=8.5)
        axes[row][0].text(0.0, 1.03, "%s (%d runs)" % (lab, len(dirs)), transform=axes[row][0].transAxes,
                          fontsize=9, fontweight="bold", color="#1E293B", ha="left")
    _learning_legend(fig, axes[0][0], y=1.02)
    fig.tight_layout(rect=[0, 0, 1, 0.93])
    _save_paper(fig, name)


def fig_paper_pirandellian_compare(groups, name="fig_pirandellian_social_vs_nonsocial"):
    """groups: [(row label, [learned_masks.csv of masked runs], learned_masks.csv of a no-mask run
    or None), ...]. Core against presented personality, one row per source of feedback. The first
    column is the same agent with mask_delta 0 -- plain Pro-AgentSpeak(L) -- read from that run,
    not assumed. With no mask every circumstance presents the core, so one panel stands for all."""
    x = np.arange(len(TRAITS))
    w = 0.38
    with_base = any(b for _, _, b in groups)
    ncol = len(CIRCS) + (1 if with_base else 0)
    fig, grid = plt.subplots(len(groups), ncol, figsize=(7.2, 2.5 * len(groups) + 0.5), sharey=True,
                             )
    cores = []

    def bars(ax, cor, mus, ses, n):
        ax.bar(x - w / 2 - 0.01, cor, w, color=P_CORE, zorder=3, label="Core (immutable identity)")
        _zero_stubs(ax, x - w / 2 - 0.01, cor, w, color=P_CORE)
        err = dict(yerr=ses, capsize=1.8, error_kw=dict(lw=0.8, ecolor=P_ERR, capthick=0.8)) if n > 1 else {}
        ax.bar(x + w / 2 + 0.01, mus, w, color=P_SHOWN, zorder=3, label="Presented personality", **err)
        _zero_stubs(ax, x + w / 2 + 0.01, mus, w, color=P_SHOWN)
        _paper_axes(ax)
        ax.tick_params(labelsize=8)

    for row, (lab, paths, baseline) in enumerate(groups):
        core, eff, seeds = _seed_masks(paths)
        if not seeds:
            print("  skipped %s (no runs for %s)" % (name, lab))
            plt.close(fig)
            return
        cores.append([core[t] for t in TRAITS])
        cor = [core[t] for t in TRAITS]
        axes = list(grid[row])
        if with_base:
            ax = axes[0]
            if baseline and os.path.exists(baseline):
                bcore, beff, _ = _seed_masks([baseline])
                flat = all(abs(beff[c][t][0] - beff[CIRCS[0]][t][0]) < 1e-9 for c in CIRCS for t in TRAITS)
                same_core = all(abs(bcore[t] - core[t]) < 1e-9 for t in TRAITS)
                if not (flat and same_core):
                    print("  WARNING: baseline for %s is not flat or has another core" % lab)
                bars(ax, cor, [beff[CIRCS[0]][t][0] for t in TRAITS], [0] * len(TRAITS), 1)
                print("  compare %-10s %-11s " % (lab[:10], "no mask") + "  ".join(
                    "%s %+.2f" % (t.upper(), beff[CIRCS[0]][t][0]) for t in TRAITS))
            else:
                ax.set_visible(False)
            if row == 0:
                ax.set_title("Alice without mask\n(any circumstance)", fontsize=9, pad=18)
            axes = axes[1:]
        for col, c in enumerate(CIRCS):
            ax = axes[col]
            stats = [_mean_se(eff[c][t]) for t in TRAITS]
            mus = [m for m, _ in stats]
            ses = [e for _, e in stats]
            bars(ax, cor, mus, ses, len(seeds))
            if row == 0:
                ax.set_title("Alice with mask\nat " + c.capitalize(), fontsize=9, pad=18)
            print("  compare %-10s %-11s " % (lab[:10], c) + "  ".join(
                "%s %+.2f+/-%.2f" % (t.upper(), m, e) for t, m, e in zip(TRAITS, mus, ses)))
        grid[row][0].set_ylabel("trait value", fontsize=8.5)
        grid[row][0].text(0.0, 1.03, "%s (%d runs)" % (lab, len(seeds)), transform=grid[row][0].transAxes,
                          fontsize=9, fontweight="bold", color="#1E293B", ha="left")
    # The comparison only reads cleanly if both rows carry the same core; refuse to draw otherwise.
    assert all(max(abs(a - b) for a, b in zip(cores[0], k)) < 1e-9 for k in cores), \
        "rows have different cores: %s" % cores
    h, l = grid[0][-1].get_legend_handles_labels()
    fig.legend(h, l, loc="upper center", bbox_to_anchor=(0.5, 1.02), ncol=2, frameon=False, fontsize=8.5)
    fig.tight_layout(rect=[0, 0, 1, 0.93])
    if with_base:
        # A thin rule between the baseline column and the masked ones.
        b0, b1 = grid[-1][0].get_position(), grid[0][1].get_position()
        xr = (b0.x1 + b1.x0) / 2
        fig.add_artist(plt.Line2D([xr, xr], [b0.y0, grid[0][0].get_position().y1 + 0.02],
                                  transform=fig.transFigure, color="#94A3B8", lw=0.8))
    _save_paper(fig, name)


def fig_paper_learning_single(run_dir, delta=0.5, name="fig_learning_seed1"):
    """One run, every mask update. Top row: the worn mask after each update, per trait. Bottom row:
    the signed step each update took, so pulls (up) and pushes (down) from regret are visible."""
    path = os.path.join(run_dir, "mask_steps.csv")
    if not os.path.exists(path):
        print("  skipped %s (no mask_steps.csv)" % name)
        return
    data = rows(path)
    fig, axes = plt.subplots(2, len(CIRCS), figsize=(7.2, 4.6),
                             gridspec_kw=dict(height_ratios=[1.4, 1]))
    step_lim = 0.0
    for col, c in enumerate(CIRCS):
        sub = [r for r in data if r["circumstance"] == c]
        n = np.arange(1, len(sub) + 1)
        top, bot = axes[0][col], axes[1][col]
        balance = {}
        for t, lab in zip(TRAITS, LABELS):
            top.plot(n, [float(r[t]) for r in sub], color=TRAIT_COLS[t], lw=1.2, label=lab)
            steps = np.array([float(r["step_" + t]) for r in sub])
            up, down = (steps > 1e-9).sum(), (steps < -1e-9).sum()
            balance[t] = min(up, down) / max(1, up + down)
            print("  single %-11s %s  updates up %4d  down %4d  zero %4d  final %+.3f"
                  % (c, t.upper(), up, down, len(steps) - up - down, float(sub[-1][t])))
        # The bottom row zooms into the first episodes of the trait whose updates are most evenly
        # split between up and down: the one where pull and push from regret are both visible.
        t = max(balance, key=balance.get)
        window = min(len(sub), 135)
        steps = np.array([float(r["step_" + t]) for r in sub[:window]]) * 1000
        bot.vlines(np.arange(1, window + 1), 0, steps, color=TRAIT_COLS[t], lw=1.0)
        step_lim = max(step_lim, np.abs(steps).max())
        top.axvspan(1, window, color="#CBD5E1", alpha=0.45, lw=0, zorder=0)
        bot.set_title("%s: step at each of updates 1-%d" % (dict(zip(TRAITS, LABELS))[t], window),
                      fontsize=7)
        for b in (-delta, delta):
            top.axhline(b, color="#64748B", lw=0.9, ls=(0, (4, 3)))
        for ax in (top, bot):
            _style_axes(ax)
            ax.axhline(0, color="#1E293B", lw=0.8, zorder=2)
        top.set_ylim(-delta - 0.08, delta + 0.08)
        top.set_title("mask at " + c.capitalize(), fontsize=9.5)
        top.set_xlabel("update (about 9 per episode)", fontsize=7.5)
        bot.set_xlabel("update", fontsize=7.5)
        if col > 0:
            top.set_yticklabels([])
            bot.set_yticklabels([])
    for col in range(len(CIRCS)):
        axes[1][col].set_ylim(-step_lim * 1.1, step_lim * 1.1)
    axes[0][0].set_ylabel("mask offset", fontsize=8.5)
    axes[1][0].set_ylabel("step\n" + r"($\times 10^{-3}$)", fontsize=8.5)
    h, l = axes[0][0].get_legend_handles_labels()
    h.append(plt.Line2D([], [], color="#64748B", lw=0.9, ls=(0, (4, 3))))
    l.append(r"mask bound $\pm\delta$")
    fig.legend(h, l, loc="upper center", bbox_to_anchor=(0.5, 1.02), ncol=6, frameon=False,
               fontsize=7.5, handlelength=1.6, columnspacing=1.0)
    fig.tight_layout(rect=[0, 0, 1, 0.93])
    _save_paper(fig, name)


CIRC_COLS = {"work": "#4a3aa7", "home": "#008300", "conference": "#e34948"}
CIRC_MARK = {"work": "o", "home": "s", "conference": "^"}


def _style_axes(ax):
    ax.set_facecolor(P_PANEL)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color("#94A3B8")
    ax.grid(axis="y", color="white", lw=1.2)
    ax.set_axisbelow(True)
    ax.tick_params(length=0, labelsize=8)


def _episode_outcomes(run_dir):
    """circumstance -> array over episodes 1..N of the mean outcome in that episode (NaN if the
    circumstance was not visited in that episode)."""
    data = rows(os.path.join(run_dir, "episode_outcomes.csv"))
    n = max(int(r["episode"]) for r in data)
    out = {c: np.full(n, np.nan) for c in CIRCS}
    for r in data:
        if r["circumstance"] in out:
            out[r["circumstance"]][int(r["episode"]) - 1] = float(r["outcome"])
    return out


def _smooth(v, k):
    """Trailing moving average over k episodes, so a change is never drawn before it happens."""
    s = np.array([np.nanmean(v[max(0, i - k + 1):i + 1]) for i in range(len(v))])
    return s


def fig_paper_interference(ours_dirs, shared_dirs, shift, window=5):
    """Outcome per episode in each circumstance when the work norms change at episode `shift`.
    Top row: one mask per circumstance. Bottom row: one mask shared by all circumstances."""
    groups = [("one mask per circumstance", ours_dirs), ("one shared mask", shared_dirs)]
    groups = [(n, [d for d in ds if os.path.exists(os.path.join(d, "episode_outcomes.csv"))])
              for n, ds in groups]
    if not all(ds for _, ds in groups):
        print("  skipped fig_interference (missing runs)")
        return
    fig, axes = plt.subplots(2, len(CIRCS), figsize=(7.2, 4.2), sharex=True, sharey=True)
    for row, (name, dirs) in enumerate(groups):
        per_run = [_episode_outcomes(d) for d in dirs]
        for col, c in enumerate(CIRCS):
            ax = axes[row][col]
            runs = np.array([_smooth(r[c], window) for r in per_run])
            ep = np.arange(1, runs.shape[1] + 1)
            m = runs.mean(axis=0)
            if len(dirs) > 1:
                se = runs.std(axis=0, ddof=1) / math.sqrt(len(dirs))
                ax.fill_between(ep, m - se, m + se, color=CIRC_COLS[c], alpha=0.2, lw=0)
            ax.plot(ep, m, color=CIRC_COLS[c], lw=1.6)
            ax.axvline(shift + 0.5, color="#1E293B", lw=0.9, ls=(0, (4, 3)))
            _style_axes(ax)
            if row == 0:
                ax.set_title(c.capitalize() + (" (norms change)" if c == "work" else ""), fontsize=9.5,
                             pad=22)
            if row == 1:
                ax.set_xlabel("episode", fontsize=8.5)
            before = np.nanmean([r[c][shift - 20:shift] for r in per_run])
            after = np.nanmean([r[c][shift + 20:shift + 40] for r in per_run])
            print("  interference %-26s %-11s outcome eps %d-%d %+.3f  eps %d-%d %+.3f"
                  % (name, c, shift - 19, shift, before, shift + 21, shift + 40, after))
        axes[row][0].set_ylabel("outcome\n(%d-episode mean)" % window, fontsize=8.5)
        axes[row][0].text(0.0, 1.04, name, transform=axes[row][0].transAxes,
                          fontsize=9, fontweight="bold", color="#1E293B", ha="left")
    fig.suptitle("Work norms change after episode %d (dashed line); home and conference norms do not"
                 % shift, fontsize=9.5, y=0.995)
    fig.tight_layout(rect=[0, 0, 1, 0.97])
    _save_paper(fig, "fig_interference")


def _max_identity_distance(run_dir):
    """max over episodes, masks and traits of |A_eff - A_core|, with A_eff clipped to [-1,1]."""
    core = {r["trait"]: float(r["core"]) for r in rows(os.path.join(run_dir, "learned_masks.csv"))}
    best = 0.0
    for r in rows(os.path.join(run_dir, "mask_trajectory.csv")):
        for t in TRAITS:
            eff = max(-1.0, min(1.0, core[t] + float(r[t])))
            best = max(best, abs(eff - core[t]))
    return best


def fig_paper_delta(sweep, last=24):
    """sweep: list of (delta, [run_dirs]). (a) outcome per circumstance over the last `last`
    episodes; (b) the largest distance the presented personality ever took from the core."""
    sweep = [(dl, [d for d in ds if os.path.exists(os.path.join(d, "episode_outcomes.csv"))])
             for dl, ds in sweep]
    sweep = [(dl, ds) for dl, ds in sweep if ds]
    if len(sweep) < 2:
        print("  skipped fig_delta (need at least two delta values)")
        return
    x = np.arange(len(sweep))
    fig, (ax_o, ax_d) = plt.subplots(1, 2, figsize=(7.2, 2.7))
    for c in CIRCS:
        mus, ses = [], []
        for dl, ds in sweep:
            v = [np.nanmean(_episode_outcomes(d)[c][-last:]) for d in ds]
            mus.append(np.mean(v))
            ses.append(np.std(v, ddof=1) / math.sqrt(len(v)) if len(v) > 1 else 0.0)
        ax_o.errorbar(x, mus, yerr=ses, color=CIRC_COLS[c], marker=CIRC_MARK[c], ms=5, lw=1.5,
                      capsize=2, label=c.capitalize())
        ax_o.text(x[-1] + 0.12, mus[-1], c.capitalize(), va="center", fontsize=7.5,
                  color="#1E293B")
        print("  delta outcome %-11s " % c + "  ".join("d=%g %+.3f" % (dl, m) for (dl, _), m in zip(sweep, mus)))
    dist = [[_max_identity_distance(d) for d in ds] for _, ds in sweep]
    dm = [np.mean(v) for v in dist]
    dse = [np.std(v, ddof=1) / math.sqrt(len(v)) if len(v) > 1 else 0.0 for v in dist]
    ax_d.errorbar(x, dm, yerr=dse, color="#1E293B", marker="o", ms=5, lw=1.5, capsize=2)
    ax_d.plot(x, [dl for dl, _ in sweep], color="#64748B", lw=0.9, ls=(0, (4, 3)),
              label=r"$\delta$ (the bound)")
    print("  delta distance " + "  ".join("d=%g %.3f" % (dl, m) for (dl, _), m in zip(sweep, dm)))
    for ax, title, ylab in ((ax_o, "(a) outcome, last %d episodes" % last, "outcome"),
                            (ax_d, "(b) largest distance from the core", r"max $|A_{eff}-A_{core}|$")):
        _style_axes(ax)
        ax.set_xticks(x)
        ax.set_xticklabels(["%g" % dl for dl, _ in sweep])
        ax.set_xlabel(r"mask bound $\delta$", fontsize=8.5)
        ax.set_ylabel(ylab, fontsize=8.5)
        ax.set_title(title, fontsize=9.5)
        if 0.5 in [dl for dl, _ in sweep]:
            ax.axvspan(x[[dl for dl, _ in sweep].index(0.5)] - 0.3, x[[dl for dl, _ in sweep].index(0.5)] + 0.3,
                       color="#CBD5E1", alpha=0.5, lw=0, zorder=0)
    ax_o.set_xlim(-0.4, x[-1] + 1.1)
    ax_d.legend(loc="upper left", frameon=False, fontsize=7.5)
    fig.tight_layout()
    _save_paper(fig, "fig_delta")


if __name__ == "__main__":
    RUNS = "results/exp2_masks/runs/"
    social = [RUNS + "social_masked_seed%d/learned_masks.csv" % i for i in (1, 2, 3)]
    nonsocial = [RUNS + "nonsocial_masked_seed%d/learned_masks.csv" % i for i in (1, 2, 3)]
    fig_transfer()
    fig_paper_pirandellian(social, baseline=RUNS + "social_nomask_seed1/learned_masks.csv")
    fig_paper_pirandellian_compare([
        ("social: partners' replies", social, RUNS + "social_nomask_seed1/learned_masks.csv"),
        ("non-social: task outcomes", nonsocial, RUNS + "nonsocial_nomask_seed1/learned_masks.csv")])
