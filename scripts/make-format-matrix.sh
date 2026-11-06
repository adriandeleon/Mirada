#!/usr/bin/env bash
# Writes src/test/resources/com/mirada/matrix: small images covering the variants each format comes
# in — bit depths, grey, CMYK, alpha, palettes, compressions, animation, orientation, heavy EXIF.
#
# Why: a native image is only as complete as its metadata, and a gap shows only when a file that
# takes that code path is opened (a 16-bit TIFF found one). MatrixTest decodes all of these on the
# JVM; CI's native job opens all of them in the native image. Re-run this to add a variant; the
# files are committed, so the tests do not need ImageMagick.
#
# Needs: ImageMagick 7 with HEIC/AVIF (libheif), Python with libheif 1.19 or later and its x265
# plugin, Python with libjxl for the JPEG XL ones, and
# ffmpeg with libaom for the AVIF sequence.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/src/test/resources/com/mirada/matrix"
mkdir -p "$out"
cd "$out"
rm -f ./*.*

base() { magick -size 48x32 gradient:'#d0302a-#2a5fd0' "$@"; }
alpha() { magick -size 48x32 gradient:'#d0302a-#2a5fd0' \( -size 48x32 gradient:white-'rgba(255,255,255,0)' -rotate 90 -resize 48x32! \) -alpha off -compose CopyOpacity -composite "$@"; }
anim() { magick -delay 20 -size 48x32 xc:'#d0302a' xc:'#2a5fd0' xc:'#30a050' -loop 0 "$@"; }

# JPEG
base -quality 85 jpeg-rgb.jpg
base -colorspace Gray -quality 85 jpeg-grey.jpg
base -colorspace CMYK -quality 85 jpeg-cmyk.jpg
base -interlace Plane -quality 85 jpeg-progressive.jpg
base -sampling-factor 4:4:4 -quality 95 jpeg-444.jpg
# PNG
base png-rgb.png
alpha png-rgba.png
base -depth 16 png-rgb16.png
base -colorspace Gray png-grey.png
alpha -colorspace Gray png-grey-alpha.png
base PNG8:png-palette.png
# GIF and BMP
base gif-static.gif
anim gif-animated.gif
base BMP3:bmp-24.bmp
alpha -define bmp:format=bmp4 bmp-32-alpha.bmp
base -colors 16 -type Palette BMP3:bmp-palette.bmp
# WebP
base -quality 80 webp-lossy.webp
base -define webp:lossless=true webp-lossless.webp
alpha -quality 80 webp-alpha.webp
anim -define webp:lossless=true webp-animated.webp
# TIFF
base -compress None tiff-rgb.tif
base -compress LZW -depth 16 tiff-rgb16-lzw.tif
base -compress Zip tiff-deflate.tif
base -compress JPEG tiff-jpeg.tif
base -colorspace Gray tiff-grey.tif
base -colorspace CMYK tiff-cmyk.tif
alpha tiff-rgba.tif
base \( +clone -negate \) tiff-multipage.tif
# PSD
base -type TrueColor -alpha off -depth 8 psd-rgb.psd
base -type TrueColor -alpha off -depth 16 psd-rgb16.psd
base -colorspace CMYK -depth 8 psd-cmyk.psd
alpha -depth 8 psd-rgba.psd
# HEIC and AVIF
base -quality 80 heic-rgb.heic
alpha -quality 80 heic-alpha.heic
base -quality 80 avif-rgb.avif
alpha -quality 80 avif-alpha.avif
base -depth 10 -quality 80 avif-10bit.avif
# An AVIF image sequence (red, then blue coming in from the left), which ImageMagick cannot write.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=red:s=48x32:r=4" -f lavfi -i "color=c=blue:s=48x32:r=4" \
    -filter_complex "[0][1]overlay=x='if(gte(n,1),0,48)':y=0,format=yuv420p" -frames:v 3 \
    -c:v libaom-av1 -still-picture 0 -f avif "$out/avif-animated.avif"
# An iPhone-style grid (cropped tiles, irot and EXIF orientation 6, the profile on the tiles), which
# ImageMagick cannot write either
python3 -I "$root/scripts/write-heic-grid.py" "$root/src/test/resources/com/mirada/edit/display-p3.icc" heic-grid.heic
# JPEG XL, through libjxl's encoder (ImageMagick here cannot write it)
base -depth 8 rgb:"$out/.jxl-rgb.raw"
python3 -I "$root/scripts/write-jxl.py" "$out/.jxl-rgb.raw" 48 32 3 jxl-rgb.jxl
alpha -depth 8 rgba:"$out/.jxl-rgba.raw"
python3 -I "$root/scripts/write-jxl.py" "$out/.jxl-rgba.raw" 48 32 4 jxl-rgba.jxl
for colour in red blue lime; do magick -size 48x32 "xc:$colour" -depth 8 rgb:"$out/.jxl-$colour.raw"; done
python3 -I "$root/scripts/write-jxl.py" "$out/.jxl-red.raw,$out/.jxl-blue.raw,$out/.jxl-lime.raw" 48 32 3 jxl-animated.jxl
rm -f .jxl-*.raw
# Orientation and metadata
base -quality 85 -rotate 90 orient-6.jpg
python3 -I - orient-6.jpg <<'PY'
import sys
from PIL import Image
p = sys.argv[1]; im = Image.open(p); exif = Image.Exif(); exif[0x0112] = 6
im.save(p, exif=exif.tobytes(), quality=85)
PY
cp ../image/exif.jpg exif-heavy.jpg

ls -la "$out"
