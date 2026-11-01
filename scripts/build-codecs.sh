#!/usr/bin/env bash
# Builds the native codecs Mirada bundles, from source, into one self-contained folder:
#
#   libheif   HEIC and AVIF, with libde265 (HEVC) and dav1d (AV1) linked in statically.
#             Decoders only: no x265 (GPL), no aom, no plugin loading.
#   libjxl    JPEG XL, plus libjxl_threads; brotli, highway and skcms from its own submodules.
#   turbojpeg JPEG, libjpeg-turbo's TurboJPEG library alone (it carries its own libjpeg): SIMD,
#             and decoding at 1/2, 1/4 or 1/8 size.
#   libwebp   WebP, still and animated: libwebp, libwebpdemux (whose animation decoder composes
#             the frames) and libsharpyuv. Decoding only; no tools.
#
# Every library finds the others beside it ($ORIGIN on Linux, @loader_path on macOS), so the folder
# works wherever it is copied. The last step checks each library's dependencies and fails on anything
# outside the system's C and C++ runtimes.
#
# Usage: scripts/build-codecs.sh [out-dir]          (default: target/codecs)
# Needs: git, cmake, ninja, meson, pkg-config, a C/C++ compiler, and nasm (x86) for dav1d's and
#        libjpeg-turbo's assembly.
# Env:   CODECS_NO_ASM=1   build without assembly (when nasm is missing; much slower AV1 and JPEG)
#        CODECS_WORK=dir   where sources and builds go (default: target/codecs-build)
set -euo pipefail

LIBDE265_TAG=v1.1.3
DAV1D_TAG=1.5.4
LIBHEIF_TAG=v1.23.7
LIBJXL_TAG=v0.12.0
LIBJPEG_TURBO_TAG=3.2.0
LIBWEBP_TAG=v1.6.0

root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$root/target/codecs}
work=${CODECS_WORK:-$root/target/codecs-build}
prefix=$work/prefix
mkdir -p "$work" "$prefix"
out=$(mkdir -p "$out" && cd "$out" && pwd)

case "$(uname -s)" in
    Linux) os=linux; rpath='$ORIGIN' ;;
    Darwin) os=macos; rpath='@loader_path' ;;
    MINGW* | MSYS* | CYGWIN*) os=windows; rpath='' ;;
    *) echo "unsupported system: $(uname -s)" >&2; exit 1 ;;
esac

asm=true
[ "${CODECS_NO_ASM:-}" = 1 ] && asm=false

cmake_common=(
    -G Ninja
    -DCMAKE_BUILD_TYPE=Release
    -DCMAKE_INSTALL_PREFIX="$prefix"
    -DCMAKE_PREFIX_PATH="$prefix"
    -DCMAKE_INSTALL_LIBDIR=lib
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    -DCMAKE_INSTALL_RPATH="$rpath"
    -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON
    -DCMAKE_MACOSX_RPATH=ON
    -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-12.0}"
)
export PKG_CONFIG_PATH="$prefix/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"

fetch() { # url tag dir
    [ -d "$work/$3" ] || git clone --quiet --depth 1 --branch "$2" "$1" "$work/$3"
}

echo "== libde265 $LIBDE265_TAG (static)"
fetch https://github.com/strukturag/libde265.git "$LIBDE265_TAG" libde265
cmake -S "$work/libde265" -B "$work/libde265/build" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=OFF -DENABLE_SDL=OFF -DENABLE_DECODER=OFF -DENABLE_ENCODER=OFF
cmake --build "$work/libde265/build" --target install

echo "== dav1d $DAV1D_TAG (static, asm=$asm)"
fetch https://code.videolan.org/videolan/dav1d.git "$DAV1D_TAG" dav1d
[ -d "$work/dav1d/build" ] || meson setup "$work/dav1d/build" "$work/dav1d" \
    --prefix="$prefix" --libdir=lib --buildtype=release --default-library=static \
    -Db_staticpic=true -Denable_tools=false -Denable_tests=false -Denable_asm=$asm
ninja -C "$work/dav1d/build" install

