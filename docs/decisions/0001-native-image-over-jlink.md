# 0001. Ship a GraalVM native image, not the jlink image

Accepted, 2026-11-01.

## Context

Start-up is the first of Mirada's priorities: an image viewer is opened a hundred times a day for a
few seconds each. The jlink image, with jpackage's launcher replaced by a script and an AOT cache
trained by a tour of every format, reached the first image in 271–275 ms, about 168 of them before
`main`. A native image built with StaticFX (JavaFX linked in statically) and Oracle GraalVM 25.4
measured 126–130 ms and a third less memory on the same machine ([native-image.md](../native-image.md)).

## Decision

Mirada ships as a native image on Linux (x64, arm64), Windows and Apple silicon. `-Pdist,native`
builds it inside jpackage's app image, so the installers, file associations and menu entries are
the same for both builds. Intel Macs keep the jlink image, as GraalVM 25.4 does not build for them.
GraalVM 25.4, not the 25.0 LTS, because 25.0 emits no AWT libraries on macOS and ImageIO needs them.

## Consequences

- A native image is only as complete as its reachability metadata, and a gap shows only when a file
  that needs it is opened. The metadata is recorded on every platform (`scripts/native-metadata.sh`,
  the **Native metadata** workflow), and CI opens every fixture and every variant of the format
  matrix in each app image.
- Each build needs a lot of memory (8.6 GB peak here) and Oracle GraalVM, under its GFTC licence.
- Long pure-Java decodes are slower compiled ahead of time than under the JIT.
- Two app images are kept working: the native one and, for Intel Macs, the jlink one, which still
  misses the file a Finder launch opens.
- The details live in [building-and-packaging.md](../building-and-packaging.md).
