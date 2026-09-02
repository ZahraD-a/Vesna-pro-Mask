"""Aggregate Experiment 1 into a paper table and a figure.

Reads results/exp1_ablation/runs/<condition>_<measure>_seed<N>/reward_components.csv
Writes summary.csv, tables/table_ablation.csv, figures/figure_ablation.pdf|png
"""
import csv, glob, io, math, os, re

ROOT   = "results/exp1_ablation"
RUNS   = os.path.join(ROOT, "runs")
CIRCS  = ["work", "home", "conference"]
MEAS   = ["dot", "l1", "cosine"]
CONDS  = ["fixed", "masked"]


def mean(v):
    return sum(v) / len(v) if v else float("nan")


def stderr(v):
    if len(v) < 2:
        return 0.0
    m = mean(v)
    return math.sqrt(sum((x - m) ** 2 for x in v) / (len(v) - 1)) / math.sqrt(len(v))


def load():
    rows = []
    for d in sorted(glob.glob(os.path.join(RUNS, "*_seed*"))):
        m = re.match(r"(fixed|masked)_(dot|l1|cosine)_seed(\d+)$", os.path.basename(d))
        f = os.path.join(d, "reward_components.csv")
        if not m or not os.path.exists(f):
            continue
        cond, meas, seed = m.group(1), m.group(2), int(m.group(3))
        with io.open(f, encoding="utf-8") as fh:
            for r in csv.DictReader(fh):
                rows.append({"condition": cond, "measure": meas, "seed": seed,
                             "circumstance": r["circumstance"],
                             "outcome": float(r["outcome"]),
                             "pct_accepted": float(r["positive_pct"]),
                             "pct_rejected": float(r["negative_pct"])})
    return rows


def main():
    rows = load()
    if not rows:
        print("no runs found in " + RUNS)
        return
    os.makedirs(os.path.join(ROOT, "tables"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "figures"), exist_ok=True)

    with io.open(os.path.join(ROOT, "summary.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)

    def cell(meas, cond, circ):
        return [r["outcome"] for r in rows
                if r["measure"] == meas and r["condition"] == cond and r["circumstance"] == circ]

    table = []
    print("%-8s %-8s %-22s %-22s %-22s %10s" % ("measure", "cond", "work", "home", "conference", "n"))
    for meas in MEAS:
        for cond in CONDS:
            cells = [cell(meas, cond, c) for c in CIRCS]
            if not any(cells):
                continue
            row = {"measure": meas, "condition": cond}
            txt = []
            for c, v in zip(CIRCS, cells):
                row["%s_mean" % c] = round(mean(v), 4)
                row["%s_stderr" % c] = round(stderr(v), 4)
                txt.append("%8.4f +/-%-7.4f" % (mean(v), stderr(v)))
            row["n_seeds"] = len(cells[0])
            table.append(row)
            print("%-8s %-8s %-22s %-22s %-22s %10d" % (meas, cond, txt[0], txt[1], txt[2], len(cells[0])))
        f_, m_ = [cell(meas, "fixed", c) for c in CIRCS], [cell(meas, "masked", c) for c in CIRCS]
        if all(f_) and all(m_):
            gains = [mean(b) - mean(a) for a, b in zip(f_, m_)]
            ses = [math.sqrt(stderr(a) ** 2 + stderr(b) ** 2) for a, b in zip(f_, m_)]
            print("%-8s %-8s %s" % ("", "gain",
                  "  ".join("%s %+.4f (%.1f x se)" % (c, g, abs(g) / se if se else 0)
                            for c, g, se in zip(CIRCS, gains, ses))))
        print()

    if table:
        with io.open(os.path.join(ROOT, "tables", "table_ablation.csv"), "w",
                     encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(table[0].keys()))
            w.writeheader(); w.writerows(table)

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib missing -- table written, figure skipped")
        return

    present = [m for m in MEAS if any(r["measure"] == m for r in rows)]
    fig, axes = plt.subplots(1, len(present), figsize=(4.2 * len(present), 3.6), sharey=True)
    if len(present) == 1:
        axes = [axes]
    x = range(len(CIRCS)); wd = 0.36
    for ax, meas in zip(axes, present):
        for i, (cond, col) in enumerate([("fixed", "#9aa0a6"), ("masked", "#3b6ea5")]):
            mu = [mean(cell(meas, cond, c)) for c in CIRCS]
            se = [stderr(cell(meas, cond, c)) for c in CIRCS]
            ax.bar([p + (i - 0.5) * wd for p in x], mu, wd, yerr=se, capsize=3,
                   label=cond, color=col)
        ax.set_xticks(list(x)); ax.set_xticklabels(CIRCS)
        ax.set_title(meas); ax.axhline(0, color="0.5", lw=0.8)
    axes[0].set_ylabel("mean outcome")
    axes[-1].legend(frameon=False)
    fig.suptitle("Experiment 1: frozen personality vs learned mask", y=1.0)
    fig.tight_layout()
    for ext in ("pdf", "png"):
        fig.savefig(os.path.join(ROOT, "figures", "figure_ablation." + ext), dpi=150,
                    bbox_inches="tight")
    print("wrote %s/tables/table_ablation.csv and %s/figures/figure_ablation.pdf" % (ROOT, ROOT))


if __name__ == "__main__":
    main()
