#!/usr/bin/env bash
# Wraps the finished app image (codec bundle and AOT cache inside, macOS bundle re-signed) into this
# platform's installers, Termina's way: jpackage's --app-image mode packages exactly what it is
# handed, where building an installer from scratch would make a second, untrained image.
#
#   macOS    .dmg
#   Windows  .msi
#   Linux    .deb, .rpm and .tar.gz
#
# Each registers Mirada for its image types (file associations), so a double-click opens it. On
# Linux and Windows the file arrives as Mirada's argument; macOS sends an "open" event instead,
# which Mirada does not handle yet (AGENTS.md, "Not done").
#
# Usage: build-installers.sh <app-image> <app-version> <dest-dir> <icon>
set -euo pipefail

image="${1:?app image required}"
version="${2:?app version required}"
dest="${3:?destination required}"
icon="${4:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"

[ -d "$image" ] || { echo "no app image at $image" >&2; exit 1; }
mkdir -p "$dest"

description="A fast, folder-oriented image viewer"
common=(--app-image "$image" --name Mirada --app-version "$version" --dest "$dest"
        --vendor "Adrian De Leon"
        --description "$description"
        --copyright "Copyright 2026 Adrian De Leon"
        --about-url "https://github.com/adriandeleon/Mirada")
[ -n "$icon" ] && [ -f "$icon" ] && common+=(--icon "$icon")

# One properties file per extension, the form jpackage takes. The MIME types must match MimeType in
# packaging/linux/Mirada.desktop.
associations="$(mktemp -d)"
while read -r extension mime label; do
    cat > "$associations/$extension.properties" <<PROPS
extension=$extension
mime-type=$mime
description=${label//_/ } image
mac.CFBundleTypeRole=Viewer
mac.LSHandlerRank=Alternate
PROPS
    common+=(--file-associations "$associations/$extension.properties")
done <<'TYPES'
jpg  image/jpeg JPEG
jpeg image/jpeg JPEG
png  image/png PNG
gif  image/gif GIF
bmp  image/bmp BMP
webp image/webp WebP
tif  image/tiff TIFF
tiff image/tiff TIFF
psd  image/vnd.adobe.photoshop Photoshop
heic image/heic HEIC
heif image/heif HEIF
avif image/avif AVIF
jxl  image/jxl JPEG_XL
TYPES

build() {
    local type="$1"; shift
    echo "[installers] building $type"
    if jpackage --type "$type" "${common[@]}" "$@"; then
        echo "[installers] $type OK"
    else
        # Named, not swallowed: a release that quietly ships two installers instead of three is a
        # bug report from whichever platform was missing.
        echo "::error::[installers] $type failed" >&2
        return 1
    fi
}

# The finished deb, rebuilt with the menu entry as a file it owns: /usr/share/applications, where
# desktops and dpkg's triggers find it, instead of the `xdg-desktop-menu install` jpackage's postinst
# runs and packaging/linux/postinst leaves out (it fails the install on a system with no menu
# directory). The entry is jpackage's, tokens already filled in.
own_menu_entry() {
    local deb="$1" root size
    root="$(mktemp -d)"
    dpkg-deb -R "$deb" "$root"
    install -D -m 644 "$root/opt/mirada/lib/mirada-Mirada.desktop" "$root/usr/share/applications/mirada.desktop"
    # The control file's sums and size describe the contents and are rewritten to match them.
    (cd "$root" && find . -path ./DEBIAN -prune -o -type f -printf '%P\0' | LC_ALL=C sort -z |
        xargs -0 md5sum) > "$root/DEBIAN/md5sums"
    size=$(du -sk --exclude=DEBIAN "$root" | cut -f1)
    sed -i "s/^Installed-Size: .*/Installed-Size: $size/" "$root/DEBIAN/control"
    dpkg-deb --root-owner-group --build "$root" "$deb" > /dev/null
    rm -rf "$root"
}

# The image as it is, for a system without dpkg or rpm, or a user without root.
build_tarball() {
    local out="$dest/mirada-$version-linux.tar.gz"
    echo "[installers] building tar.gz"
    tar --create --gzip --file "$out" --sort=name --owner=0 --group=0 --numeric-owner --mode=go-w \
        --transform "flags=rh;s,^$(basename "$image"),mirada-$version," \
        -C "$(dirname "$image")" "$(basename "$image")"
    echo "[installers] tar.gz OK"
}

case "$(uname -s)" in
    Darwin)
        build dmg --mac-package-name Mirada
        ;;
    Linux)
        # The resource directory overrides the menu entry and the icon (packaging/linux/NOTES.md).
        [ -n "$icon" ] && [ -f "$icon" ] || { echo "::error::[installers] no icon at '$icon'" >&2; exit 1; }
        resources="$(mktemp -d)"
        cp "$root/packaging/linux/Mirada.desktop" "$root/packaging/linux/postinst" \
            "$root/packaging/linux/prerm" "$resources/"
        cp "$icon" "$resources/Mirada.png"

        # What JavaFX links (Termina's list, from readelf of its natives). They are extracted from
        # the module jars at run time, so the bundlers' library scan never sees them. Debian names
        # with the t64 rename as an alternative, so one deb installs on Debian 12 and 13 and Ubuntu
        # 22.04 and 24.04; the rpm by shared-library capability. The codec bundle needs only libc
        # and libstdc++ (scripts/check-codecs.sh).
        deb_deps="libgtk-3-0t64 | libgtk-3-0, libgl1, libx11-6, libxxf86vm1, libxtst6"
        deb_deps+=", libpango-1.0-0, libpangoft2-1.0-0, libfontconfig1, libfreetype6, libstdc++6"
        rpm_deps="libgtk-3.so.0()(64bit), libGL.so.1()(64bit), libX11.so.6()(64bit)"
        rpm_deps+=", libXxf86vm.so.1()(64bit), libXtst.so.6()(64bit), libpangoft2-1.0.so.0()(64bit)"
        rpm_deps+=", libfontconfig.so.1()(64bit), libfreetype.so.6()(64bit), libstdc++.so.6()(64bit)"

        build deb \
            --linux-package-name mirada \
            --linux-deb-maintainer "adrian.deleon@gmail.com" \
            --linux-menu-group "Graphics;Viewer" \
            --linux-shortcut \
            --linux-package-deps "$deb_deps" \
            --license-file "$root/LICENSE" \
            --resource-dir "$resources"
        own_menu_entry "$(ls "$dest"/mirada_*.deb)"
        if command -v rpmbuild >/dev/null 2>&1; then
            build rpm \
                --linux-package-name mirada \
                --linux-rpm-license-type MIT \
                --linux-menu-group "Graphics;Viewer" \
                --linux-shortcut \
                --linux-package-deps "$rpm_deps" \
                --license-file "$root/LICENSE" \
                --resource-dir "$resources"
        else
            echo "::warning::[installers] no rpmbuild here, so no rpm (CI and the release install it)" >&2
        fi
        build_tarball
        ;;
    *)
        # A fixed upgrade UUID makes the next MSI replace this one rather than install beside it.
        # Generated once; changing it strands every existing install.
        build msi \
            --win-menu \
            --win-menu-group Mirada \
            --win-shortcut \
            --win-dir-chooser \
            --win-per-user-install \
            --win-upgrade-uuid "390e8e75-fb71-413f-9f7d-a70507695e4c"
        ;;
esac

echo "[installers] produced:"
ls -la "$dest"
