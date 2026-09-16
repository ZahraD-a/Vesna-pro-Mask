#!/usr/bin/env bash
# The non-social scenario (Alice alone, feedback from the environment) over a range of seeds.
#
#   bash experiments/exp2_masks/run_nonsocial.sh NAME DELTA SEED_LO SEED_HI [WEIGHTING] [ETA]
#
# Same meaning as run.sh. Core, circumstances and episodes come from vesna_nonsocial.jcm unchanged.
# Each run writes to results/exp2_masks/runs/NAME_seedS; the .jcm is restored afterwards.
set -u
NAME=$1; DELTA=$2; LO=$3; HI=$4; WEIGHTING=${5:-uniform}; ETA=${6:-}
JCM=vesna_nonsocial.jcm
OUT=results/exp2_masks/runs
mkdir -p "$OUT"
cp "$JCM" "$OUT/.jcm_ns.bak"
cp logging.properties "$OUT/.lp_ns.bak"
restore() { cp "$OUT/.jcm_ns.bak" "$JCM"; cp "$OUT/.lp_ns.bak" logging.properties; rm -f "$OUT/.jcm_ns.bak" "$OUT/.lp_ns.bak"; }
trap restore EXIT

for s in $(seq "$LO" "$HI"); do
    cp "$OUT/.jcm_ns.bak" "$JCM"
    sed -i 's|^handlers = jason.runtime.MASConsoleLogHandler|handlers = java.util.logging.ConsoleHandler|' logging.properties
    sed -i 's/close_delay([0-9]*)/close_delay(0)/' "$JCM"
    sed -i "s/^\(\s*seed:\s*\)[0-9]*$/\1$s/" "$JCM"
    sed -i "s|^\(\s*results_dir:\s*\).*$|\1\"$OUT/${NAME}_seed$s\"|" "$JCM"
    sed -i "s|^\(\s*\)mask_delta:\(\s*\)[0-9.]*$|\1mask_delta:\2$DELTA\n\1regret_weighting:    $WEIGHTING|" "$JCM"
    if [ -n "$ETA" ]; then
        sed -i "s/^\(\s*mask_learning_rate:\s*\)[0-9.]*$/\1$ETA/" "$JCM"
    fi
    ./gradlew runNonSocial --console=plain -q > "$OUT/${NAME}_seed$s.log" 2>&1
    code=$?
    if [ -f "$OUT/${NAME}_seed$s/reward_components.csv" ]; then
        echo "$(date +%H:%M:%S) $NAME seed $s ok (exit $code)"
    else
        echo "$(date +%H:%M:%S) $NAME seed $s FAILED (exit $code)"
    fi
done
