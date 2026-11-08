# Mirada as a native image (StaticFX)

Measured 2026-10-31 on this Linux x64 machine, against the `-Pdist` jlink image built from the same
commit. **The native image starts in half the time and uses a third less memory, and every format
works — but only after three metadata gaps were found the hard way, at run time.** Worth pursuing;
not yet worth shipping. The recommendation is at the end.

The experiment: [StaticFX](https://github.com/HebiRobotics/jfx-static-feature) 1.1.1 with
jfx-static-libs 27-2 (matching JavaFX 27), Oracle GraalVM 25.4.4.1, `-O2`, the pom's `native`
profile. Mirada's own reachability metadata was recorded by GraalVM's tracing agent
(`scripts/native-metadata.sh`).

## The numbers

| | jlink + AOT cache (`-Pdist`) | Native image | Change |
|---|---|---|---|
| Launch to first image (small JPEG, median of 10 warm runs, three rounds) | 271–275 ms | **126–130 ms** | −53 % |
| Reaching `main` | ~168 ms | ~13 ms | |
| Window built | ~72 ms | ~20 ms | |
| Step to a prefetched image | 1–10 ms | 1–10 ms | same |
| Decode, 1600 × 1000 (WebP / TIFF / HEIC / JPEG XL) | 216 / 111 / 130 / 64 ms | 152 / 110 / 37 / 80 ms | similar |
| Decode, 24 MP JPEG / WebP / HEIC | 227 / 1600 / 966 ms | 187 / 2055 / 1052 ms | WebP +28 % |
| Resident memory, small JPEG shown | 236 MB | **147 MB** | −38 % |
| Resident memory, 24 MP JPEG shown | 356 MB | **257 MB** | −28 % |
| On disk, without the codec bundle (10 MB either way) | 118 MB | **74 MB** (73 MB executable + 4 MB of AWT libraries) | −37 % |
| Build | ~1.5 min, ~2 GB | 55 s compile, **8.6 GB peak RAM** | |

Most of the native image's remaining 128 ms is JavaFX bringing up GTK (`main` → `fx-start`,
~109 ms). On the JVM that work hides inside "reaching `main`", because the java launcher starts
the toolkit before calling `main` when the main class extends `Application`.

Long pure-Java decodes are where the JIT still wins: a 24 MP WebP (TwelveMonkeys) is 28 % slower
compiled ahead of time. Short decodes are as fast or faster natively, because the JIT never warms
up for them. HEIC, AVIF and JPEG XL are native libraries either way.

## What it took

Each of these built cleanly and failed only when that code path ran. A native image is only as
correct as its metadata, and nothing at build time says what is missing.

1. **A main class that is not an `Application`** (`com.mirada.Launcher`): built from the class path,
   JavaFX's launcher check would fail otherwise. StaticFX's examples do the same.
2. **`libXxf86vm` for the linker.** StaticFX links JavaFX statically, so the system's GL and X
   libraries are needed at build time; without `libxxf86vm-dev`, a symlink and
   `-Dnative.extra=-native-compiler-options=-L<dir>` (native-image does not pass `LIBRARY_PATH` on).
3. **All charsets** (`-H:+AddAllCharsets`). metadata-extractor asks for Windows-1252 when it reads a
   PNG or HEIF file; the native image has only the standard ones without this. It surfaced as
   `ExceptionInInitializerError` and took the whole image with it, which was **a bug in Mirada on
   any runtime**: `Exif.read` promised never to fail but did not catch `LinkageError`. Fixed in
   both `Exif` and `ExifDetails`, and failures on the way to the screen are now printed to stderr
   instead of leaving the previous image up in silence.
4. **AWT's shared libraries.** `java.desktop` (ImageIO) is not linked statically: the build leaves
   `libawt.so`, `libawt_headless.so`, `libawt_xawt.so`, `libfontmanager.so`, `libjavajpeg.so`,
   `liblcms.so` beside the executable, and ImageIO fails without them. "One file" is really one
   file and six libraries.
5. **JNI metadata for AWT's raster classes.** The first metadata run used 8-bit test files; a
   16-bit TIFF failed with `NoSuchFieldError: ShortComponentRaster.data`. Running the agent again
   over larger and deeper images (`config-merge-dir`) recorded it. Every new kind of file is a
   chance of another gap like this.

## The format matrix and the CI job

`scripts/make-format-matrix.sh` writes 41 small variants into `src/test/resources/com/mirada/matrix`:
JPEG (RGB, grey, CMYK, progressive, 4:4:4), PNG (8- and 16-bit, grey, grey + alpha, palette, RGBA),
GIF (static, animated), BMP (24-bit, 32-bit with alpha, palette), WebP (lossy, lossless, alpha,
animated), TIFF (8- and 16-bit, LZW, deflate, JPEG-compressed, grey, CMYK, RGBA, multi-page), PSD
(8- and 16-bit, CMYK, RGBA), HEIC and AVIF (with alpha, 10-bit), JPEG XL (RGB, RGBA), an EXIF
orientation and a heavy EXIF block.

