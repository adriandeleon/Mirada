#!/usr/bin/env bash
# Wraps the finished Linux app image into an AppImage: one file that runs on any distribution with
# GTK 3 (the same libraries the deb depends on, which an AppImage takes from the system too), for
# a user without root or a distribution without dpkg or rpm.
#
# The AppDir holds the app image at opt/mirada, as the deb installs it, with AppRun starting
# opt/mirada/bin/Mirada, the menu entry (packaging/linux/Mirada.desktop, its jpackage tokens
# filled), the icon and AppStream metadata. appimagetool and the runtime it puts in front are
# release builds pinned by their SHA-256, downloaded once into ~/.cache/mirada/appimage
# (MIRADA_APPIMAGE_TOOLS moves it), never the "continuous" ones. MIRADA_APPDIR_ONLY=1 stops at
# the AppDir, left in <dest-dir>, without fetching them.
#
# Usage: build-appimage.sh <app-image> <app-version> <dest-dir> <icon.png>
set -euo pipefail

image="${1:?app image required}"
version="${2:?app version required}"
dest="${3:?destination required}"
icon="${4:?icon required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
id=io.github.adriandeleon.Mirada

tool_version=1.9.1
runtime_version=20251108
case "$(uname -m)" in
    x86_64)
        arch=x86_64
        tool_sha=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
        runtime_sha=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d
        ;;
    aarch64 | arm64)
        arch=aarch64
        tool_sha=f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158
        runtime_sha=00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444
        ;;
    *) echo "::error::[appimage] no AppImage tools for $(uname -m)" >&2; exit 1 ;;
esac

[ -x "$image/bin/Mirada" ] || { echo "::error::[appimage] no app image at $image" >&2; exit 1; }
[ -f "$icon" ] || { echo "::error::[appimage] no icon at $icon" >&2; exit 1; }
mkdir -p "$dest"

if [ -n "${MIRADA_APPDIR_ONLY:-}" ]; then
    appdir="$dest/Mirada.AppDir"
    rm -rf "$appdir"
else
    appdir="$(mktemp -d)/Mirada.AppDir"
fi
mkdir -p "$appdir/opt" "$appdir/usr/share/metainfo"
cp -a "$image" "$appdir/opt/mirada"

cat > "$appdir/AppRun" <<'RUN'
#!/bin/sh
# The AppImage's entry: Mirada as the deb installs it, at opt/mirada inside the mounted image.
here="$(dirname "$(readlink -f "$0")")"
exec "$here/opt/mirada/bin/Mirada" "$@"
RUN
chmod 755 "$appdir/AppRun"

# The deb's menu entry, its tokens filled as jpackage fills them; no TryExec, as the launcher is
# inside the image, not on the PATH.
sed -e "s|APPLICATION_NAME|Mirada|" \
    -e "s|APPLICATION_DESCRIPTION|A fast, folder-oriented image viewer|" \
    -e "s|APPLICATION_LAUNCHER|Mirada|" \
    -e "s|APPLICATION_ICON|$id|" \
    -e "/^TryExec=/d" \
    "$root/packaging/linux/Mirada.desktop" > "$appdir/$id.desktop"
echo "X-AppImage-Version=$version" >> "$appdir/$id.desktop"
if grep -q 'APPLICATION_' "$appdir/$id.desktop"; then
    echo "::error::[appimage] the menu entry has a token this does not fill" >&2
    exit 1
fi
cp "$icon" "$appdir/$id.png"
ln -s "$id.png" "$appdir/.DirIcon"

# The release's date as the changelog gives it; today's for a version not yet released.
released=$(sed -n "s/^## $version — \([0-9-]*\)\$/\1/p" "$root/CHANGELOG.md" | head -1)
[ -n "$released" ] || released=$(date -u +%F)

# AppStream, which software centres and AppImage catalogues read; the media types are the menu
# entry's, so the two cannot disagree.
mimetypes=$(sed -n 's/^MimeType=//p' "$appdir/$id.desktop" | tr ';' '\n' | sed '/^$/d' |
    sed 's|.*|    <mediatype>&</mediatype>|')
cat > "$appdir/usr/share/metainfo/$id.appdata.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<component type="desktop-application">
  <id>$id</id>
  <name>Mirada</name>
  <summary>A fast, folder-oriented image viewer</summary>
  <metadata_license>CC0-1.0</metadata_license>
  <project_license>MIT</project_license>
  <developer id="io.github.adriandeleon">
    <name>Adrian De Leon</name>
  </developer>
  <description>
    <p>
      Mirada opens a photo and the folder it is in, and steps through them as fast as the disk
      allows: JPEG, PNG, GIF, WebP, TIFF, PSD, HEIC, AVIF and JPEG XL, animations included.
      Everything is on the keyboard and in a command palette.
    </p>
  </description>
  <launchable type="desktop-id">$id.desktop</launchable>
  <url type="homepage">https://github.com/adriandeleon/Mirada</url>
  <url type="bugtracker">https://github.com/adriandeleon/Mirada/issues</url>
  <content_rating type="oars-1.1"/>
  <provides>
$mimetypes
  </provides>
  <releases>
    <release version="$version" date="$released"/>
  </releases>
</component>
XML

if [ -n "${MIRADA_APPDIR_ONLY:-}" ]; then
    echo "[appimage] AppDir at $appdir (MIRADA_APPDIR_ONLY: no AppImage)"
    exit 0
fi

# The tools, fetched once and checked every time.
tools="${MIRADA_APPIMAGE_TOOLS:-${XDG_CACHE_HOME:-$HOME/.cache}/mirada/appimage}"
mkdir -p "$tools"
fetch() { # <url> <file> <sha256>
    local url="$1" file="$tools/$2" sha="$3"
    if [ ! -f "$file" ] || [ "$(sha256sum "$file" | cut -d' ' -f1)" != "$sha" ]; then
        echo "[appimage] downloading $url"
        curl --fail --silent --show-error --location --retry 3 --output "$file.part" "$url"
        [ "$(sha256sum "$file.part" | cut -d' ' -f1)" = "$sha" ] ||
            { echo "::error::[appimage] $url does not match its pinned SHA-256" >&2; rm -f "$file.part"; exit 1; }
        mv "$file.part" "$file"
    fi
}
fetch "https://github.com/AppImage/appimagetool/releases/download/$tool_version/appimagetool-$arch.AppImage" \
    "appimagetool-$tool_version-$arch.AppImage" "$tool_sha"
fetch "https://github.com/AppImage/type2-runtime/releases/download/$runtime_version/runtime-$arch" \
    "runtime-$runtime_version-$arch" "$runtime_sha"
chmod +x "$tools/appimagetool-$tool_version-$arch.AppImage"

out="$dest/mirada-$version-$arch.AppImage"
echo "[appimage] building $(basename "$out")"
# The tool is itself an AppImage; extracting it to run needs no FUSE, which CI's runners lack.
APPIMAGE_EXTRACT_AND_RUN=1 ARCH="$arch" "$tools/appimagetool-$tool_version-$arch.AppImage" \
    --no-appstream --runtime-file "$tools/runtime-$runtime_version-$arch" "$appdir" "$out"
rm -rf "$(dirname "$appdir")"
echo "[appimage] AppImage OK"
