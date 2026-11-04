#!/usr/bin/env bash
# Records an AOT cache for the packaged application, into the image it was built from. Termina's
# script, with Mirada's training run.
#
# JDK 25's ahead-of-time cache archives the classes a run loads, so the next start skips most of the
# loading and linking. The training run opens a folder holding one small file of every format and
# steps through all of them (com.mirada.perf.Tour), so the cache holds JavaFX's scene and CSS
# machinery, every decoder, ImageIO's plugins and the native bindings — not only what showing the
# first image needed. It must render: a headless run touches almost none of that.
#
# Failure-tolerant by design: the launcher runs under AOTMode=auto, which warns and continues when
# the cache is missing or stale. A machine with no display produces no cache and a working app.
# MIRADA_REQUIRE_AOT=1 (CI) turns a missing cache into a failure.
#
# Usage: aot-train.sh <app-image-dir> <cache-file-name> <training-folder>
set -uo pipefail

image="${1:?app image directory required}"
cache_name="${2:-mirada.aot}"
training="${3:?training folder required}"

case "$(uname -s)" in
    Darwin)
        java="$image/Contents/runtime/Contents/Home/bin/java"
        appdir="$image/Contents/app"
        ;;
    Linux)
        java="$image/lib/runtime/bin/java"
        appdir="$image/lib/app"
        ;;
    *)
        java="$image/runtime/bin/java.exe"
        appdir="$image/app"
        ;;
esac

if [ ! -x "$java" ]; then
    echo "[aot] no runtime java at $java — skipping" >&2
    exit 0
fi
cache="$appdir/$cache_name"
rm -f "$cache"

# MinGW hands a Windows java a POSIX path as is; cygpath gives it the drive-letter form.
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

# The launcher's options, as the cache is only used under the same module options: macOS's
# launcher also exports glass's Mac package (the pom's glass.mac.option).
mac_exports=()
if [ "$(uname -s)" = "Darwin" ]; then mac_exports=(--add-exports=javafx.graphics/com.sun.glass.ui.mac=com.mirada); fi

# es2/sw, never the platform default: CI runners' virtual GPUs abort Metal on macOS (Termina's and
# Editora's lesson). The commas are inside option values, not separators.
# shellcheck disable=SC2054
opts=(-XX:AOTCacheOutput="$(native "$cache")"
      -Dprism.order=es2,sw
      -Dmirada.aotTrain=1
      -Dmirada.codecs="$(native "$appdir/codecs")"
      --enable-native-access=javafx.graphics,com.mirada
      --add-exports=javafx.graphics/com.sun.glass.ui=com.mirada
      ${mac_exports[@]+"${mac_exports[@]}"}
      -m com.mirada/com.mirada.App
      "$(native "$training")")

# A bound on a training run that never reaches its last image; macOS has no `timeout`.
limit=()
command -v timeout >/dev/null 2>&1 && limit=(timeout 120)

echo "[aot] training -> $cache"
if [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    ${limit[@]+"${limit[@]}"} xvfb-run -a "$java" "${opts[@]}" >/dev/null 2>&1
    status=$?
else
    ${limit[@]+"${limit[@]}"} "$java" "${opts[@]}" >/dev/null 2>&1
    status=$?
fi

if [ -s "$cache" ]; then
    echo "[aot] cache written: $(du -h "$cache" | cut -f1) (trainer exit $status)"
    exit 0
fi

echo "::warning::[aot] no cache produced (trainer exit $status); the app will start uncached" >&2
if [ "${MIRADA_REQUIRE_AOT:-0}" = "1" ]; then
    echo "[aot] MIRADA_REQUIRE_AOT=1 — failing the build" >&2
    exit 1
fi
exit 0
