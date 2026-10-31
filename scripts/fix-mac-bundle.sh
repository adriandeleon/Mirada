#!/usr/bin/env bash
# macOS only, last of the image steps: sets the bundle's version to the real one and re-signs it.
#
# jpackage writes a bundle version with the first number bumped (macOS refuses 0.x), and signs the
# bundle ad hoc before add-codecs.sh and aot-train.sh add files to it, which breaks the seal: an
# unquarantined launch does not check it, a downloaded copy does and calls the app "damaged".
# Termina's fix-mac-bundle-version.sh.
#
# Usage: fix-mac-bundle.sh <Mirada.app> <version>
set -euo pipefail
app="${1:?app bundle path required}"
version="${2:?version required}"
[ "$(uname -s)" = "Darwin" ] || exit 0
[ -d "$app" ] || exit 0

plist="$app/Contents/Info.plist"
current=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$plist" 2>/dev/null || echo "")
if [ "$current" != "$version" ]; then
    /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $version" "$plist"
    /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $version" "$plist"
    echo "bundle version: $current -> $version"
fi
codesign --force --deep --sign - "$app"
codesign --verify --deep --verbose=2 "$app"
echo "bundle re-signed and verified"
