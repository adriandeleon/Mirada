#!/usr/bin/env bash
# Replaces jpackage's native launcher in a Linux app image with a shell script that starts the
# bundled java with the same options.
#
# Why: on every start, jpackage's Linux launcher runs `rpm -qf` and `dpkg -S` on its own path to learn
# whether a package installed it (JPACKAGE_DEBUG=true shows the calls). `dpkg -S` searches the whole
# package database — measured at about 300 ms, half of Mirada's start-up with the AOT cache. The
# script reads nothing at run time: the options are copied from the launcher's own Mirada.cfg here,
# so the pom stays their one source.
#
# Usage: linux-launcher.sh <app-image-dir> <name>
set -euo pipefail

image="${1:?app image directory required}"
name="${2:-Mirada}"
[ "$(uname -s)" = Linux ] || exit 0

cfg="$image/lib/app/$name.cfg"
launcher="$image/bin/$name"
[ -f "$cfg" ] && [ -x "$launcher" ] || { echo "[launcher] no $cfg or $launcher" >&2; exit 1; }

main=$(sed -n 's/^app.mainmodule=//p' "$cfg")
options=""
while IFS= read -r option; do
    # $APPDIR stays a variable, resolved from the script's own location when it runs.
    options="$options \"$(printf '%s' "$option" | sed 's/\$APPDIR/$APPDIR/g; s/"/\\"/g')\""
done < <(sed -n 's/^java-options=//p' "$cfg")

cat > "$launcher" <<SCRIPT
#!/bin/sh
# Mirada's launcher, written by scripts/linux-launcher.sh in place of jpackage's native one, which
# spends about 300 ms per start asking dpkg and rpm whether they installed it.
self=\$(readlink -f "\$0")
image=\$(dirname "\$(dirname "\$self")")
APPDIR="\$image/lib/app"
exec "\$image/lib/runtime/bin/java"$options -Djpackage.app-path="\$self" -m $main "\$@"
SCRIPT
chmod 755 "$launcher"
echo "[launcher] $launcher is now a script"
