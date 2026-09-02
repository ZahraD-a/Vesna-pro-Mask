"""Aggregate a seed sweep into one summary table and report the standard error.

The stderr is the number that decides the experiment budget: it says how far apart
two conditions must be before the difference can be told from seed noise.

Usage:  python experiments/analyze_sweep.py [sweep_dir] [out_dir]
"""
import csv, glob, io, os, re, sys, math

SWEEP = sys.argv[1] if len(sys.argv) > 1 else "experiments/_sweep"
OUT   = sys.argv[2] if len(sys.argv) > 2 else "results/exp0_seed_sweep"
CIRCS = ["work", "home", "conference"]
TRAITS = ["o", "c", "e", "a", "n"]


def seeds():
    out = []
    for p in sorted(glob.glob(os.path.join(SWEEP, "components_seed*.csv"))):
        m = re.search(r"seed(\d+)", p)
        if m:
            out.append(int(m.group(1)))
    return sorted(out)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def mean(v):
    return sum(v) / len(v)


def stderr(v):
    if len(v) < 2:
        return 0.0
    m = mean(v)
    return math.sqrt(sum((x - m) ** 2 for x in v) / (len(v) - 1)) / math.sqrt(len(v))


def main():
    S = seeds()
    if not S:
        print("no sweep data in " + SWEEP)
        return
    os.makedirs(OUT, exist_ok=True)

    rows = []
    for s in S:
        comp = {r["circumstance"]: r for r in read(os.path.join(SWEEP, "components_seed%d.csv" % s))}
        mpath = os.path.join(SWEEP, "masks_seed%d.csv" % s)
        masks = read(mpath) if os.path.exists(mpath) else []
        for c in CIRCS:
            if c not in comp:
                continue
            off = {r["trait"]: float(r["mask_offset"]) for r in masks if r["circumstance"] == c}
            norm = math.sqrt(sum(off.get(t, 0.0) ** 2 for t in TRAITS)) if off else float("nan")
            r = comp[c]
            rows.append({
                "seed": s, "circumstance": c,
                "outcome": float(r["outcome"]),
                "authenticity": float(r["authenticity"]),
                "cost": float(r["cost"]),
                "pct_accepted": float(r["positive_pct"]),
                "pct_rejected": float(r["negative_pct"]),
                "mask_norm": round(norm, 4),
                **{"mask_" + t: off.get(t, float("nan")) for t in TRAITS},
            })

    cols = list(rows[0].keys())
    with io.open(os.path.join(OUT, "summary.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        w.writerows(rows)

    print("seeds: %s  (n=%d)\n" % (S, len(S)))
    print("%-12s %18s %18s %16s %12s" % ("circumstance", "outcome", "mask_norm", "pct_rejected", "seeds"))
    agg = []
    for c in CIRCS:
        sub = [r for r in rows if r["circumstance"] == c]
        if not sub:
            continue
        o = [r["outcome"] for r in sub]
        n = [r["mask_norm"] for r in sub]
        p = [r["pct_rejected"] for r in sub]
        print("%-12s %9.4f +/-%-7.4f %9.4f +/-%-7.4f %7.3f +/-%-5.3f %8d"
              % (c, mean(o), stderr(o), mean(n), stderr(n), mean(p), stderr(p), len(sub)))
        agg.append((c, mean(o), stderr(o), len(sub)))

    with io.open(os.path.join(OUT, "table_seed_variance.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["circumstance", "mean_outcome", "stderr_outcome", "n_seeds"])
        for c, m, se, n in agg:
            w.writerow([c, round(m, 4), round(se, 4), n])

    worst = max(se for _, _, se, _ in agg)
    print("\nlargest stderr across circumstances: %.4f" % worst)
    if worst < 0.03:
        print("  -> under 0.03: %d seeds is enough to separate effects of ~0.06 or more." % len(S))
    else:
        need = math.ceil(len(S) * (worst / 0.03) ** 2)
        print("  -> at or above 0.03: about %d seeds needed to bring stderr under 0.03." % need)
    print("\nwrote %s/summary.csv and %s/table_seed_variance.csv" % (OUT, OUT))


if __name__ == "__main__":
    main()