- `MatrixTest` decodes all of them on the JVM. It found **a gap on every runtime**: JavaFX's own BMP
  decoder throws on a 32-bit BMP with alpha. Mirada now falls back to ImageIO whenever JavaFX gives
  up on a file.
- The tour reports a file it cannot show (`[perf] fail name: reason`) and goes on, so the smoke test
  names every failure in one run instead of timing out at the first.
- CI's **native** job (Linux and macOS) installs Oracle GraalVM 25.4, builds the native image, opens the fixtures and
  the whole matrix in it, prints a start-up figure and uploads the app for a week. Its first local
  run found **two more native-only gaps**, both JNI into the JDK's image code: AWT's
  `IntegerComponentRaster` (the BMP fallback) and the JPEG reader's callback (a JPEG-compressed
  TIFF). `scripts/native-metadata.sh` now tours the matrix as well, and all 41 variants open.
- CI's jlink packaging job opens the matrix too, on all three platforms.
- A native image finds the codec bundle in a `codecs` folder beside its executable, so `target/mirada`
  runs with no options.

## macOS

CI builds and smoke-tests it on `macos-latest` (Apple silicon): every fixture and all 41 matrix
variants open. It took one change, the GraalVM release:

- **GraalVM 25.0 has no AWT on macOS.** The first run (25.0.4) built cleanly and opened JPEG, PNG,
  HEIC, AVIF and JPEG XL, then failed WebP, TIFF and PSD with `Could not initialize class
  java.awt.Toolkit`: the build emitted no `.dylib` at all, and ImageIO needs `libawt` even headless
  (`BufferedImage` loads it). **25.4** emits nine libraries beside the executable — `libawt`,
  `libawt_lwawt`, `libfontmanager`, `libfreetype`, `libjavajpeg`, `liblcms`, `libmlib_image` and the
  `libjava`/`libjvm` shims — and the same metadata recorded on Linux was enough. Both CI jobs now
  use 25.4, as this machine does.
- **Not done:** a `.app` bundle around it (the libraries go in `Contents/MacOS` and each must be
  signed), Finder's open-file event (since observed through the Objective-C runtime, `MacOpenFiles`,
  and smoke-tested through `open -a`), and a run on a real Mac.

Start-up on the CI runners, launch to the first image (`measure-startup.sh -n 5`, median of the 4
warm runs; shared virtual machines, so a sanity figure, not a benchmark). Both builds come from the
same commit and run on the same kind of runner (run 41281297354):

| Runner | jlink + AOT cache | Native image |
|---|---|---|
| `ubuntu-latest` (x64, Xvfb) | 624 ms | **153 ms** |
| `macos-latest` (Apple silicon) | 558 ms | **364 ms** (563 in the run before; single runs 312–815) |

On macOS the gap is smaller and the native figure noisier than on Linux; until it is measured on a
real Mac, take it as "faster", not as a ratio. The macOS build fits the runner's 7 GB: about five and
a half minutes.

## What it would cost to ship

- **A native build per platform**, each on a runner with enough memory: 8.6 GB peak here. GitHub's
  Linux and Windows runners have 16 GB; the macOS ones have less.
- **Oracle GraalVM**, free under the GFTC licence, as each build's JDK.
- **Metadata upkeep**: the agent run, plus a native smoke test over many variants of every format
  (bit depths, colour models, animations, CMYK) in CI, because a gap shows only when that file is
  opened. Today's fixtures are too uniform to catch them.
- **StaticFX's licence**: its repository is GPL-2.0 without the Classpath Exception. It is a build
  tool and its feature code may not end up in the executable, but that needs checking with its
  author before shipping anything built with it.
- **Windows is untried.** StaticFX claims it; the AWT libraries, the codecs and the launcher
  arrangement would need the same work there that macOS needed (below).

## Decision

**The native image ships** (2026-11-01), on Linux (x64, arm64), Windows and Apple silicon; Intel
Macs keep the jlink image, which GraalVM 25.4 no longer builds for. StaticFX's licence was cleared
for shipping. `-Pdist,native` builds it inside jpackage's app image (`scripts/native-app-image.sh`),
so the installers are made exactly as before; [building-and-packaging.md](building-and-packaging.md#the-native-image-what-ships) has the
how.

Since this page's measurements, libjpeg-turbo, libwebp and the first image's head start have moved
the native image's numbers again: 123 ms to a small JPEG, 160–177 ms to a 24-megapixel one, about
220 ms to a 24-megapixel WebP ([building-and-packaging.md](building-and-packaging.md#start-up-measured)).

What is still open:

- **Windows needs its own metadata.** Its AWT calls back into Java through JNI that Linux's never
  reaches (`java.awt.Toolkit.getDefaultToolkit` while loading), so ImageIO failed there until the
  **Native metadata** workflow recorded it. Every platform's run is merged into the one file.
- **A real Mac and a real Windows machine** have not run it by hand; CI opens every format in both.
