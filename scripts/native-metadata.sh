#!/usr/bin/env bash
# Records Mirada's reachability metadata for the native image (the pom's `native` profile) with
# GraalVM's tracing agent: reflection, resources, JNI and the FFM downcalls a run actually uses.
#
# The run is a tour (com.mirada.perf.Tour) of the test fixtures, one file of every format, with the
# properties sidebar and the thumbnail strip open, so it reaches every decoder, ImageIO's plugins,
# the libheif and libjxl bindings, the EXIF reader, the stylesheets and the icon. It runs from the
# class path, as the native image is built. JavaFX's own metadata comes from StaticFX.
#
# Usage: GRAALVM_HOME=~/.sdkman/candidates/java/25.4.4.1+1-graal scripts/native-metadata.sh
set -euo pipefail

graal="${GRAALVM_HOME:?set GRAALVM_HOME to an Oracle GraalVM 25.4}"
cd "$(dirname "$0")/.."
out=src/main/resources/META-INF/native-image/com.mirada/mirada

./mvnw -q -B compile
./mvnw -q -B dependency:build-classpath -Dmdep.outputFile=target/native-classpath.txt -Dmdep.includeScope=runtime
classpath="target/classes:$(cat target/native-classpath.txt)"

config="$(mktemp -d)"
trap 'rm -rf "$config"' EXIT
printf 'start.info=true\nstart.thumbnails=true\n' > "$config/settings.properties"

mkdir -p "$out"
MIRADA_CONFIG_DIR="$config" MIRADA_PERF_TOUR=1 MIRADA_PERF_TOUR_PAUSE=600 \
    "$graal/bin/java" \
    -agentlib:native-image-agent=config-output-dir="$out" \
    --enable-native-access=ALL-UNNAMED \
    -Dmirada.codecs=target/codecs \
    -cp "$classpath" com.mirada.Launcher src/test/resources/com/mirada/image
echo "metadata in $out:"
ls -la "$out"