echo "== libheif $LIBHEIF_TAG (shared, decoders only)"
fetch https://github.com/strukturag/libheif.git "$LIBHEIF_TAG" libheif
# libde265's header declares its functions dllimport on Windows unless told the library is static.
# Through the environment rather than CMAKE_CXX_FLAGS, which would replace MSVC's defaults (/EHsc).
heif_flags=""
[ "$os" = windows ] && heif_flags="-DLIBDE265_STATIC_BUILD"
CFLAGS="${CFLAGS:-} $heif_flags" CXXFLAGS="${CXXFLAGS:-} $heif_flags" \
cmake -S "$work/libheif" -B "$work/libheif/build" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=ON -DBUILD_TESTING=OFF -DBUILD_DOCUMENTATION=OFF \
    -DWITH_EXAMPLES=OFF -DWITH_GDK_PIXBUF=OFF \
    -DENABLE_PLUGIN_LOADING=OFF -DWITH_LIBSHARPYUV=OFF \
    -DWITH_LIBDE265=ON -DWITH_LIBDE265_PLUGIN=OFF -DWITH_DAV1D=ON -DWITH_DAV1D_PLUGIN=OFF \
    -DWITH_X265=OFF -DWITH_AOM_DECODER=OFF -DWITH_AOM_ENCODER=OFF -DWITH_RAV1E=OFF \
    -DWITH_SvtEnc=OFF -DWITH_KVAZAAR=OFF -DWITH_FFMPEG_DECODER=OFF -DWITH_OpenH264_DECODER=OFF \
    -DWITH_JPEG_DECODER=OFF -DWITH_JPEG_ENCODER=OFF -DWITH_OpenJPEG_DECODER=OFF \
    -DWITH_OpenJPEG_ENCODER=OFF -DWITH_OPENJPH_ENCODER=OFF -DWITH_OPENJPH_DECODER=OFF \
    -DWITH_UNCOMPRESSED_CODEC=OFF -DWITH_HEADER_COMPRESSION=OFF -DWITH_VVDEC=OFF \
    -DWITH_VVENC=OFF -DWITH_UVG266=OFF
cmake --build "$work/libheif/build" --target install

echo "== libjxl $LIBJXL_TAG (shared)"
fetch https://github.com/libjxl/libjxl.git "$LIBJXL_TAG" libjxl
git -C "$work/libjxl" submodule update --quiet --init --depth 1 \
    third_party/brotli third_party/highway third_party/skcms
cmake -S "$work/libjxl" -B "$work/libjxl/build" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=ON -DBUILD_TESTING=OFF \
    -DJPEGXL_ENABLE_TOOLS=OFF -DJPEGXL_ENABLE_DEVTOOLS=OFF -DJPEGXL_ENABLE_JPEGLI=OFF \
    -DJPEGXL_ENABLE_JPEGLI_LIBJPEG=OFF -DJPEGXL_ENABLE_DOXYGEN=OFF -DJPEGXL_ENABLE_MANPAGES=OFF \
    -DJPEGXL_ENABLE_BENCHMARK=OFF -DJPEGXL_ENABLE_EXAMPLES=OFF -DJPEGXL_ENABLE_SJPEG=OFF \
    -DJPEGXL_ENABLE_OPENEXR=OFF -DJPEGXL_ENABLE_JNI=OFF -DJPEGXL_ENABLE_PLUGINS=OFF \
    -DJPEGXL_ENABLE_SKCMS=ON -DJPEGXL_BUNDLE_LIBPNG=OFF \
    -DJPEGXL_FORCE_SYSTEM_BROTLI=OFF -DJPEGXL_FORCE_SYSTEM_HWY=OFF -DJPEGXL_FORCE_SYSTEM_LCMS2=OFF
cmake --build "$work/libjxl/build" --target install

echo "== libjpeg-turbo $LIBJPEG_TURBO_TAG (TurboJPEG only, simd=$asm)"
fetch https://github.com/libjpeg-turbo/libjpeg-turbo.git "$LIBJPEG_TURBO_TAG" libjpeg-turbo
simd=ON
[ "$asm" = false ] && simd=OFF
# No rpath: it needs nothing from the bundle, and its CMake would otherwise write the absolute
# install directory in place of ours.
cmake -S "$work/libjpeg-turbo" -B "$work/libjpeg-turbo/build" "${cmake_common[@]}" \
    -DENABLE_SHARED=ON -DENABLE_STATIC=OFF -DWITH_TURBOJPEG=ON -DWITH_JNA=OFF \
    -DWITH_SIMD=$simd -DWITH_TOOLS=OFF -DWITH_TESTS=OFF \
    -DCMAKE_SKIP_INSTALL_RPATH=ON
