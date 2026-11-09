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
# set), so neither the user's settings, bookmarks nor clipboard are touched. The update check is off and
# Check for Updates asks a closed local port, so no run reaches GitHub. Nothing it starts can
# reach the desktop either: xdg-open, gdbus and dbus-send (Show in Folder, the About window's links)
# are stubs that list what they were asked once it ends, and DISPLAY, WAYLAND_DISPLAY and the session
# bus are unset. Commands that change files (Delete, Rename, saving an edit) still change them: drive
# a copy. File dialogs (Ctrl+O, Save) are not supported by the platform and fail the run.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${MIRADA_NO_BUILD:-}" != 1 ]; then
    ./mvnw -q -B test-compile
fi

work="$(mktemp -d)"
# What the stubs were asked to run, printed at the end: Mirada discards their output.
trap 'cat "$work/not-run.log" >&2 2>/dev/null || true; rm -rf "$work"' EXIT

if [ -z "${MIRADA_CONFIG_DIR:-}" ]; then
    export MIRADA_CONFIG_DIR="$work/config"
fi

mkdir "$work/bin"
for program in xdg-open gdbus dbus-send; do
    printf '#!/bin/sh\necho "[headless] not run: %s $*" >> "%s"\n' "$program" "$work/not-run.log" > "$work/bin/$program"
    chmod +x "$work/bin/$program"
done

PATH="$work/bin:$PATH" \
MIRADA_MAIN=com.mirada/com.mirada.ui.HeadlessDriver \
MIRADA_PATCH=target/test-classes \
    env -u DISPLAY -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS \
    scripts/dev-run.sh -Dglass.platform=Headless -Dprism.order=sw -Dmirada.updateCheck=off \
        -Dmirada.updateUrl=http://127.0.0.1:9/releases/latest "$@"
