#!/usr/bin/env bash
# Signs the macOS bundle, inside out: every library and executable in it, then the bundle, whose
# seal covers them.
#
#   MIRADA_MAC_IDENTITY unset or "-"   ad hoc, as an unsigned build: runs where it was built, and
#                                      elsewhere only after "Open Anyway" (README)
#   MIRADA_MAC_IDENTITY="Developer ID Application: Name (TEAMID)"
#                                      the hardened runtime, a secure timestamp and the entitlements
#                                      in packaging/macos, as notarization requires
#
# The identity must be in a keychain on the search list (the release workflow imports it into a
# temporary one). Called by fix-mac-bundle.sh, last of the image steps.
#
# Usage: sign-mac.sh <Mirada.app>
set -euo pipefail
app="${1:?app bundle path required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
identity="${MIRADA_MAC_IDENTITY:--}"

options=(--force --sign "$identity")
if [ "$identity" != "-" ]; then
    options+=(--options runtime --timestamp --entitlements "$root/packaging/macos/entitlements.plist")
fi

# Every Mach-O file but the bundle's own executable: the codecs, the native image's JDK libraries,
# and for the JVM build the runtime's libraries and executables. Then the JVM build's runtime,
# itself a bundle, which seals what is in it.
main="$app/Contents/MacOS/$(basename "$app" .app)"
while IFS= read -r -d '' file; do
    if file -b "$file" | grep -q 'Mach-O'; then
        codesign "${options[@]}" "$file"
    fi
done < <(find "$app/Contents" -type f ! -path "$main" -print0)
if [ -d "$app/Contents/runtime" ]; then codesign "${options[@]}" "$app/Contents/runtime"; fi

codesign "${options[@]}" "$app"
codesign --verify --deep --strict --verbose=2 "$app"
echo "[sign] $app signed by ${identity/#-/ad hoc}"
