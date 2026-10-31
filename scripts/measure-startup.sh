#!/usr/bin/env bash
# Measures cold start, repeatedly, and reports the median.
#
# The median, not the mean: the first run of any binary is always slower — page cache, GPU
# initialisation, JIT — and an average lets that one run distort every comparison.
#
# The origin is stamped here, immediately before exec, rather than read from the process inside the
# JVM. ProcessHandle's start instant is derived from boot time on Linux and drifts: it has reported
# a first paint earlier than the process existed. Sanity-check any figure against wall clock; a
# reported total above the wall time means the origin is wrong, not that the app is fast.
#
# The figure is launch to the first image on screen, so the launcher needs an image to open:
#
#   scripts/measure-startup.sh -n 5 scripts/dev-run.sh ~/Pictures/photo.jpg
#   scripts/measure-startup.sh -n 5 target/dist/Mirada/bin/Mirada ~/Pictures/photo.jpg
set -euo pipefail

runs=5
while [ $# -gt 0 ]; do
    case "$1" in
        -n) runs="$2"; shift 2 ;;
        --) shift; break ;;
        *)  break ;;
    esac
done

[ $# -ge 1 ] || { echo "usage: $0 [-n runs] <launcher> [args...]" >&2; exit 2; }

totals=()
for i in $(seq 1 "$runs"); do
    wall_start=$(python3 -c 'import time; print(int(time.time()*1000))')
    out=$(MIRADA_PERF=1 \
          MIRADA_PERF_EXIT=1 \
          MIRADA_PERF_T0="$wall_start" \
          "$@" 2>&1 || true)
    wall_end=$(python3 -c 'import time; print(int(time.time()*1000))')
    wall=$((wall_end - wall_start))

    total=$(printf '%s\n' "$out" | awk '/TOTAL/ {print $2}')
    if [ -z "$total" ]; then
        echo "run $i: no measurement (did an image open? the harness reports at its first paint)" >&2
        printf '%s\n' "$out" | tail -3 >&2
        continue
    fi
    # A total above wall clock is impossible and means the origin is wrong.
    if [ "$total" -gt "$wall" ]; then
        echo "run $i: reported ${total}ms exceeds wall ${wall}ms — the origin is wrong" >&2
    fi
    printf 'run %d: %5s ms   (wall %s ms)%s\n' "$i" "$total" "$wall" \
        "$([ "$i" = 1 ] && echo '   [cold — excluded from the median]' || true)"
    [ "$i" = 1 ] || totals+=("$total")
done

[ ${#totals[@]} -gt 0 ] || { echo "no usable runs" >&2; exit 1; }
printf '%s\n' "${totals[@]}" | sort -n | awk '{v[NR]=$1} END {
    m = (NR % 2) ? v[(NR+1)/2] : int((v[NR/2] + v[NR/2+1]) / 2)
    printf "\nmedian of %d warm runs: %d ms\n", NR, m
}'
