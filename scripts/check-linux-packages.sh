#!/usr/bin/env bash
# Checks the built .deb and .rpm, not the tree: every mistake here produces a package that installs
# and looks wrong (Termina's packages shipped a stock Java icon and a menu entry jpackage ignored).
#
#   - native image (-Pdist,native): bin/Mirada is the executable, with AWT's libraries and the codec
#     bundle beside it, and no Java runtime
#   - JVM image (-Pdist): the launcher is the script (scripts/linux-launcher.sh), not jpackage's slow
#     native one, and the codec bundle and the AOT cache are inside
#   - the menu entry is ours: passes the file (%f), lists the image types, names the window class,
#     and passes desktop-file-validate
#   - the icon is branding/mirada.png, byte for byte
#   - the dependencies name GTK and nothing from the sound stack, and the deb names no t64 package
#     without its pre-t64 alternative (or it would not install on Debian 12 or Ubuntu 22.04)
#   - every maintainer script parses (sh -n), and the deb's prerm lets an upgrade through
#     ("failed-upgrade" exits 0): 0.1.0 and 0.2.0 shipped a prerm that did not parse, as jpackage put
#     multi-line text where a comment named one of its placeholders, and dpkg falls back to the new
#     package's prerm when the old one fails
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
    local codecs
    if [ "$(head -c 4 "$app/bin/Mirada" | tr -d '\0')" = $'\x7fELF' ]; then
        codecs="$app/bin/codecs"
        [ -f "$app/bin/libawt.so" ] || fail "$label: native image without libawt.so beside it"
        [ -d "$app/lib/runtime" ] && fail "$label: native image still carries a Java runtime"
    else
        codecs="$app/lib/app/codecs"
        head -c 200 "$app/bin/Mirada" | grep -q 'linux-launcher.sh' ||
            fail "$label: bin/Mirada is neither the native image nor the script launcher"
        [ -s "$app/lib/app/mirada.aot" ] || fail "$label: no AOT cache"
    fi
    for lib in libheif.so.1 libjxl.so.0.12 libjxl_threads.so.0.12; do
        [ -f "$codecs/$lib" ] || fail "$label: codec bundle lacks $lib"
    done
    [ -f "$app/lib/app/NOTICE" ] || fail "$label: no NOTICE"
    case "$label" in
        *.deb)
            [ -f "$tree/usr/share/applications/mirada.desktop" ] ||
                fail "$label: no /usr/share/applications/mirada.desktop (the menu entry must be a file it owns)"
            ;;
    esac

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
    case "$label" in
        *.deb)
            local bare
            bare=$(printf '%s' "$deps" | tr ',' '\n' | grep 't64' | grep -v '|' || true)
            [ -z "$bare" ] || fail "$label: t64 dependencies without an alternative:$(echo $bare)"
            ;;
    esac
    echo "$label: checked"
}

shopt -s nullglob
debs=("$dir"/*.deb)
rpms=("$dir"/*.rpm)
[ ${#debs[@]} -gt 0 ] || fail "no .deb in $dir"

for deb in "${debs[@]}"; do
    tree="$(mktemp -d)"
    # Its install script must not call xdg-desktop-menu, which fails the install wherever there is no
    # system menu directory (packaging/linux/postinst).
    if dpkg-deb -I "$deb" postinst | grep -v '^[[:space:]]*#' | grep -q 'xdg-desktop-menu'; then
        fail "$(basename "$deb"): postinst calls xdg-desktop-menu"
    fi
    for script in preinst postinst prerm postrm; do
        dpkg-deb -I "$deb" "$script" > "$tree.$script" 2>/dev/null || continue
        sh -n "$tree.$script" 2>/dev/null || fail "$(basename "$deb"): $script does not parse: $(sh -n "$tree.$script" 2>&1)"
    done
    # What dpkg runs when the installed version's prerm fails; its first lines are jpackage's helper
    # functions, the case below them does nothing for this argument.
    [ -f "$tree.prerm" ] && { sh "$tree.prerm" failed-upgrade 0.0.0 > /dev/null 2>&1 || fail "$(basename "$deb"): prerm failed-upgrade does not exit 0"; }
    rm -f "$tree".{preinst,postinst,prerm,postrm}
    dpkg-deb -x "$deb" "$tree"
    check_tree "$(basename "$deb")" "$tree" "$(dpkg-deb -f "$deb" Depends)"
    rm -rf "$tree"
done
for rpm in "${rpms[@]}"; do
    tree="$(mktemp -d)"
    (cd "$tree" && rpm2cpio "$rpm" | cpio -idm --quiet)
    # Each scriptlet as rpm runs it, under /bin/sh: a header line, then the body.
    rpm -qp --queryformat '%{PREIN}\n' "$rpm" > "$tree.pre" 2>/dev/null
    rpm -qp --queryformat '%{POSTIN}\n' "$rpm" > "$tree.post" 2>/dev/null
    rpm -qp --queryformat '%{PREUN}\n' "$rpm" > "$tree.preun" 2>/dev/null
    rpm -qp --queryformat '%{POSTUN}\n' "$rpm" > "$tree.postun" 2>/dev/null
    for script in pre post preun postun; do
        grep -qx '(none)' "$tree.$script" && continue
        sh -n "$tree.$script" 2>/dev/null || fail "$(basename "$rpm"): %$script does not parse: $(sh -n "$tree.$script" 2>&1)"
    done
    rm -f "$tree".{pre,post,preun,postun}
    check_tree "$(basename "$rpm")" "$tree" "$(rpm -qp --requires "$rpm" 2>/dev/null)"
    rm -rf "$tree"
done

[ "$failures" = 0 ] || { echo "$failures problem(s) with the Linux packages"; exit 1; }
echo "Linux packages: all checks passed"
