# 0003. Build the native codecs from source, never from a package manager

Accepted, 2026-10-31.

## Context

HEIC, AVIF, JPEG XL, WebP and fast JPEG need native libraries: libheif (with libde265 and dav1d),
libjxl, libwebp and libjpeg-turbo. Homebrew's libheif links x265, which is GPL, and both Homebrew's
and the Linux distributions' copies drag in encoders and image libraries Mirada never calls. Mirada
also has to ship the same libraries on five targets, down to the oldest glibc it supports.

## Decision

`scripts/build-codecs.sh` builds them from source at pinned versions: libheif with libde265 and
dav1d linked in statically (decoders only, no plugin loading), libjxl with brotli, highway and skcms
from its submodules. `scripts/check-codecs.sh` fails the build if any library needs something
outside the bundle and the system's C/C++ runtime. The **Codecs** workflow builds all five targets
(Linux in an Ubuntu 22.04 container) and publishes a `codecs-N` release with checksums, which the
build downloads with the checksums pinned in the pom.

## Consequences

- The licences are the ones `NOTICE` lists, with no GPL encoder in the bundle, and the zips are
  3.0–4.2 MB.
- Changing a library version is a workflow run and a pom change (the tag and five checksums), and a
  local build of the bundle takes about ten minutes.
- `NativeLibraries` finds the bundle first, then the system's libraries; a missing library is not
  fatal, its files open with a message naming it.
- The details live in [building-and-packaging.md](../building-and-packaging.md#the-codec-bundle).
