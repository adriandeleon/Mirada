#!/usr/bin/env bash
# Turns jpackage's app image into the native one: the StaticFX executable in place of the launcher,
# the JDK libraries the native image build left beside it (AWT, for ImageIO), and the codec bundle
# beside it too, where a native image looks for it (NativeLibraries.bundleFolder). The Java runtime
# goes; nothing runs it.
#
# jpackage's image stays the container because the installers are made from it (--app-image): its
# .jpackage.xml, the launcher's name and the file associations are what build-installers.sh's
# deb, rpm, dmg and msi carry. Only what is inside changes.
#
#   Linux    bin/Mirada,              bin/*.so,       bin/codecs
#   macOS    Contents/MacOS/Mirada,   Contents/MacOS/*.dylib, Contents/MacOS/codecs
#   Windows  Mirada.exe,              *.dll,          codecs
#
# Runs after add-codecs.sh, which put the bundle where the JVM build wants it (the app directory);
# it is moved from there. Exits at once without a native executable, so a plain -Pdist is untouched.
#
# Usage: native-app-image.sh <app-image> <dir-holding-the-native-build> [name]
set -euo pipefail

image="${1:?app image required}"
build="${2:?native build directory required}"
name="${3:-Mirada}"

case "$(uname -s)" in
    Darwin)
        exe="$build/mirada"; libs=("$build"/*.dylib)
        bin="$image/Contents/MacOS"; launcher="$bin/$name"
        appdir="$image/Contents/app"; runtime="$image/Contents/runtime"
        ;;
    Linux)
        exe="$build/mirada"; libs=("$build"/*.so)
        bin="$image/bin"; launcher="$bin/$name"
        appdir="$image/lib/app"; runtime="$image/lib/runtime"
        ;;
    *)
        exe="$build/mirada.exe"; libs=("$build"/*.dll)
        bin="$image"; launcher="$bin/$name.exe"
        appdir="$image/app"; runtime="$image/runtime"
        ;;
esac

[ -f "$exe" ] || { echo "[native] no native executable at $exe; the image stays a JVM one"; exit 0; }
[ -d "$bin" ] || { echo "[native] no app image at $image" >&2; exit 1; }

# The libraries are not optional: without libawt every ImageIO format (WebP, TIFF, PSD) fails, and
# only when one is opened.
[ -e "${libs[0]}" ] || { echo "[native] no JDK libraries beside $exe" >&2; exit 1; }

# jpackage's launcher and its helper library (macOS) go with the runtime they start.
rm -f "$launcher" "$bin/libapplauncher.dylib" "$image/lib/libapplauncher.so"
cp "$exe" "$launcher"
chmod 755 "$launcher"
cp "${libs[@]}" "$bin/"

if [ -d "$appdir/codecs" ]; then
    rm -rf "$bin/codecs"
    mv "$appdir/codecs" "$bin/codecs"
fi
rm -rf "$runtime"

echo "[native] $launcher is the native image, with ${#libs[@]} JDK libraries and the codecs beside it"
du -sh "$image" | sed 's/^/[native] image: /'
