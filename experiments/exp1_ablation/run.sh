#!/usr/bin/env bash
# Experiment 1 -- ablation.
#
# Claim: letting the mask move improves the outcome over a frozen personality.
#
# The two conditions differ in exactly one parameter, mask_delta. At 0.0 every mask is
# clipped to zero and can never move, so the agent keeps its core personality everywhere
# while running the identical code path and producing the identical logs. That isolates
# the mask itself rather than the whole learner.
#
# Usage: bash experiments/exp1_ablation/run.sh [first_seed] [last_seed]
set -u
LO=${1:-1}; HI=${2:-10}
OUT=results/exp1_ablation/runs
mkdir -p "$OUT"

cp vesna.jcm /tmp/exp1.jcm.bak
cp logging.properties /tmp/exp1.lp.bak
trap 'cp /tmp/exp1.jcm.bak vesna.jcm; cp /tmp/exp1.lp.bak logging.properties; echo restored' EXIT

sed -i 's|^handlers = jason.runtime.MASConsoleLogHandler|handlers = java.util.logging.ConsoleHandler|' logging.properties
sed -i 's/close_delay([0-9]*)/close_delay(0)/' vesna.jcm

total=0
for measure in dot l1 cosine; do
  for condition in fixed masked; do
    case $condition in
      fixed)  delta=0.0 ;;
      masked) delta=0.5 ;;
    esac
    for s in $(seq "$LO" "$HI"); do
      dest="$OUT/${condition}_${measure}_seed${s}"
      if [ -f "$dest/reward_components.csv" ]; then echo "skip $dest"; continue; fi
      sed -i "s/^\(\s*seed:\s*\)[0-9]*$/\1$s/"                vesna.jcm
      sed -i "s/^\(\s*compat:\s*\)[a-z0-9]*$/\1$measure/"     vesna.jcm
      sed -i "s/^\(\s*mask_delta:\s*\)[0-9.]*$/\1$delta/"     vesna.jcm
      echo "[$(date +%H:%M:%S)] $condition $measure seed $s"
      ./gradlew run --console=plain -q > /tmp/exp1_run.log 2>&1
      if [ -f results/latest/reward_components.csv ]; then
        mkdir -p "$dest"
        cp results/latest/reward_components.csv results/latest/learned_masks.csv \
           results/latest/episode_log.csv       results/latest/style_by_partner.csv "$dest/" 2>/dev/null
        total=$((total+1))
      else
        echo "   FAILED"
      fi
    done
  done
done
echo "completed $total runs"
