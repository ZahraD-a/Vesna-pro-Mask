#!/usr/bin/env bash
# One experimental condition over a range of seeds.
#
#   bash experiments/exp2_masks/run.sh NAME DELTA SHARED SHIFT SEED_LO SEED_HI [WEIGHTING] [ETA]
#
#   DELTA      mask bound (0 = no mask, plain Pro-AgentSpeak(L))
#   SHARED     true = one mask for every circumstance, false = one per circumstance
#   SHIFT      episode after which the partners' work norms become their home norms (0 = never)
#   WEIGHTING  uniform (default, Eq. 8 as written) or policy (regret weighted by choice probability)
#   ETA        mask step size (default: the value in vesna.jcm)
#
# Each run writes to results/exp2_masks/runs/NAME_seedS. vesna.jcm and logging.properties are
# edited for the run and restored afterwards, also if the script is interrupted.
set -u
NAME=$1; DELTA=$2; SHARED=$3; SHIFT=$4; LO=$5; HI=$6; WEIGHTING=${7:-uniform}; ETA=${8:-}
OUT=results/exp2_masks/runs
mkdir -p "$OUT"
cp vesna.jcm "$OUT/.jcm.bak"
cp logging.properties "$OUT/.lp.bak"
restore() { cp "$OUT/.jcm.bak" vesna.jcm; cp "$OUT/.lp.bak" logging.properties; rm -f "$OUT/.jcm.bak" "$OUT/.lp.bak"; }
trap restore EXIT

for s in $(seq "$LO" "$HI"); do
    cp "$OUT/.jcm.bak" vesna.jcm
    sed -i 's|^handlers = jason.runtime.MASConsoleLogHandler|handlers = java.util.logging.ConsoleHandler|' logging.properties
    sed -i 's/close_delay([0-9]*)/close_delay(0)/' vesna.jcm
    sed -i "s/^\(\s*seed:\s*\)[0-9]*$/\1$s/" vesna.jcm
    sed -i "s|^\(\s*\)mask_delta:\(\s*\)[0-9.]*$|\1mask_delta:\2$DELTA\n\1shared_mask:         $SHARED\n\1regret_weighting:    $WEIGHTING\n\1results_dir:         \"$OUT/${NAME}_seed$s\"|" vesna.jcm
    if [ -n "$ETA" ]; then
        sed -i "s/^\(\s*mask_learning_rate:\s*\)[0-9.]*$/\1$ETA/" vesna.jcm
    fi
    if [ "$SHIFT" != "0" ]; then
        sed -i "s/max_episodes(\([0-9]*\)),/max_episodes(\1),\n                             norm_shift_episode($SHIFT),/" vesna.jcm
    fi
    ./gradlew run --console=plain -q > "$OUT/${NAME}_seed$s.log" 2>&1
    code=$?
    if [ -f "$OUT/${NAME}_seed$s/reward_components.csv" ]; then
        echo "$(date +%H:%M:%S) $NAME seed $s ok (exit $code)"
    else
        echo "$(date +%H:%M:%S) $NAME seed $s FAILED (exit $code)"
    fi
done
