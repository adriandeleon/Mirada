#!/usr/bin/env bash
# Records Mirada's reachability metadata for the native image (the pom's `native` profile) with
# GraalVM's tracing agent: reflection, resources, JNI and the FFM downcalls a run actually uses.
#
# Two tours (com.mirada.perf.Tour), with the properties sidebar and the thumbnail strip open: the
# test fixtures (one file of every format) and the format matrix (every variant: bit depths, grey,
# CMYK, alpha, compressions; scripts/make-format-matrix.sh). Together they reach every decoder,
# ImageIO's plugins and the JDK's native image code behind them (JNI), the libheif and libjxl
# bindings, the EXIF reader, the stylesheets and the icon. The second run merges into the first.
# It runs from the class path, as the native image is built. JavaFX's own metadata comes from StaticFX.
#
# Afterwards: JavaFX's own native libraries are dropped from the resources (StaticFX links them in), and
# both Cupertino themes are kept, whichever one the run happened to use.
#
# Each platform's AWT reaches its own JNI (Windows' calls back into java.awt.Toolkit as it loads, for
# one), so the committed file is the union of runs on Linux, macOS and Windows. MIRADA_METADATA_MERGE=1
# merges this run into the committed file instead of replacing it; the Native metadata workflow does
# that on macOS and Windows, and native-image-configure joins the results (docs/native-image.md).
#
# Usage: GRAALVM_HOME=~/.sdkman/candidates/java/25.4.4.1+1-graal scripts/native-metadata.sh
set -euo pipefail

graal="${GRAALVM_HOME:?set GRAALVM_HOME to an Oracle GraalVM 25.4}"
cd "$(dirname "$0")/.."
out=src/main/resources/META-INF/native-image/com.mirada/mirada

./mvnw -q -B compile
./mvnw -q -B dependency:build-classpath -Dmdep.outputFile=target/native-classpath.txt -Dmdep.includeScope=runtime
separator=":"
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) separator=";" ;; esac
classpath="target/classes$separator$(cat target/native-classpath.txt)"

config="$(mktemp -d)"
trap 'rm -rf "$config"' EXIT
printf 'start.info=true\nstart.thumbnails=true\n' > "$config/settings.properties"

mkdir -p "$out"
agent="config-output-dir"
[ "${MIRADA_METADATA_MERGE:-}" = 1 ] && agent="config-merge-dir"
for folder in src/test/resources/com/mirada/image src/test/resources/com/mirada/matrix; do
    MIRADA_CONFIG_DIR="$config" MIRADA_PERF_TOUR=1 MIRADA_PERF_TOUR_PAUSE=600 \
        "$graal/bin/java" \
        -agentlib:native-image-agent=$agent="$out" \
        --enable-native-access=ALL-UNNAMED \
        -Dmirada.codecs=target/codecs \
        -cp "$classpath" com.mirada.Launcher "$folder"
    agent="config-merge-dir"
done

python="$(command -v python3 || command -v python)"
"$python" - "$out/reachability-metadata.json" <<'PY'
import json, sys
path = sys.argv[1]
data = json.load(open(path))
native = (".so", ".dylib", ".dll")
resources = [r for r in data.get("resources", []) if not str(r.get("glob", "")).endswith(native)]
globs = {r.get("glob") for r in resources}
for theme in ("atlantafx/base/theme/cupertino-light.bss", "atlantafx/base/theme/cupertino-dark.bss"):
    if theme not in globs:
        resources.append({"glob": theme})
data["resources"] = resources
json.dump(data, open(path, "w"), indent=2)
PY
echo "metadata in $out:"
ls -la "$out"
