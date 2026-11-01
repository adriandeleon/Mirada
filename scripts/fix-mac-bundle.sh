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
# The image types it opens (packaging/file-types.txt), as Finder reads them: one document type each,
# Mirada a viewer of them and not their default owner (LSHandlerRank Alternate).
root="$(cd "$(dirname "$0")/.." && pwd)"
buddy() { /usr/libexec/PlistBuddy -c "$1" "$plist"; }
buddy "Delete :CFBundleDocumentTypes" 2>/dev/null || true
buddy "Add :CFBundleDocumentTypes array"
i=0
while read -r extension mime label uti; do
    case "$extension" in '' | '#'*) continue ;; esac
    entry=":CFBundleDocumentTypes:$i"
    buddy "Add $entry dict"
    buddy "Add $entry:CFBundleTypeName string ${label//_/ } image"
    buddy "Add $entry:CFBundleTypeRole string Viewer"
    buddy "Add $entry:LSHandlerRank string Alternate"
    buddy "Add $entry:LSItemContentTypes array"
    buddy "Add $entry:LSItemContentTypes:0 string $uti"
    buddy "Add $entry:CFBundleTypeExtensions array"
    buddy "Add $entry:CFBundleTypeExtensions:0 string $extension"
    buddy "Add $entry:CFBundleTypeMIMETypes array"
    buddy "Add $entry:CFBundleTypeMIMETypes:0 string $mime"
    i=$((i + 1))
done < "$root/packaging/file-types.txt"
echo "document types: $i"

# Inside out: every loose library first (the codecs, and in a native image the JDK's AWT libraries
# beside the executable), then the bundle, whose seal covers them.
find "$app/Contents" -type f -name "*.dylib" -print0 | xargs -0 codesign --force --sign -
codesign --force --deep --sign - "$app"
codesign --verify --deep --verbose=2 "$app"
echo "bundle re-signed and verified"
