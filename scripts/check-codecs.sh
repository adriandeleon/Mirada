#!/usr/bin/env bash
# Fails if any library in a codec bundle depends on something that is neither in the bundle nor part
# of the operating system's C/C++ runtime: a stray system libaom or libx265 picked up at build time
# would work on the build machine and nowhere else (and x265 is GPL).
#
# Usage: scripts/check-codecs.sh <codecs-dir>
set -euo pipefail
dir=$1
status=0

bundled() { [ -e "$dir/$1" ]; }

case "$(uname -s)" in
    Linux)
        allowed='^(libc|libm|libdl|libpthread|librt|libgcc_s|libstdc\+\+|ld-linux.*)\.so'
        for f in "$dir"/*.so*; do
            for need in $(readelf -d "$f" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
                bundled "$need" || [[ "$need" =~ $allowed ]] || { echo "$(basename "$f") needs $need"; status=1; }
            done
            readelf -d "$f" | grep -q 'R\(UN\)\?PATH.*\$ORIGIN' || { echo "$(basename "$f") has no \$ORIGIN rpath"; status=1; }
        done
        ;;
    Darwin)
        for f in "$dir"/*.dylib; do
            for need in $(otool -L "$f" | tail -n +2 | awk '{print $1}'); do
                case "$need" in
                    /usr/lib/* | /System/*) ;;
                    @rpath/* | @loader_path/*) bundled "$(basename "$need")" || { echo "$(basename "$f") needs missing $need"; status=1; } ;;
                    *) echo "$(basename "$f") needs $need"; status=1 ;;
                esac
            done
        done
        ;;
    MINGW* | MSYS* | CYGWIN*)
        # dumpbin comes with MSVC (the workflow puts it on the path); its dependents are the indented
        # *.dll lines.
        for f in "$dir"/*.dll; do
            for need in $(dumpbin -nologo -dependents "$(cygpath -w "$f")" | tr -d '\r' | sed -n 's/^ \+\([^ ]*\.dll\)$/\1/Ip'); do
                bundled "$need" && continue
                case "$(echo "$need" | tr '[:upper:]' '[:lower:]')" in
                    kernel32.dll | user32.dll | advapi32.dll | msvcp140*.dll | vcruntime140*.dll | api-ms-win-*.dll | ucrtbase.dll) ;;
                    *) echo "$(basename "$f") needs $need"; status=1 ;;
                esac
            done
        done
        ;;
esac

[ $status = 0 ] && echo "codec bundle is self-contained"
exit $status
