#!/usr/bin/env bash
# Copies the native codec bundle (libheif, libjxl: see build-codecs.sh) into a jpackage app image,
# into the app directory the launcher's -Dmirada.codecs=$APPDIR/codecs points at.
#
# A missing bundle is an error, not a skip: an image without it opens HEIC, AVIF and JPEG XL only
# where the user happens to have the libraries installed, and nothing else in the build would say so.
#
# Usage: add-codecs.sh <app-image-dir> <codecs-dir>
set -euo pipefail

image="${1:?app image directory required}"
codecs="${2:?codecs directory required}"

# jpackage's layouts differ: Linux keeps the app directory under lib/.
case "$(uname -s)" in
    Darwin) appdir="$image/Contents/app" ;;
    Linux) appdir="$image/lib/app" ;;
    *) appdir="$image/app" ;;
esac

if [ ! -d "$codecs" ] || [ -z "$(ls -A "$codecs" 2>/dev/null)" ]; then
    echo "[codecs] no codec bundle at $codecs (the build downloads it unless -Dcodecs.skip)" >&2
    exit 1
fi
mkdir -p "$appdir/codecs"
cp -R "$codecs"/. "$appdir/codecs/"
echo "[codecs] bundled into $appdir/codecs: $(du -sh "$appdir/codecs" | cut -f1)"
