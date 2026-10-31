#!/usr/bin/env bash
# Launches a packaged Mirada on a folder and checks it showed every image in it.
#
# The tour (com.mirada.perf.Tour) steps through the folder and prints a line per image it put on
# screen, so a count of those lines proves the whole chain for each format — the bundled runtime,
# JavaFX, ImageIO and its plugins, the bundled libheif and libjxl — where a launch that merely
# opens a window proves none of it. A decoder that fails stalls the tour, and the time limit ends it.
#
# Usage: smoke-test.sh <launcher> <folder-of-images>
set -euo pipefail

launcher="${1:?launcher required}"
folder="${2:?folder required}"
[ -x "$launcher" ] || { echo "::error::no launcher at $launcher"; exit 1; }

images=$(find "$folder" -maxdepth 1 -type f | wc -l | tr -d ' ')
limit=()
command -v timeout >/dev/null 2>&1 && limit=(timeout 120)
start=$(python3 -c 'import time; print(int(time.time()*1000))' 2>/dev/null || python -c 'import time; print(int(time.time()*1000))')

export MIRADA_PERF=1 MIRADA_PERF_TOUR=1 MIRADA_PERF_T0="$start"
if command -v cygpath >/dev/null 2>&1; then folder=$(cygpath -w "$folder"); fi
if [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ]; then
    command -v xvfb-run >/dev/null || { echo "::error::no display and no xvfb-run"; exit 1; }
    out=$(${limit[@]+"${limit[@]}"} xvfb-run -a "$launcher" "$folder" 2>&1 || true)
else
    out=$(${limit[@]+"${limit[@]}"} "$launcher" "$folder" 2>&1 || true)
fi
printf '%s\n' "$out" | grep -E '^\[perf\]|^  [a-z-]+ +[0-9]+ ms' || true

# The first image is the start-up figure; every other one is a step line.
steps=$(printf '%s\n' "$out" | grep -c '^\[perf\] step' || true)
if [ "$steps" -ne $((images - 1)) ]; then
    echo "::error::showed $((steps + 1)) of $images images"
    printf '%s\n' "$out" | tail -20
    exit 1
fi
echo "smoke test: all $images images shown by the packaged app"
