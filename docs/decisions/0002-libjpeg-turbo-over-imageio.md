# 0002. Decode JPEG with libjpeg-turbo, not ImageIO or JavaFX

Accepted, 2026-11-01.

## Context

JPEG is most of what Mirada opens. JavaFX's own decoder is slow for large photos, and it cannot run
before the toolkit is up (it asks for the screens' scale), so it cannot decode the first image
while GTK starts. The JDK's ImageIO reader could, but it converts an embedded ICC profile, which
JavaFX ignores: the same photo would look different depending on which decoder opened it.

## Decision

JPEG is decoded by libjpeg-turbo's TurboJPEG, through FFM (`TurboJpegDecoder`), into premultiplied
ARGB ints that become a `WritableImage` without a copy. It ignores the embedded profile exactly as
JavaFX does. What it will not decode to 8-bit RGB (CMYK, YCCK, 12-bit) is an `IOException`, and
`ImageCache` hands the file to JavaFX.

## Consequences

- Two to three times JavaFX's speed, scaled decodes for the strip's thumbnails, and the first image
  decoding before JavaFX is up (`HeadStart`).
- libjpeg-turbo is one more native library in the codec bundle
  ([0003](0003-codecs-built-from-source.md)), with hand-written FFM bindings.
- JavaFX stays as the fallback, and the format matrix's CMYK JPEG is the test that the hand-over
  works. A photo's look must never change with the decoder ([gotchas.md](../gotchas.md)).
