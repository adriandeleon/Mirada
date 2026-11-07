#!/usr/bin/env bash
#
# Runs Mirada from target/classes with an explicit module path, Termina's way.
#
# `mvn javafx:run` cannot take ad-hoc JVM options (the plugin's <options> are fixed in the pom), and
# Maven's own start-up would be counted in any timing. This builds the module path itself and
# starts Java directly. Options starting with - go to the JVM; anything else (a file or folder) goes
# to Mirada:
#
#   scripts/dev-run.sh -Dmirada.tour=true ~/Pictures/IMG_0001.HEIC
#   MIRADA_NO_BUILD=1 scripts/measure-startup.sh -n 5 scripts/dev-run.sh ~/Pictures/photo.jpg
set -euo pipefail

cd "$(dirname "$0")/.."

# MIRADA_NO_BUILD=1 skips Maven, for timing runs: scripts/measure-startup.sh stamps the start before
# this script runs, so a build here would be counted as start-up.
if [ "${MIRADA_NO_BUILD:-}" != 1 ]; then
    ./mvnw -q -B compile
    # Refresh from empty: copy-dependencies adds and never removes, so after a version bump the old
    # jar sits beside the new one, both declare the same module, and whichever is listed first wins.
    rm -rf target/deps
    ./mvnw -q -B dependency:copy-dependencies -DoutputDirectory=target/deps -DincludeScope=runtime
fi

MODULE_PATH="$(python3 - <<'PY'
import os, platform

deps = 'target/deps'
machine = platform.machine()
arch = 'aarch64' if machine in ('arm64', 'aarch64') else 'x86_64'
system = platform.system()
classifier = {'Darwin': 'mac', 'Linux': 'linux', 'Windows': 'win'}.get(system, 'mac')
want = classifier + '-aarch64' if arch == 'aarch64' and classifier in ('mac', 'linux') else classifier

keep = []
for jar in sorted(os.listdir(deps)):
    if jar.startswith('javafx-'):
        # JavaFX ships a stub jar plus a platform-classifier jar holding the real classes. Both
        # declare the same module, so putting both on the path is a duplicate-module error.
        if jar.endswith('-' + want + '.jar'):
            keep.append(jar)
        continue
    keep.append(jar)
print(os.pathsep.join(os.path.join(deps, j) for j in keep))
PY
)"

case "$(uname -s)" in
    Darwin) PRISM=mtl,es2,sw ;;
    Linux)  PRISM=es2,sw ;;
    *)      PRISM=d3d,es2,sw ;;
esac

jvm=()
app=()
steps=0
for arg in "$@"; do
    # After -- (scripts/headless-run.sh's steps) everything is the program's.
    if [ "$steps" = 1 ]; then app+=("$arg"); continue; fi
    case "$arg" in
        --) steps=1; app+=("$arg") ;;
        -*) jvm+=("$arg") ;;
        *) app+=("$arg") ;;
    esac
done

# scripts/headless-run.sh starts its driver from the test classes, patched into the module.
patch=()
if [ -n "${MIRADA_PATCH:-}" ]; then patch=(--patch-module "com.mirada=$MIRADA_PATCH"); fi

# Glass's Mac package exists only on macOS (os.MacAppMenu); elsewhere exporting it would warn.
mac_exports=()
if [ "$(uname -s)" = "Darwin" ]; then mac_exports=(--add-exports=javafx.graphics/com.sun.glass.ui.mac=com.mirada); fi

exec java \
    --module-path "$MODULE_PATH:target/classes" \
    --add-modules com.mirada \
    --enable-native-access=javafx.graphics,com.mirada \
    --add-exports=javafx.graphics/com.sun.glass.ui=com.mirada \
    ${mac_exports[@]+"${mac_exports[@]}"} \
    ${patch[@]+"${patch[@]}"} \
    "-Dprism.order=$PRISM" \
    -Dmirada.codecs=target/codecs \
    ${jvm[@]+"${jvm[@]}"} \
    -m "${MIRADA_MAIN:-com.mirada/com.mirada.App}" ${app[@]+"${app[@]}"}
