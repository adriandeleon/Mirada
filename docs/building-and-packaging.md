# Building and packaging

The commands are in [AGENTS.md](../AGENTS.md#commands); this is how what they build fits together:
the codec bundle, the native image that ships inside jpackage's app image, the installers made from
it, and what start-up measures. Releasing and signing are in [release.md](release.md).

## The codec bundle

**The codec bundle** is built from source, never copied from a package manager: Homebrew's libheif
links x265, which is GPL, and both Homebrew's and the distributions' copies drag in encoders and
image libraries Mirada never calls. `scripts/build-codecs.sh` pins the versions and builds libheif
with libde265 and dav1d linked in statically (decoders only, no plugin loading) and libjxl with
brotli, highway and skcms from its submodules; `scripts/check-codecs.sh` fails the build if any
library needs something outside the bundle and the system's C/C++ runtime. The **Codecs** workflow
runs it on all five targets (Linux in an Ubuntu 22.04 container, for the oldest glibc we support)
and publishes a `codecs-N` release with checksums; the zips are 3.0–4.2 MB. Changing a library
version means: edit the tags in `build-codecs.sh`, run the workflow with the next tag, then put the
new tag and the five checksums from its `SHA256SUMS` in the pom.

## The native image (what ships)

Mirada ships as a GraalVM native image with JavaFX linked in by StaticFX, on Linux (x64, arm64),
Windows and Apple silicon; GraalVM 25.4 has no Intel macOS, which keeps the jlink image. It starts
in half the time of the jlink image and uses a third less memory ([native-image.md](native-image.md); [decision 0001](decisions/0001-native-image-over-jlink.md)).

`-Pdist,native` builds it inside jpackage's app image: the native profile compiles `target/mirada`
in `prepare-package`, jpackage makes the app image as before, and `scripts/native-app-image.sh`
puts the executable, AWT's libraries and the codec bundle in place of the launcher and the Java
runtime. The installers are made from that image, so the file associations, menu entries and
package metadata are the same for both builds. `./mvnw -Pnative -DskipTests package` alone builds
just `target/mirada`.

CI's packaging job opens every fixture and every variant of the format matrix in each app image:
[testing.md](testing.md#the-format-matrix).

A native image is only as complete as its reachability metadata, and a gap shows only when a file
that needs it is opened. It is recorded by `scripts/native-metadata.sh` into
`src/main/resources/META-INF/native-image`, on Linux locally and on macOS and Windows by the
**Native metadata** workflow, then joined (the workflow's header says how). Re-record after anything
that reflects, loads resources, or adds an FFM downcall: each platform's AWT reaches its own JNI.

**GraalVM 25.4, not the 25.0 LTS.** 25.0 has no AWT on macOS: the image builds and opens JPEG, PNG,
HEIC and JPEG XL, then fails on WebP, TIFF and PSD with `Could not initialize class java.awt.Toolkit`,
because ImageIO needs `libawt` and no `.dylib` was emitted. 25.4 leaves `libawt.dylib` and eight
others beside the executable, as Linux leaves its `.so` files; they must travel with it.

## Start-up, measured

The native app image (what ships), this machine, median of 6 warm runs: **123 ms** to a small JPEG,
**160–177 ms** to a 24-megapixel JPEG, **655 ms** to a 24-megapixel HEIC. `main` is reached at
about 9 ms; bringing up GTK takes the next 90, during which the first image decodes (`HeadStart`). Measure again after anything that touches start-up; these are the numbers to beat.

The jlink image's numbers, before the native image and libjpeg-turbo, are kept in
[reference/startup-jlink.md](reference/startup-jlink.md).

## Things that will bite you when packaging

- **Installing winget on a runner calls GitHub's API.** `Repair-WinGetPackageManager` finds its
  release there, and without a token a hosted runner shares 60 calls an hour with every job on its
  address: the `winget` workflow failed on that limit, not on the manifests. Its install step gets
  the job's token as `GITHUB_TOKEN`.

- **The deb's dependencies are a hand-kept list.** jpackage adds a package for every library the
  image reaches, as the build host's dpkg names it: for a native image that is the whole GTK
  closure under Debian 13's names (`libgtk-3-0t64`), which Debian 12 and Ubuntu 22.04 cannot
  install. `build-installers.sh` replaces the field; `check-linux-packages.sh` fails a bare t64 name.
- **The Linux runners are pinned, never `ubuntu-latest`.** A native image takes the build host's
  newest glibc symbol versions: built on 26.04, 0.2.0 and 0.2.1 for x64 needed glibc 2.43 for one
  `sqrtf` and started nowhere older. `check-linux-packages.sh` fails any binary needing more than
  Ubuntu 22.04's 2.35; move the runner only when that check passes.
- **jpackage substitutes its tokens in the deb's scripts, comments included.** A comment in
  `packaging/linux/prerm` naming a desktop-menu token broke 0.1.0's and 0.2.0's removal script, and
  with it every upgrade and removal (packaging/linux/NOTES.md). `check-linux-packages.sh` parses
  every maintainer script.
- **The Windows image carries the Visual C++ runtime.** The native executable, AWT's DLLs, libheif
  and libjxl import `vcruntime140.dll`, `vcruntime140_1.dll` and `msvcp140.dll`, which Windows does
  not include. GitHub's runners have them installed, so every smoke test passed while 0.2.0's MSI
  failed to start on a clean Windows (winget's validation: 0xC0000135, STATUS_DLL_NOT_FOUND).
  `native-app-image.sh` copies them from Visual Studio's redistributable folder, then
  `scripts/check-windows-dlls.py` reads every PE file's imports and fails on a DLL that is neither
  in the image nor in its list of Windows' own. A new system DLL goes in that list; anything else
  goes in the image.
- **jpackage puts the whole launcher in the MSI as its Add/Remove Programs icon**, uncompressed in
  the Icon table. Its own launcher is a 100 KB stub; ours is the 78 MB native image, which made the
  MSI 110 MB around 36 MB of compressed files. Its `main.wxs` takes that file from the `JpIcon`
  variable, and `build-installers.sh` writes an `overrides.wxi` (jpackage's stub for changing those
  variables, included first) that points it at `branding/mirada.ico`. Editing the finished MSI
  instead does not work: rewriting it to a new path drops the cabinet, and editing it in place
  keeps the old stream's space. The release fails an MSI larger than the app it installs, then
  installs it and smoke-tests the installed copy.
- **The AppImage is the app image at `opt/mirada`**, as the deb installs it, so
  `check-linux-packages.sh` checks the same tree in all three (`scripts/build-appimage.sh`).
  appimagetool and the runtime are release builds pinned by SHA-256, cached in
  `~/.cache/mirada/appimage`; `MIRADA_APPDIR_ONLY=1` stops at the AppDir, without them. Like the
  deb it takes GTK and the rest from the system, so the glibc floor applies to it too. GitHub's
  runners have no FUSE: the smoke test runs it with `APPIMAGE_EXTRACT_AND_RUN=1`.
- **The macOS dmg is `hdiutil`, not jpackage**, whose dmg step wants the Java runtime inside the
  bundle. The document types are written into `Info.plist` by `fix-mac-bundle.sh` from
  `packaging/file-types.txt`, which the other installers' file associations read too.
- **jpackage's Linux launcher asks dpkg and rpm about itself on every start.** It runs `rpm -qf`
  and `dpkg -S` on its own path (`JPACKAGE_DEBUG=true` shows it) to learn whether a package
  installed it; `dpkg -S` searches the whole package database, about 300 ms — more than the rest of
  start-up. `scripts/linux-launcher.sh` replaces it in the image with a shell script that starts the
  bundled java with the options copied from `Mirada.cfg`. macOS's and Windows' launchers do not do
  this and are left alone.
- **The AOT cache is trained by a tour, not a launch.** Opening one image archives only that
  decoder's classes. `scripts/aot-train.sh` opens `src/test/resources/com/mirada/image` (one tiny
  file per format) under `-Dmirada.aotTrain`, and `com.mirada.perf.Tour` steps through all of it and
  exits, so every decoder, ImageIO's plugins and the FFM bindings are archived. The cache is about
  35 MB and halves start-up. It is tied to the exact runtime: any change to the image means
  retraining, which `-Pdist` always does. CI sets `MIRADA_REQUIRE_AOT=1` so a missing cache fails.
- **jlink compression is not the slow part.** `--compress=zip-6` against `zip-0` measured 577 against
  563 ms for 44 MB more on disk; zip-6 stays.
- **Ten dependencies are automatic modules, and jlink links only explicit ones.** moditect writes a
  descriptor for each in `-Pdist` (TwelveMonkeys' eight, metadata-extractor, xmpcore), keeping the
  automatic names `module-info` requires. jdeps carries the readers' `provides` over from
  `META-INF/services`; none of them calls `ServiceLoader` itself, so no `uses` is needed. A new
  dependency that is an automatic module needs an entry there, or `-Pdist` fails at jlink.
- **The icon is rendered with librsvg, not ImageMagick.** ImageMagick's built-in SVG renderer, used
  when `rsvg-convert` is missing, draws only the tile of these icons (Termina's too). `branding/`
  holds the SVG and the PNG, ICO and ICNS made from it.
- **The macOS bundle is changed after jpackage signs it** (codecs, AOT cache, version), so
  `scripts/fix-mac-bundle.sh` re-signs it ad hoc, last; CI verifies the signature.
