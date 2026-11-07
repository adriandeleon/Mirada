# Mirada

A fast, folder-oriented image viewer. JDK 25 + JavaFX 27 + Maven, modular (JPMS, module
`com.mirada`). The image half of the viewers plan; a Reader for PDF and comics may follow and share
code with it, but nothing is extracted yet.

## Priorities

In this order, when they pull against each other:

1. **Speed.** Cold start, stepping to the next image, and decoding. A feature that costs either is
   not worth it.
2. **Keyboard navigation.** Everything reachable without the mouse.
3. **A command palette**, so every command can be found and run by name.

## Commands

- Run: `./mvnw javafx:run`, or on a file or folder: `./mvnw javafx:run -Djavafx.args=/path/to/photo.jpg`
- Test: `./mvnw test`
- Everything CI would check (tests, NullAway, format): `./mvnw verify`
- Format: `./mvnw spotless:apply` (Palantir, as in Termina and Torrenta)
- App image, as it ships: **`./mvnw clean -Pdist,native -DskipTests package`** with Oracle GraalVM
  25.4 as `JAVA_HOME` ⇒ `target/dist/Mirada{.app}`, the native executable with AWT's libraries and
  the codec bundle beside it. `clean` is required (jlink reads `target/classes`).
- The JVM app image (Intel Macs, and any JDK 25): `./mvnw clean -Pdist -DskipTests package`, with
  a trained AOT cache in place of the native executable.
- Installers too: add `-Dskip.installers=false` ⇒ `target/installers` (deb, rpm, tar.gz and
  AppImage on Linux, dmg on macOS, msi on Windows);
  `scripts/check-linux-packages.sh target/installers branding/mirada.png` checks the Linux ones.
- Run without Maven (any JVM option, no build time counted): `scripts/dev-run.sh [-D…] photo.jpg`
- Look at the UI without a window on the desktop (keys, clicks, screenshots):
  `scripts/headless-run.sh photo.jpg -- 'key C-S-p' 'type rot' 'shot /tmp/palette.png'`; the steps
  are listed in `src/test/java/com/mirada/ui/HeadlessDriver.java`
- Start-up, median of warm runs: `scripts/measure-startup.sh -n 7 target/dist/Mirada/bin/Mirada photo.jpg`
  (or `MIRADA_NO_BUILD=1 … scripts/dev-run.sh photo.jpg`). Stepping: `MIRADA_PERF=1 MIRADA_PERF_TOUR=1`
  on any launcher prints each step's time (`MIRADA_PERF_TOUR_PAUSE=0` for the no-prefetch worst case).
- Codec bundle: the build downloads this platform's zip from the `codecs-N` release into
  `target/codecs` (pom.xml, `codecs-*` profiles, checksums pinned). To build one yourself:
  `scripts/build-codecs.sh` (about 10 minutes; `CODECS_NO_ASM=1` without nasm), then run Maven with
  `-Dcodecs.skip` so the download does not overwrite it.

Run Maven from the project root, on JDK 25 (the enforcer refuses others). CI
(`.github/workflows/ci.yml`) runs `verify` on Linux, macOS and Windows for every push to `main`.

A user-visible change gets a line in `CHANGELOG.md`; a new dependency gets an entry in `NOTICE`.

## Architecture

| Package | What |
|---|---|
| `com.mirada.folder` | The "document": the images in one folder, in the chosen order (`Sorting`: natural name order, date or size), and which one is showing (`FolderImages`, `NaturalOrder`, `ImageFormats`) |
| `com.mirada.image` | Loading: decoded images cached by path (`ImageCache`), EXIF orientation and the camera line (`Exif`, `Orientation`), the sidebar's camera card and every metadata tag (`ExifDetails`), the strip's thumbnails (`Thumbnails`) |
| `com.mirada.edit` | The editor's rules and pixel work, none of it UI: the edit and its order (`Edit`: turn, flip, crop), the crop as fractions (`Crop`) and as a box being dragged (`CropBox`), the shapes (`AspectRatio`), turns and mirrors on ARGB ints (`Raster`), undo and redo (`History`), the EXIF, colour profile and XMP a saved edit keeps (`ExifBlock`, `IccProfile`, `Xmp`, all reading through `Containers`), and writing JPEG, PNG, TIFF or BMP (`Encoder`) |
| `com.mirada.os` | Platform glue: the files Finder opens Mirada with (`MacOpenFiles`) and About and Settings in its application menu (`MacAppMenu`); moving a file to the desktop's trash, and the one rule for `java.awt.headless` (`Trash`); showing a file in Finder, File Explorer or the freedesktop file manager (`Reveal`) |
| `com.mirada.settings` | `settings.properties` in `~/.mirada`, `~/Library/Application Support/Mirada` on macOS or `%APPDATA%\Mirada` on Windows, moved there once from `~/.mirada` (`MIRADA_CONFIG_DIR` moves it): theme, what opens at start, how long the floating controls stay; clamped on read, written through on every change. Beside it, `bookmarks.properties`: the user's bookmarks and notes on images and folders (`Bookmarks`), read after the first paint, re-read before every change so two windows add to each other's |
| `com.mirada.ui` | The window (`ViewerWindow`), laid out as GNOME Image Viewer's: the header bar (`TitleBar`), the zoom/pan canvas (`ImageViewport`, `Zoom`, `Scrolling`) with floating controls over it (`OnScreenControls`), the properties sidebar (`InfoPanel`, `ImageDetails`) and the thumbnail strip (`ThumbnailStrip`, along the bottom or down the left, pictures or names, as the settings say); the editor (`Editor`, `CropView`); every command with its keys, name, description and group (`Command`), the command palette (`CommandPalette`), the macOS menu bar (`MacMenus`), `AboutWindow`, `SettingsWindow`, `ShortcutsWindow`, the rename rules (`Renaming`), the theme and, on macOS and Windows, the system's accent colour (`Theme`, `Accent`), the symbolic icons, each command's included (`Icons`) |

