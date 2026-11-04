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
    # A file, not the folder: LaunchServices hands an app only the kinds it registered for, and
    # Mirada registers image types (packaging/file-types.txt). The first in name order, so the tour
    # still steps through every image after it.
    first=$(find "$folder" -maxdepth 1 -type f | LC_ALL=C sort | head -1)
    log=$(mktemp)
    open -W -n --stdout "$log" --stderr "$log" \
        --env MIRADA_PERF=1 --env MIRADA_PERF_TOUR=1 --env MIRADA_PERF_T0="$start" \
        -a "$bundle" "$first" &
    waiting=$!
    waited=0
    retried=""
    while [ "$waited" -lt 120 ] && kill -0 "$waiting" 2>/dev/null; do
        sleep 1
        waited=$((waited + 1))
        # Not there after 30 s: lost at launch, or never delivered? A second open goes to the
        # running instance, as a double-click while Mirada is open does.
        if [ "$waited" = 30 ] && ! grep -q '^\[perf\] open-files' "$log"; then
            echo "[smoke] nothing opened after 30s: opening $(basename "$first") again in the running app"
            open -a "$bundle" "$first" || echo "[smoke] the second open failed: $?"
            retried=1
        fi
    done
    if kill -0 "$waiting" 2>/dev/null; then
        echo "[smoke] still running after ${waited}s: stopped"
        pkill -f "$bundle/Contents/MacOS/" || true
        kill "$waiting" 2>/dev/null || true
    fi
    wait "$waiting" 2>/dev/null && status=0 || status=$?
    echo "[smoke] open -a $(basename "$bundle") $(basename "$first"): exit $status after ${waited}s"
    out=$(cat "$log")
    rm -f "$log"
    printf '%s\n' "$out" | grep -E '^\[perf\] (open-file|glass)|^\[mirada\]' || true
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
# The file that launched the app must arrive with the launch. The JVM image (Intel) misses it:
# jpackage's launcher starts Java on a second thread while the main thread waits without AppKit,
# and the event comes and goes in between. It opens files sent to it once running, which is what
# the retry shows; the native image must not need the retry.
if [ -n "${retried:-}" ]; then
    if [ -d "$bundle/Contents/runtime" ]; then
        echo "::warning::the JVM image missed the file it was launched with; the running app received it"
    else
        echo "::error::the file that launched the app never arrived; only the running app received it"
        exit 1
    fi
fi
echo "smoke test: all $images images shown by the packaged app"
