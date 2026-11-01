#!/usr/bin/env bash
# Checks the built .deb and .rpm, not the tree: every mistake here produces a package that installs
# and looks wrong (Termina's packages shipped a stock Java icon and a menu entry jpackage ignored).
#
#   - the launcher is the script (scripts/linux-launcher.sh), not jpackage's slow native one
#   - the codec bundle and the AOT cache are inside
#   - the menu entry is ours: passes the file (%f), lists the image types, names the window class,
#     and passes desktop-file-validate
#   - the icon is branding/mirada.png, byte for byte
#   - the dependencies name GTK and nothing from the sound stack
#
# Usage: check-linux-packages.sh <installers-dir> <icon.png>
set -euo pipefail

dir="${1:?installers directory required}"
icon="${2:?icon required}"
# Absolute: the rpm is unpacked from inside a temporary directory.
dir="$(cd "$dir" && pwd)"
icon="$(cd "$(dirname "$icon")" && pwd)/$(basename "$icon")"
failures=0
fail() { echo "::error::$1"; failures=$((failures + 1)); }

check_tree() { # <package-label> <extracted-root> <depends-text>
    local label="$1" tree="$2" deps="$3" app="$2/opt/mirada"
    head -c 200 "$app/bin/Mirada" | grep -q 'linux-launcher.sh' ||
        fail "$label: bin/Mirada is not the script launcher"
    for lib in libheif.so.1 libjxl.so.0.12 libjxl_threads.so.0.12; do
        [ -f "$app/lib/app/codecs/$lib" ] || fail "$label: codec bundle lacks $lib"
    done
    [ -s "$app/lib/app/mirada.aot" ] || fail "$label: no AOT cache"
    [ -f "$app/lib/app/NOTICE" ] || fail "$label: no NOTICE"

    local entry="$app/lib/mirada-Mirada.desktop"
    if [ -f "$entry" ]; then
        grep -q '^Exec=.* %f$' "$entry" || fail "$label: menu entry does not pass the file (%f)"
        for mime in image/jpeg image/heic image/avif image/jxl image/webp; do
            grep -q "^MimeType=.*$mime;" "$entry" || fail "$label: menu entry lacks $mime"
        done
        grep -q '^StartupWMClass=com.mirada.App$' "$entry" || fail "$label: no StartupWMClass"
        grep -q 'APPLICATION_' "$entry" && fail "$label: menu entry has unsubstituted tokens"
        if command -v desktop-file-validate >/dev/null; then
            desktop-file-validate "$entry" || fail "$label: desktop-file-validate"
        fi
    else
        fail "$label: no menu entry"
    fi

    if [ "$(sha256sum < "$app/lib/Mirada.png")" != "$(sha256sum < "$icon")" ]; then
        fail "$label: lib/Mirada.png is not $icon (the bundler's stock icon?)"
    fi
    printf '%s' "$deps" | grep -q 'gtk-3' || fail "$label: dependencies do not name GTK 3"
    printf '%s' "$deps" | grep -q -i 'asound' && fail "$label: depends on ALSA (libjsound back in the image?)"
    echo "$label: checked"
}

shopt -s nullglob
debs=("$dir"/*.deb)
rpms=("$dir"/*.rpm)
[ ${#debs[@]} -gt 0 ] || fail "no .deb in $dir"

for deb in "${debs[@]}"; do
    tree="$(mktemp -d)"
    dpkg-deb -x "$deb" "$tree"
    check_tree "$(basename "$deb")" "$tree" "$(dpkg-deb -f "$deb" Depends)"
    rm -rf "$tree"
done
for rpm in "${rpms[@]}"; do
    tree="$(mktemp -d)"
    (cd "$tree" && rpm2cpio "$rpm" | cpio -idm --quiet)
    check_tree "$(basename "$rpm")" "$tree" "$(rpm -qp --requires "$rpm" 2>/dev/null)"
    rm -rf "$tree"
done

[ "$failures" = 0 ] || { echo "$failures problem(s) with the Linux packages"; exit 1; }
echo "Linux packages: all checks passed"