Six decoders (`ImageFormats.Decoder`, by extension). JavaFX's own `Image` decodes BMP, GIF
(animated) and PNG in its background thread, and the JPEGs libjpeg-turbo declines; a decoder with
a `fallback()` hands what it declines, or cannot load for, to that one. The rest run on
two decoder threads in `ImageCache`, into premultiplied ARGB ints (`ArgbPixels`) that become a
`WritableImage` without a copy (`PixelBuffer`):

| Formats | Decoder | How |
|---|---|---|
| JPEG | `TurboJpegDecoder` | libjpeg-turbo's TurboJPEG, through FFM; JavaFX for CMYK, YCCK and 12-bit |
| WebP (still and animated) | `WebpDecoder` | libwebp's animation decoder, through FFM; ImageIO for WebP without libwebp |
| TIFF, PSD | `ImageIoDecoder` | ImageIO with the TwelveMonkeys plugins, pure Java |
| HEIC, AVIF | `HeifDecoder` | libheif (with libde265 and dav1d), through FFM |
| JPEG XL | `JxlDecoder` | libjxl, through FFM, on every core via `libjxl_threads` |

The native libraries are found by `NativeLibraries`: first in the folder named by the
`mirada.codecs` system property, then the system's search path, then Homebrew's. Runs and tests set
it to `target/codecs` (`codecs.dir` in the pom); the installers will set it to their own copy.

**The codec bundle** is built from source, never copied from a package manager: Homebrew's libheif
links x265, which is GPL, and both Homebrew's and the distributions' copies drag in encoders and
image libraries Mirada never calls. `scripts/build-codecs.sh` pins the versions and builds libheif
with libde265 and dav1d linked in statically (decoders only, no plugin loading) and libjxl with
brotli, highway and skcms from its submodules; `scripts/check-codecs.sh` fails the build if any
library needs something outside the bundle and the system's C/C++ runtime. The **Codecs** workflow
runs it on all five targets (Linux in an Ubuntu 22.04 container, for the oldest glibc we support)
and publishes a `codecs-N` release with checksums; the zips are 3.0–4.2 MB. Changing a library
version means: edit the tags in `build-codecs.sh`, run the workflow with the next tag, then put the
new tag and the five checksums from its `SHA256SUMS` in the pom. A missing library is not fatal: its files still list, and open with a message
naming the library. EXIF is metadata-extractor.

## The native image (what ships)

Mirada ships as a GraalVM native image with JavaFX linked in by StaticFX, on Linux (x64, arm64),
Windows and Apple silicon; GraalVM 25.4 has no Intel macOS, which keeps the jlink image. It starts
in half the time of the jlink image and uses a third less memory ([docs/native-image.md](docs/native-image.md)).

`-Pdist,native` builds it inside jpackage's app image: the native profile compiles `target/mirada`
in `prepare-package`, jpackage makes the app image as before, and `scripts/native-app-image.sh`
puts the executable, AWT's libraries and the codec bundle in place of the launcher and the Java
runtime. The installers are made from that image, so the file associations, menu entries and
package metadata are the same for both builds. `./mvnw -Pnative -DskipTests package` alone builds
just `target/mirada`.

CI's packaging job opens every fixture and every variant of the format matrix in each app image
(`src/test/resources/com/mirada/matrix`, from `scripts/make-format-matrix.sh`); `MatrixTest`
decodes the matrix on the JVM. Add a variant there when a new kind of file turns up a problem.

