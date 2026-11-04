#!/usr/bin/env bash
# Launches a packaged Mirada on a folder and checks it showed every image in it.
#
# The tour (com.mirada.perf.Tour) steps through the folder and prints a line per image it put on
# screen, so a count of those lines proves the whole chain for each format — the bundled runtime,
# JavaFX, ImageIO and its plugins, the bundled libheif and libjxl — where a launch that merely
# opens a window proves none of it. A decoder that fails stalls the tour, and the time limit ends it.
#
# Given a macOS bundle (Mirada.app) rather than its executable, it opens the folder as Finder does:
# `open -a`, so the folder arrives as an "open documents" Apple Event and not as an argument
# (os.MacOpenFiles). That is the path a double-click takes, and the one 0.1.0 dropped.
#
# Usage: smoke-test.sh <launcher-or-Mirada.app> <folder-of-images>
set -euo pipefail

launcher="${1:?launcher required}"
folder="${2:?folder required}"
if [ -d "$launcher" ] && [ "${launcher%.app}" != "$launcher" ]; then
    bundle=$(cd "$launcher" && pwd)
    folder=$(cd "$folder" && pwd)
else
    bundle=""
    [ -x "$launcher" ] || { echo "::error::no launcher at $launcher"; exit 1; }
fi

images=$(find "$folder" -maxdepth 1 -type f | wc -l | tr -d ' ')
limit=()
command -v timeout >/dev/null 2>&1 && limit=(timeout 120)
start=$(python3 -c 'import time; print(int(time.time()*1000))' 2>/dev/null || python -c 'import time; print(int(time.time()*1000))')

export MIRADA_PERF=1 MIRADA_PERF_TOUR=1 MIRADA_PERF_T0="$start"
if command -v cygpath >/dev/null 2>&1; then folder=$(cygpath -w "$folder"); fi
if [ -n "$bundle" ]; then
    # `open -W` waits for the app to quit, which the tour does after the last image; macOS has no
    # `timeout`, so the limit is a loop, and a stalled app is killed.
    log=$(mktemp)
    open -W -n --stdout "$log" --stderr "$log" \
        --env MIRADA_PERF=1 --env MIRADA_PERF_TOUR=1 --env MIRADA_PERF_T0="$start" \
        -a "$bundle" "$folder" &
    waiting=$!
    for _ in $(seq 120); do kill -0 "$waiting" 2>/dev/null || break; sleep 1; done
    if kill -0 "$waiting" 2>/dev/null; then
        pkill -f "$bundle/Contents/MacOS/" || true
        kill "$waiting" 2>/dev/null || true
    fi
    wait "$waiting" 2>/dev/null || true
    out=$(cat "$log")
    rm -f "$log"
elif [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ]; then
    command -v xvfb-run >/dev/null || { echo "::error::no display and no xvfb-run"; exit 1; }
    out=$(${limit[@]+"${limit[@]}"} xvfb-run -a "$launcher" "$folder" 2>&1 || true)
else
    out=$(${limit[@]+"${limit[@]}"} "$launcher" "$folder" 2>&1 || true)
fi
printf '%s\n' "$out" | grep -E '^\[perf\] (startup|step)|^  [a-z-]+ +[0-9]+ ms' || true

# The first image is the start-up figure; every other one is a step line, or a fail line naming
# the file and why (the tour reports a failure and goes on, so one run names them all).
steps=$(printf '%s\n' "$out" | grep -c '^\[perf\] step' || true)
fails=$(printf '%s\n' "$out" | grep -c '^\[perf\] fail' || true)
if [ "$fails" -gt 0 ]; then
    printf '%s\n' "$out" | grep '^\[perf\] fail' | sed 's/^/::error::/'
    echo "::error::$fails of $images images failed to open"
    exit 1
fi
if [ "$steps" -ne $((images - 1)) ]; then
    echo "::error::showed $((steps + 1)) of $images images (the first failed, or the run stopped)"
    printf '%s\n' "$out" | tail -20
    exit 1
fi
echo "smoke test: all $images images shown by the packaged app"
