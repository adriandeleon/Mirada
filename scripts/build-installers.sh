#!/usr/bin/env bash
# Wraps the finished app image (codec bundle and AOT cache inside, macOS bundle re-signed) into this
# platform's installers, Termina's way: jpackage's --app-image mode packages exactly what it is
# handed, where building an installer from scratch would make a second, untrained image.
#
#   macOS    .dmg
#   Windows  .msi
#   Linux    .deb, .rpm, .tar.gz and .AppImage (build-appimage.sh)
#
# Each registers Mirada for its image types (packaging/file-types.txt), so a double-click opens it.
# On Linux and Windows the file arrives as Mirada's argument; macOS sends an "open" event, which
# MacOpenFiles observes.
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
        --copyright "Copyright (c) 2026 Adrián Arturo De León Saldivar"
        --about-url "https://github.com/adriandeleon/Mirada")
[ -n "$icon" ] && [ -f "$icon" ] && common+=(--icon "$icon")

# One properties file per extension, the form jpackage takes (packaging/file-types.txt).
associations="$(mktemp -d)"
while read -r extension mime label _; do
    case "$extension" in '' | '#'*) continue ;; esac
    cat > "$associations/$extension.properties" <<PROPS
extension=$extension
mime-type=$mime
description=${label//_/ } image
PROPS
    common+=(--file-associations "$associations/$extension.properties")
done < "$root/packaging/file-types.txt"

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
#
# Its dependencies become exactly the given list. jpackage adds every package that owns a library
# any file in the image reaches, by asking the build host's dpkg: for a native image that is the
# whole GTK closure under this host's names (libgtk-3-0t64 alone), which Debian 12 and Ubuntu 22.04
# cannot satisfy.
fix_deb() {
    local deb="$1" deps="$2" root size
    root="$(mktemp -d)"
    dpkg-deb -R "$deb" "$root"
    sed -i "s/^Depends: .*/Depends: $deps/" "$root/DEBIAN/control"
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
        # hdiutil, not jpackage: jpackage's dmg wants the Java runtime inside the bundle, which the
        # native image no longer has. The bundle is finished (document types, signature) by
        # fix-mac-bundle.sh; this only puts it on a disk image beside a link to /Applications.
        echo "[installers] building dmg"
        staging="$(mktemp -d)"
        cp -R "$image" "$staging/"
        ln -s /Applications "$staging/Applications"
        # hdiutil fails now and then on GitHub's Mac runners ("Resource busy"), and passes when
        # simply run again.
        for attempt in 1 2 3 4 5; do
            if hdiutil create -volname Mirada -srcfolder "$staging" -fs HFS+ -format UDZO -ov \
                "$dest/Mirada-$version.dmg"; then
                break
            fi
            [ "$attempt" -lt 5 ] || { echo "::error::[installers] hdiutil failed five times" >&2; exit 1; }
            echo "[installers] hdiutil failed, trying again ($attempt of 5)" >&2
            sleep 10
        done
        rm -rf "$staging"
        echo "[installers] dmg OK"
        ;;
    Linux)
        # The resource directory overrides the menu entry and the icon (packaging/linux/NOTES.md).
        [ -n "$icon" ] && [ -f "$icon" ] || { echo "::error::[installers] no icon at '$icon'" >&2; exit 1; }
        resources="$(mktemp -d)"
        cp "$root/packaging/linux/Mirada.desktop" "$root/packaging/linux/postinst" \
            "$root/packaging/linux/prerm" "$resources/"
        cp "$icon" "$resources/Mirada.png"

        # What the executable and AWT's libraries link (readelf -d; the native image has JavaFX in
        # it, the JVM image extracts JavaFX's natives at run time, and these cover both). Debian
        # names with the t64 rename as an alternative, so one deb installs on Debian 12 and 13 and
        # Ubuntu 22.04 and 24.04; the rpm by shared-library capability. The codec bundle needs only
        # libc and libstdc++ (scripts/check-codecs.sh).
        deb_deps="libc6, libgtk-3-0t64 | libgtk-3-0, libglib2.0-0t64 | libglib2.0-0, libgdk-pixbuf-2.0-0"
        deb_deps+=", libcairo2, libpango-1.0-0, libpangoft2-1.0-0, libfontconfig1, libfreetype6"
        deb_deps+=", libgl1, libx11-6, libxext6, libxi6, libxrender1, libxtst6, libxxf86vm1"
        deb_deps+=", libstdc++6, zlib1g"
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
        fix_deb "$(ls "$dest"/mirada_*.deb)" "$deb_deps"
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
        bash "$root/scripts/build-appimage.sh" "$image" "$version" "$dest" "$icon" ||
            { echo "::error::[installers] AppImage failed" >&2; exit 1; }
        ;;
    *)
        # jpackage takes the Add/Remove Programs icon from the main launcher and stores the whole
        # file in the MSI's Icon table, uncompressed: harmless for its 100 KB launcher, 78 MB twice
        # over for our native image. Its main.wxs reads the file from the JpIcon variable, which
        # overrides.wxi (included first, a stub for exactly this) points at the .ico instead.
        case "$icon" in *.ico) ;; *) echo "::error::[installers] the msi needs an .ico, not '$icon'" >&2; exit 1 ;; esac
        msi_resources="$(mktemp -d)"
        cat > "$msi_resources/overrides.wxi" <<WXI
<?xml version="1.0" encoding="utf-8"?>
<Include xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <?ifdef JpIcon ?><?undef JpIcon ?><?endif?>
  <?define JpIcon = "$(cygpath -w "$icon")" ?>
</Include>
WXI
        # A fixed upgrade UUID makes the next MSI replace this one rather than install beside it.
        # Generated once; changing it strands every existing install.
        build msi \
            --win-menu \
            --win-menu-group Mirada \
            --win-shortcut \
            --win-dir-chooser \
            --win-per-user-install \
            --win-upgrade-uuid "390e8e75-fb71-413f-9f7d-a70507695e4c" \
            --resource-dir "$msi_resources"
        ;;
esac

echo "[installers] produced:"
ls -la "$dest"