A native image is only as complete as its reachability metadata, and a gap shows only when a file
that needs it is opened. It is recorded by `scripts/native-metadata.sh` into
`src/main/resources/META-INF/native-image`, on Linux locally and on macOS and Windows by the
**Native metadata** workflow, then joined (the workflow's header says how). Re-record after anything
that reflects, loads resources, or adds an FFM downcall: each platform's AWT reaches its own JNI.

**GraalVM 25.4, not the 25.0 LTS.** 25.0 has no AWT on macOS: the image builds and opens JPEG, PNG,
HEIC and JPEG XL, then fails on WebP, TIFF and PSD with `Could not initialize class java.awt.Toolkit`,
because ImageIO needs `libawt` and no `.dylib` was emitted. 25.4 leaves `libawt.dylib` and eight
others beside the executable, as Linux leaves its `.so` files; they must travel with it.

## Releasing

1. Set `<version>` in `pom.xml` without `-SNAPSHOT`, and give its `CHANGELOG.md` heading a date
   (`## 0.1.0 — 2026-11-02`); the release notes are that section.
2. Commit, then tag and push the tag: `git tag v0.1.0 && git push origin v0.1.0`.
3. The **Release** workflow refuses a tag that disagrees with the pom or a snapshot, builds, tests and
   packages on all five targets, smoke-tests every app image and the installed deb, checks the Linux
   packages, and publishes the installers with `SHA256SUMS` and build provenance. Its last job, the
   **winget** workflow, writes the winget manifests from the published MSI
   (`scripts/winget-manifests.sh`), validates them, installs and uninstalls through them, and uploads
   them as `winget-manifests-<version>`.
4. Open a pull request to microsoft/winget-pkgs with those manifests (one version per PR, titled
   "New package: AdrianDeLeon.Mirada version 0.1.0", later "Update: AdrianDeLeon.Mirada to 0.2.0").
5. Move the pom to the next `-SNAPSHOT`.

Run the workflow by hand first for a dry run: it builds and checks everything and publishes nothing.

**Signing** is set up from repository secrets (`scripts/ci-signing-credentials.sh`); without them a
tag builds unsigned, and a dry run signs with throwaway credentials it makes on the runner, so every
signing step and its check runs either way:

| Secret | What |
|---|---|
| `MACOS_CERTIFICATE` | The Developer ID Application certificate and key, a `.p12`, base64 |
| `MACOS_CERTIFICATE_PASSWORD` | The `.p12`'s password |
| `MACOS_SIGNING_IDENTITY` | Its name, `Developer ID Application: Name (TEAMID)` |
| `APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD` | For notarizing: the account, its team, an app-specific password |
| `LINUX_GPG_PRIVATE_KEY` (or `LINUX_GPG_KEY`), `LINUX_GPG_PASSPHRASE` | The release key, armored, and its passphrase if it has one |

macOS: `scripts/sign-mac.sh` signs every Mach-O file in the bundle, then the bundle, with the
hardened runtime and `packaging/macos/entitlements.plist`; `scripts/notarize-mac.sh` signs the dmg,
notarizes it and staples the ticket. Linux: `scripts/sign-linux.sh` signs the rpm in place and
writes an `.asc` beside every file; the release signs `SHA256SUMS` and publishes the public key
(`mirada-signing-key.asc`). Windows is not signed yet: SmartScreen warns on first launch.

## Start-up, measured

The native app image (what ships), this machine, median of 6 warm runs: **123 ms** to a small JPEG,
**160–177 ms** to a 24-megapixel JPEG, **655 ms** to a 24-megapixel HEIC. `main` is reached at
about 9 ms; bringing up GTK takes the next 90, during which the first image decodes (`HeadStart`).

The jlink image, before the native image and libjpeg-turbo:

Launch to the first image on screen, Linux x64 (this machine), a small JPEG, median of 6 warm runs.
Measure again after anything that touches start-up; these are the numbers to beat.

| Build | ms |
|---|---|
| `scripts/dev-run.sh` (system JDK, jars on the module path) | 455 |
| App image, jpackage's launcher, no AOT cache | 812 |
| App image, jpackage's launcher, AOT cache | 577 |
| App image, script launcher, no AOT cache | 476 |
| App image, script launcher, AOT cache, before the header-bar UI | 246 |
| **The same, with the header bar, controls, sidebar and palette styles (what `-Pdist` builds now)** | **285–291** |

With everything on, `main` is reached at about 160 ms, the window is up 60 ms later, and the image is
decoded and painted in the next 30. A HEIC as the first image costs about 60 ms more. Stepping onto
a prefetched neighbour is 2–8 ms for every format.

## Things that will bite you

- **The first image decodes before JavaFX is up** (`HeadStart`, started from `App.main`). JavaFX's
  own decoder cannot (it asks for the screens' scale), so that head start needs a decoder of ours:
  every format but PNG, GIF and BMP. `ImageCache.load` claims it for that one file; the first claim
  closes the door, so a head start that loses the race gives up instead of holding its pixels.
  `WritableImage` still needs the toolkit: `ArgbPixels` is the toolkit-free form, wrapped later.
- **libjpeg-turbo must not change how a photo looks.** It ignores an embedded ICC profile exactly
  as JavaFX does; the JDK's ImageIO reader converts it, which is why ImageIO is not the JPEG path.
  What TurboJPEG will not decode to 8-bit RGB (CMYK, YCCK, 12-bit) is an `IOException`, and
  `ImageCache` hands the file to JavaFX: the matrix's CMYK JPEG is the test.
- **Animations decode after the still is up.** `ImageCache.animation` decodes every frame of a WebP,
  AVIF/HEIC sequence or JPEG XL on the low-priority thread once the first frame shows, so stepping
  costs no more for an animated file; `ViewerWindow` then swaps frames with `replace`. The frames
  are whole pictures (libwebp and libjxl compose partial ones) and limited to `Animation.MAX_PIXELS`
  in all; a larger animation stays a still. HEIF sequences need libheif 1.20, colour output for JPEG
  XL libjxl 0.9: both are optional bindings, so an older system library still decodes stills.
- **The editor works on a preview and saves from the full image.** `Editor` shrinks the image to
  1600 pixels, makes it upright by the file's orientation (`Edit.upright`, the same "mirror, then
  turn" as `Orientation`), and every turn and flip re-arranges that preview at once. The crop is kept
  as fractions (`Crop`), so the same edit applies to the full image when saving, off the FX thread.
  It takes the window's centre, below the header, so the window's own controls stay usable; while
  it is open, `ViewerWindow.run` refuses what would change the image under it.
- **Saving never overwrites by default.** The save dialog suggests "name (edited).ext" in a format
  Mirada can write (HEIC, AVIF, JPEG XL and WebP become JPEG or PNG); the file is written beside its
  target and moved over it.
- **A saved edit keeps the EXIF, patched in place, never rebuilt.** `ExifBlock` reads the raw TIFF
  block (JPEG's APP1, PNG's eXIf, WebP's EXIF chunk, the HEIF Exif item through iinf and iloc, JPEG
  XL's Exif box) and changes only fixed-size values: orientation to 1, the image sizes to the
  edit's (a SHORT entry widened to LONG in place), and IFD0's link to the thumbnail cut. Nothing
  moves, so maker notes, whose offsets other tools break, survive. `Encoder` puts it into JPEG
  (after JFIF) and PNG (after IHDR), but not a block over JPEG's 64 KB; BMP gets none.
- **A saved TIFF is written by `Encoder.writeTiff`, not ImageIO, around the EXIF block.** The
  block is a TIFF structure whose offsets count from its own header, so the file *starts* with it,
  in its byte order: every offset, maker notes' too, stays true unread. Uncompressed strips and a
  new IFD0 follow, and the header points there; that IFD0 takes the block's IFD0 tags except those
  saying how pixels are stored (`STORAGE_TAGS`), and the profile and XMP as tags 34675 and 700.
- **A saved edit keeps the XMP, as text with a few values changed** (`Xmp`): the orientation to 1
  and the sizes to the edit's wherever it repeats EXIF's (`tiff:`, `exif:`, whatever prefixes the
  packet binds them to), and the xmp: thumbnail, Camera Raw's crop and the extended-XMP note go. A
  packet whose orientation it cannot find to set (another form, a default namespace) is dropped, as
  it would turn the upright picture again. JPEG's extended XMP (past 64 KB) is not carried.
- **A saved edit keeps the ICC profile only while it still describes the pixels.** `IccProfile`
  reads it (JPEG's APP2 segments, PNG's iCCP, WebP's ICCP, the HEIF primary item's colr through
  ipma) and `Encoder` writes it into JPEG, PNG and TIFF beside the EXIF. Mirada shows pixels as
  they are stored, profile unapplied, so carrying it is right for every decoder that leaves them so;
  `Decoder.keepsColours` names the two that do not: libjxl (asked for sRGB) and ImageIO, whose
  TwelveMonkeys WebP reader converts to sRGB. `ImageCache.keepsColours` counts a WebP as ImageIO's
  when libwebp is missing. Only an RGB profile is carried, as the edit is RGB: a CMYK JPEG's profile
  is dropped. A HEIF that gives its colours as nclx codes has no profile to carry; one is not made
  up for it. `NativeDecodersTest` checks each decoder against P3-tagged fixtures.
- **Settings changes are frequent now; theme changes are not.** The window's place is saved as it
  closes (and from `App.stop`, as Cmd-Q may not hide it first), the recent folders on every folder
  opened (after the first paint: writing the settings is not start-up's), and every change runs every
  settings listener. `Theme.apply` returns at once unless the theme itself changed, as restyling
  every window costs tens of milliseconds.
- **Undo is a history of whole steps** (`History`): each records the edit and the shape. A drag
  records once, when the mouse is released; a run of arrow presses records once, as a "run" that
  joins steps until something else happens. Setting the shape from code (open, reset, undo) goes
  through `Editor.quiet`, or its listener would record a step of its own.
- **Copy puts the picture as it stands on screen and the file.** `ViewerWindow.copy` turns the
  full image upright by the file's orientation and the user's turns (`Edit.upright`, then
  `Raster.turned`, off the FX thread) and puts it on the clipboard with the file, so an app that
  takes images pastes the picture and a folder the file. JavaFX's clipboard refuses grey pixels (a
  grey PNG loads so): `putOnClipboard` converts those to ARGB. The metadata recording's tours copy
  every image they show (`-Dmirada.tourCopies`), as nothing else there reaches the clipboard.
- **Move to Trash is three implementations.** Linux follows the freedesktop.org spec itself (info
  file first, created exclusively to reserve the name, then an atomic move) and leaves a file on
  another drive to `gio trash`; Windows calls `SHFileOperationW` through FFM with undo; macOS uses
  AWT's `Desktop`, AWT being headful there. Only Linux's is tested here (`TrashTest`, against a
  temporary trash). Only Linux's can be undone (`Trash.restore`, `Command.UNDO_TRASH`): Mirada
  wrote the info file, so it knows where the file went; it never overwrites a file that came since.
  A file sent to `gio trash` (another drive) cannot be, nor the Recycle Bin's or the Mac's, whose
  Trash needs Full Disk Access to read.
- **A preview stands in at the full image's size.** `ImageViewport` lays out by `imageWidth` and
  `imageHeight`, not the `Image`'s own, so a 1280-pixel preview fills exactly what the full image
  will, and `replace` swaps the pixels without touching zoom or turns.
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

- **JavaFX ignores EXIF orientation.** Phones store pixels as the sensor read them and tag how the
  phone was held; untreated, portrait photos lie on their side. `Orientation` is "mirror first, then
  turn clockwise", which is the order JavaFX applies a node's `scaleX` and `rotate`, and
  `ExifTest.mirrorThenTurnPutsTheCornerWhereTheTagSays` checks all eight against the tag's
  definition. Change one without the other and the mirrored orientations (2, 4, 5, 7) go wrong
  while the common ones still look right.
- **The window waits for both the pixels and the EXIF before it swaps images.** Showing the image as
  soon as it decodes flashes it sideways for a frame; showing it before it decodes flashes an empty
  canvas. `ImageCache.Entry.ready` completes when both are in, and `ViewerWindow.display` drops a
  result for any image other than the one most recently asked for (`wanted`).
- **The window buttons on Linux are JavaFX's, and follow the scene's colour scheme, not the
  stylesheet.** An extended window's minimize, maximize and close are drawn by JavaFX
  (`HeaderButtonOverlay`), light or dark by `Scene.Preferences.colorScheme`, which falls back to
  the system's: `Theme` sets it on every window from the theme chosen, or a dark theme over a light
  system draws the light buttons, dark grey on the dark header. An unfocused window draws them
  `#777` in either style; `mirada.css` lightens the dark one, as a scene's stylesheet reaches the
  overlay. To tell the styles apart in a screenshot: focused, dark draws white glyphs, light `#333`.
- **Escape is ours, not the stage's.** JavaFX's full-screen exit key would leave full screen and
  then let the same press reach the key filter, which would close the window.
  `setFullScreenExitKeyCombination(NO_MATCH)` hands it to `Command.ESCAPE`: leave full screen if in
  it, otherwise close — on Linux only (`Command.escapeCloses`); macOS and Windows keep the window.
- **A strip thumbnail of a JPEG is usually the camera's own** (`Thumbnails.embedded`): IFD1's
  JPEG, taken when it is at least 160 pixels and within 2 % of the photo's shape (`Thumbnails.fits`),
  else the photo at an eighth. metadata-extractor's "adjusted" thumbnail offset counts from the APP1
  segment, not the file, so `Thumbnails.exifTiff` walks the segments to find the TIFF block. On this
  machine's photo folders that took 56–100 % of them and made each 1–5 ms instead of 13–52. The
  queue is newest first and a cell scrolled past releases its request (`Thumbnails.release`).
- **The cache holds full resolution, so it is small.** A decoded image is four bytes a pixel: a
  48-megapixel photo is about 190 MB. Three entries (the current image and one either side).
  Downsampled previews for the fitted view would let it hold more; not done.
- **The viewport turns an `ImageView`, it never copies pixels.** The view is laid out unturned at the
  zoomed size and rotated about its centre, so `layoutChildren` places that centre on the centre of
  where the turned image should stand. Sizes the user sees (properties, fit) are the turned ones:
  `contentWidth`/`contentHeight`.
- **libheif and libjxl turn the image upright themselves; the EXIF orientation must then be
  skipped.** HEIF readers are required to apply the container's `irot`/`imir`, and iPhones write an
  EXIF orientation too; applying both turns the photo twice. `Decoder.turnsUpright` says which
  decoders do it, and `ImageCache` hands those an `Exif.upright()`. The matrix's `heic-grid.heic`
  is laid out as an iPhone's (`scripts/write-heic-grid.py`: cropped tiles, irot and EXIF 6, the
  profile on the tiles), and `NativeDecodersTest` checks it is turned once; no real iPhone photo yet.
- **The FFM bindings are hand-written against the C headers.** `heif_error` is returned by value (a
  struct; the handle takes a `SegmentAllocator` first), `JxlPixelFormat` has 4 bytes of padding
  before its `size_t`, and `JxlBasicInfo`'s offsets (`xsize` 4, `ysize` 8, `alpha_bits` 60) are
  libjxl's ABI from 0.7 to 0.12. A version that moves them breaks decoding, not the build:
  `NativeDecodersTest` is what notices. CI sets `MIRADA_REQUIRE_CODECS=1` on Linux and macOS so
  those tests fail rather than skip when a library stops loading.
- **Native access is enabled for `com.mirada`** in the javafx plugin's options (and for the
  classpath in surefire's `argLine`); without it the JDK warns on every launch. A jlink launcher
  will need the same flag.
- **Windows finds a DLL's dependencies beside the program, not beside the DLL.** `jxl.dll` loaded
  by full path cannot find `brotlidec.dll` next to it, so `NativeLibraries.preloadOnWindows` loads
  every DLL in the bundle first, retrying until nothing more loads. Linux and macOS libraries carry
  `$ORIGIN` / `@loader_path` rpaths instead, which `check-codecs.sh` asserts on Linux.
- **libjxl writes to stderr on a bad file** (`decode.cc: invalid signature`). Noise, not a failure.
- **Only what the first frame shows is built before it.** The header bar is; the floating controls
  are built on the first pointer movement over the image, the properties sidebar and the thumbnail
  strip the first time they open, About and Settings the first time they are asked for, and the
  header's tooltips, its menu's items and the window icon run from `ViewerWindow.afterFirstPaint`,
  on the pulse after the first image is shown (or half a second after start without one). Building
  them first measured +40 ms; deferred, +20 ms over the old window. Put new chrome behind the same
  door. `HeaderBar` itself is not the cost: switched off, the start-up was the same.
- **A window covered by others runs at about one frame a second here.** GNOME throttles a covered
  XWayland window's frame callbacks, so every JavaFX animation and even mouse delivery slows to that
  rate: the floating controls seemed to fade seconds late in a test until timestamps showed wake and
  idle on time and only the pulses slow. Judge timing on an uncovered window, or by timestamps.
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
- **macOS does not pass a double-clicked file as an argument.** Linux (`Exec=… %f`) and Windows do.
  macOS sends an "open documents" Apple Event, which AppKit gives the application's delegate,
  JavaFX's `GlassApplication`. Since JavaFX 25 (JDK-8354631) that delegate answers
  `application:openURLs:`, which AppKit prefers to `application:openFiles:`, and only posts an
  `AWTEmbeddedEvent` notification with each URL, for an AWT embedded in JavaFX: glass's own
  open-files event (`Application.EventHandler.handleOpenFilesAction`) never fires, and AWT's
  `Desktop` handlers start too late for the event that launched the app. So 0.1.0 opened nothing from
  Finder (jpackage's launcher had hidden it before the native image replaced it), and so did a glass
  handler. `MacOpenFiles` observes the notification itself: an Objective-C class made at run time
  through FFM, its method an upcall, registered from `App.main` before `launch`; files that arrive
  before the window are held until `App.start` asks. A file named on the command line also arrives
  as an event and is dropped once (`besides`). The upcall, its lookup and the `objc_msgSend` shapes
  are in the native metadata by hand: the tracing agent never sees a Finder event. Proved on CI's
  Macs, not by hand: the smoke test given `Mirada.app` opens a fixture through `open -a`, then
  again in the running app. If JavaFX starts calling `openFiles:` again, check this is not doubled.
  **The JVM image (Intel) misses the launching file**: jpackage's launcher (`JLI_Launch`) starts
  Java on a second thread while the main thread waits without AppKit, and the event comes and goes
  in between. Files sent to a running Mirada arrive; the smoke test warns there and fails the
  native image.
- **The macOS menu bar is JavaFX's system `MenuBar`, the application menu is glass's.** `MacMenus`
  builds File, Image, View, Go and Help from `Command` after the first paint, unmanaged in the scene
  (it draws nothing there). Only chords with Cmd are key equivalents: a bare one could be taken by
  the menu while the palette's field has focus. Items grey only while their menu is open
  (`onShowing`, by `Command.unavailable`) and are enabled again on close, so a key equivalent never
  meets a stale state. JavaFX gives no way into the application menu (Hide, Quit), so `MacAppMenu`
  gets it from `com.sun.glass.ui.mac.MacApplication.getAppleMenu`, by reflection, since that
  package exists only in macOS's JavaFX: macOS's launchers pass `--add-exports
  javafx.graphics/com.sun.glass.ui.mac=com.mirada` (the pom's `glass.mac.option`, a harmless `-D`
  elsewhere, as the export would warn; `dev-run.sh` and `aot-train.sh` add it on Darwin, and the
  AOT cache needs training and launch to agree), and the native image has the method in its
  reflection metadata, added by hand. Untested on a Mac.
- **The icon is rendered with librsvg, not ImageMagick.** ImageMagick's built-in SVG renderer, used
  when `rsvg-convert` is missing, draws only the tile of these icons (Termina's too). `branding/`
  holds the SVG and the PNG, ICO and ICNS made from it.
- **The macOS bundle is changed after jpackage signs it** (codecs, AOT cache, version), so
  `scripts/fix-mac-bundle.sh` re-signs it ad hoc, last; CI verifies the signature.
- **`java.awt.headless` is decided in `App.main`, as the first statement**, before `HeadStart` can
  touch ImageIO: headless everywhere but macOS, where Move to Trash is AWT's `Desktop`
  (`Trash.headless`); AWT itself loads only when a file is first trashed. ImageIO and `BufferedImage` are `java.desktop` and need no display; tests are
  headless on every platform through surefire's `java.awt.headless`.
- **The TwelveMonkeys readers are found by service lookup**, so nothing names their classes.
  `module-info` requires them anyway, or a jlink image would drop them silently, and
  `ImageFormatsTest.everyImageIoExtensionHasAReader` fails if one leaves the pom.
- **The command palette is a port of Termina's, which is Editora's, and the pure parts are verbatim.**
  `FuzzyMatch`, `PaletteKeys`, `TextInputKeys`, `KeyChord` and `MatchText` are Termina's files with
  the package renamed and `PaletteKeys`' localised legend replaced by plain English. A ranking or
  navigation change belongs in all three projects, or the palettes stop agreeing. One fix went the
  other way and is not in Termina yet: `TextInputKeys.actionFor` checks for a null chord, because a
  bare Ctrl or Shift press has none and `Map.ofEntries` throws on a null key.
- **Bookmarks and notes are Mirada's, never the file's** (`Bookmarks`, Editora's bookmarks and
  personal notes moved to pictures). One mark per path, image or folder: bookmarked, a note, or
  both; with neither it is dropped. Paths are kept absolute and normalised, as `FolderImages` lists
  them, so `indexOf` finds a mark in the open folder. The file is read after the first paint
  (`ViewerWindow.showMarks` does nothing before, so the first image never waits for it), and every
  change reads it again before writing, so a second window's marks survive. A rename in Mirada
  carries the mark (`Bookmarks.moved`); a file moved or renamed elsewhere loses it, and a mark
  whose file is gone is kept (an unplugged drive) but left out of Go to Bookmark. The badge over
  the image (`MarksBadge`) is built the first time a shown image has a mark.
- **The palette also finds images** (`Command.GO_TO_IMAGE`, `CommandPalette.showImages`): its rows
  are `Row`s, a command or an image of the folder, and file names rank by the same `FuzzyMatch`,
  ties in the folder's order (`orderedImages`). Ctrl+P opens it but does not close it: inside the
  palette Ctrl+P is the Emacs key for the row above, so only the palette's own keys close it from
  the keyboard; Escape closes either.
- **While the palette is up, the scene's key filter stands aside.** Filters run from the scene down,
  so `ViewerWindow.onKey` sees every key before the palette's card does; it returns early while the
  palette shows (or typing "r" would rotate the image) and handles only the palette's own keys, to
  close it, and not even those when the open palette moves or edits by them
  (`PaletteChords.keptByPalette`: a user who binds C-p keeps C-p as the row above in there). Escape
  is the palette's then, not the window's.
- **The palette's keys are the user's** (`PaletteChords`, `palette.keys` in the settings, as
  `KeyChord` tokens: `C-S-p M-x f1` by default, `Cmd-S-p` on a Mac). `onKey` asks them before
  `Command.forKey`, which never answers `PALETTE`, so a chosen key wins over its built-in command.
  The first is the one shown (`Command.PALETTE.chord()`), the Mac menu's is the first with Cmd, and
  a setting with no readable key is the default. Parsed on the first key press, not at start-up.
- **Every command has one name and one chord, in `Command`.** The key filter (`forKey`), the
  palette and the context menu all read from it; `CommandTest.everyShownChordRunsItsCommand` checks
  the chord a command shows is one that runs it, on both platforms (the palette's excepted: above). macOS shows its own for two
  (`chord(boolean mac)`): ⌘⌫ to trash, as a Mac's Delete key is Backspace, which steps back; Return
  to rename, as F2 is a brightness key. `forKey` takes the Ctrl key apart from the shortcut for ⌃⌘F.
  Stepping follows the strip: while it stands down the side (`Command.vertical`, set by
  `ViewerWindow.stepAlong`) Up and Down step and Left and Right pan, and `chord()`, the floating
  arrows and the shortcuts window follow; the test checks both axes.
- **A menu's keys are shown, never registered** (`MenuChord`). JavaFX puts a `MenuButton`'s and
  a context menu's accelerators into the scene, where a bare one fires under the palette's text
  field and any other runs a second time. `MenuChord` is a `KeyCombination` that names the
  command's chord and never matches, so `Command.forKey` stays the only way in. Styled as Editora's
  `.menu-item-chord`: 11px in its `-state-key` periwinkle, defined on `.root` in `mirada.css`.
- **A scroll is a wheel's or a trackpad's** (`Scrolling`): macOS reports the fingers
  (`ScrollEvent.getTouchCount`), and those scrolls pan, or swipe once a gesture when there is nothing
  to pan; a wheel zooms. Windows and Linux touchpads report no fingers and so still zoom; Windows
  sends a pinch as Ctrl+wheel. Untested on a real Mac trackpad. A command that cannot run (`Command.unavailable`)
  is greyed with its reason in the palette and disabled in the context menu.
- **Agents look at the UI headless, never on the desktop.** Someone works at this machine, and a
  Mirada window popping up takes their focus. `scripts/headless-run.sh` runs the app on JavaFX's
  Headless platform with software rendering, a throwaway settings folder and the headless clipboard,
  and `HeadlessDriver` (a test class, patched into the module) sends keys and clicks through
  `javafx.scene.robot.Robot` and saves screenshots. Its screen is a fixed 1000×1000 and throws on a
  window drawn past an edge, so the driver puts the window at 1000×760 in the corner and moves every
  popup inside as it shows (a menu opens where it fits, not where the desktop would put it). A shot
  is each window's scene snapshot, composed: the robot's capture reads one buffer every window draws
  into, so the main window's next frame paints over an open menu. There are no window buttons, no
  GTK and no XWayland, so check those on the real desktop only when the user asks for it.

## Conventions

- **Extract the decision, then test it.** Anything with a rule in it — sort order, which key does
  what, zoom stops, where an image may be panned to — is a static method with its own tests (`Zoom`,
  `Command.forKey`, `NaturalOrder`, `FolderImages.select`, `Exif.summary`).
- **Fast first.** Startup and stepping are the product. Prefetch neighbours after the current image
  is up, never before it; no per-pulse work.

## Null annotations

JSpecify annotations, checked by NullAway in the main compile — Editora's setup, the same rules as
Termina and Torrenta. `mvn compile` fails on any null that can reach a type not marked nullable.

- **Every package is `@NullMarked`**, through its `package-info.java`; `NullMarkedPackagesTest` fails
  for a package without one, and if NullAway leaves the pom.
- **Mark what really may be null** (`private @Nullable Image image`), and prefer a shape that cannot
  be null when the null is an accident of the code.
- **NullAway does not follow a boolean that holds a null check**, nor a getter called twice: read it
  into a local and check inline.
- **JavaFX returns null more often than it looks:** `Node.getScene()`, `Scene.getWindow()`,
  `Path.getFileName()`, `Path.getParent()`, `FileChooser.showOpenDialog`.
- **Plumbing.** Only NullAway runs, only on the main compile. `.mvn/jvm.config` exports the javac
  internals Error Prone needs. JSpecify is `provided`, read through `requires static org.jspecify`.

## Not done

Signing:
Developer ID and notarization on macOS (wired, waiting for the secrets), a code-signing certificate
on Windows. A Homebrew cask (Homebrew takes only notarized apps); winget-pkgs has 0.3.0 under review (microsoft/winget-pkgs#471599), not yet merged. A TIFF's own EXIF when an edit of it is saved; a profile for a HEIF with nclx colours or a JPEG XL; a Brotli-compressed JPEG XL Exif box. Trash on Windows and macOS is untested. HEIC grids are tested on an iPhone-style file, not a real iPhone photo. The app itself has not been run on Windows
or macOS by hand; CI only tests there, and the macOS Finder double-click is tested only through `open -a` on CI; on an Intel Mac (the JVM image) a launch from Finder still opens an empty window.
