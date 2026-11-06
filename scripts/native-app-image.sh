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
#   Windows  Mirada.exe,              *.dll,          codecs, and the Visual C++ runtime
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
# codesign takes everything in Contents/MacOS for code and refuses a bundle with text there: the
# bundle's licences and versions go to Contents/Resources/codecs (NativeLibraries.versionsFile).
if [ "$(uname -s)" = Darwin ] && [ -d "$bin/codecs" ]; then
    mkdir -p "$image/Contents/Resources/codecs"
    for text in "$bin/codecs/licenses" "$bin/codecs/VERSIONS.txt"; do
        [ -e "$text" ] && mv "$text" "$image/Contents/Resources/codecs/"
    done
fi
rm -rf "$runtime"

# Windows: the Visual C++ runtime, beside the executable. The executable, AWT's DLLs, libheif and
# libjxl all import vcruntime140.dll (msvcp140.dll and vcruntime140_1.dll too), which Windows does
# not include; GitHub's runners have it installed, a clean Windows does not, and 0.2.0's MSI failed
# to start there (STATUS_DLL_NOT_FOUND). Visual Studio's redistributable folder holds the copies
# Microsoft lets an application carry; the newest installed toolset's, or MIRADA_VC_REDIST's.
case "$(uname -s)" in
    Darwin | Linux) ;;
    *)
        crt="${MIRADA_VC_REDIST:-}"
        if [ -z "$crt" ]; then
            vswhere="/c/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
            vs="$("$vswhere" -latest -products '*' -property installationPath | tr -d '\r')"
            crt="$(ls -d "$(cygpath -u "$vs")"/VC/Redist/MSVC/*/x64/Microsoft.VC14*.CRT | sort -V | tail -1)"
        fi
        for dll in vcruntime140.dll vcruntime140_1.dll msvcp140.dll; do
            [ -f "$crt/$dll" ] || { echo "[native] no $dll in $crt" >&2; exit 1; }
            cp "$crt/$dll" "$bin/"
        done
        echo "[native] the Visual C++ runtime from $crt"
        python="$(command -v python3 || command -v python)"
        "$python" "$(dirname "$0")/check-windows-dlls.py" "$image"
        ;;
esac

echo "[native] $launcher is the native image, with ${#libs[@]} JDK libraries and the codecs beside it"
du -sh "$image" | sed 's/^/[native] image: /'
