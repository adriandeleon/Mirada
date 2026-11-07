#!/usr/bin/env bash
#
# Runs Mirada on JavaFX's Headless platform and drives it, with no window on the desktop: the way to
# look at the UI while someone is working at this machine. Arguments up to -- are as for dev-run.sh
# (JVM options, then a file or folder); each argument after -- is one step for
# src/test/java/com/mirada/ui/HeadlessDriver.java, which lists them:
#
#   scripts/headless-run.sh ~/Pictures -- 'key C-S-p' 'type rotate' 'shot /tmp/palette.png' 'key Escape'
#
# Software rendering, a headless clipboard, and a throwaway settings folder (MIRADA_CONFIG_DIR, unless
# set), so neither the user's settings, bookmarks nor clipboard are touched.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${MIRADA_NO_BUILD:-}" != 1 ]; then
    ./mvnw -q -B test-compile
fi

if [ -z "${MIRADA_CONFIG_DIR:-}" ]; then
    MIRADA_CONFIG_DIR="$(mktemp -d)"
    trap 'rm -rf "$MIRADA_CONFIG_DIR"' EXIT
    export MIRADA_CONFIG_DIR
fi

MIRADA_MAIN=com.mirada/com.mirada.ui.HeadlessDriver \
MIRADA_PATCH=target/test-classes \
    scripts/dev-run.sh -Dglass.platform=Headless -Dprism.order=sw "$@"