cmake --build "$work/libjpeg-turbo/build" --target install
# The libjpeg API library is a second copy of the same decoder that nothing here calls.
rm -f "$prefix"/lib/libjpeg.* "$prefix"/bin/jpeg62.dll "$prefix"/lib/jpeg.lib

echo "== libwebp $LIBWEBP_TAG (shared, decoder and demux)"
fetch https://github.com/webmproject/libwebp.git "$LIBWEBP_TAG" libwebp
webp_simd=ON
[ "$asm" = false ] && webp_simd=OFF
cmake -S "$work/libwebp" -B "$work/libwebp/build" "${cmake_common[@]}" \
    -DBUILD_SHARED_LIBS=ON -DWEBP_ENABLE_SIMD=$webp_simd -DWEBP_BUILD_ANIM_UTILS=OFF \
    -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF -DWEBP_BUILD_GIF2WEBP=OFF \
    -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF -DWEBP_BUILD_WEBPINFO=OFF \
    -DWEBP_BUILD_WEBPMUX=OFF -DWEBP_BUILD_EXTRAS=OFF -DWEBP_BUILD_LIBWEBPMUX=OFF
cmake --build "$work/libwebp/build" --target install
# libwebpdecoder is libwebp's decoder half again, for programs that link nothing else.
rm -f "$prefix"/lib/libwebpdecoder.* "$prefix"/bin/libwebpdecoder.dll "$prefix"/lib/libwebpdecoder.lib

echo "== collecting into $out"
rm -rf "${out:?}"/*
mkdir -p "$out/licenses"
case "$os" in
    linux)
        # Each library under the name others ask for it by (its SONAME), dereferenced: one real file
        # per library, no symlinks for an archive to mangle.
        for f in "$prefix"/lib/*.so*; do
            [ -L "$f" ] && continue
            soname=$(readelf -d "$f" | sed -n 's/.*(SONAME).*\[\(.*\)\]/\1/p')
            [ -n "$soname" ] && cp "$f" "$out/$soname"
        done
        ;;
    macos)
        for f in "$prefix"/lib/*.dylib; do
            [ -L "$f" ] && continue
            name=$(otool -D "$f" | tail -1)
            cp "$f" "$out/$(basename "$name")"
        done
        ;;
    windows)
        cp "$prefix"/bin/*.dll "$out/"
        ;;
esac
# Symbols are only for debugging the libraries themselves (about 10% of the bundle).
case "$os" in
    linux) strip --strip-unneeded "$out"/*.so* ;;
    macos) strip -x "$out"/*.dylib ;;
esac
cp "$work/libheif/COPYING" "$out/licenses/libheif.txt"
cp "$work/libde265/COPYING" "$out/licenses/libde265.txt"
cp "$work/dav1d/COPYING" "$out/licenses/dav1d.txt"
cp "$work/libjxl/LICENSE" "$out/licenses/libjxl.txt"
cp "$work/libjxl/third_party/brotli/LICENSE" "$out/licenses/brotli.txt"
cp "$work/libjxl/third_party/highway/LICENSE" "$out/licenses/highway.txt"
cp "$work/libjxl/third_party/skcms/LICENSE" "$out/licenses/skcms.txt"
cp "$work/libjpeg-turbo/LICENSE.md" "$out/licenses/libjpeg-turbo.md"
cp "$work/libwebp/COPYING" "$out/licenses/libwebp.txt"
cat > "$out/VERSIONS.txt" <<EOF
libheif $LIBHEIF_TAG
libde265 $LIBDE265_TAG
dav1d $DAV1D_TAG (asm: $asm)
libjxl $LIBJXL_TAG
libjpeg-turbo $LIBJPEG_TURBO_TAG (simd: $simd)
libwebp $LIBWEBP_TAG (simd: $webp_simd)
EOF

echo "== checking dependencies"
"$root/scripts/check-codecs.sh" "$out"
ls -la "$out"
