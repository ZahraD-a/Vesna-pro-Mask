#!/usr/bin/env bash
# Runs the MAS once per seed and collects reward_components.csv per run.
# Usage: bash experiments/seed_sweep.sh 1 8   (inclusive range)
set -u
LO=${1:-1}; HI=${2:-5}
OUT=experiments/_sweep
mkdir -p "$OUT"
cp logging.properties "$OUT/.lp.bak"
cp vesna.jcm "$OUT/.jcm.bak"
sed -i 's|^handlers = jason.runtime.MASConsoleLogHandler|handlers = java.util.logging.ConsoleHandler|' logging.properties
sed -i 's/close_delay([0-9]*)/close_delay(0)/' vesna.jcm
for s in $(seq "$LO" "$HI"); do
    sed -i "s/^\(\s*seed:\s*\)[0-9]*$/\1$s/" vesna.jcm
    echo "=== seed $s  $(date +%H:%M:%S) ==="
    ./gradlew.bat run --console=plain -q > "$OUT/run_seed$s.log" 2>&1
    if [ -f results/latest/reward_components.csv ]; then
        cp results/latest/reward_components.csv "$OUT/components_seed$s.csv"
        cp results/latest/learned_masks.csv     "$OUT/masks_seed$s.csv"
        echo "    ok"
    else
        echo "    FAILED"
    fi
done
cp "$OUT/.lp.bak" logging.properties
cp "$OUT/.jcm.bak" vesna.jcm
rm -f "$OUT/.lp.bak" "$OUT/.jcm.bak"
echo "done"
