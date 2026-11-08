# Testing

What the tests cover, how they run, and how to look at the UI without a window on the desktop.

## Running them

```bash
./mvnw test                       # every test
./mvnw -DexcludedGroups=fx test   # the pure tests only, without starting JavaFX
./mvnw verify                     # what CI runs: NullAway, every test, the format check
```

Surefire runs the tests on the classpath, so they reach package-private members, on JavaFX's
Headless platform with software rendering and with `java.awt.headless` (`pom.xml`). A test that
starts the toolkit does it through `FxToolkit` and is `@Tag("fx")`. Why there is no TestFX:
[decision 0005](decisions/0005-no-testfx-yet.md).

The native decoders' tests read the codec bundle from `target/codecs` (`mirada.codecs`) and skip a
library that does not load, unless `MIRADA_REQUIRE_CODECS=1`, which CI sets on Linux and macOS so a
library that stops loading fails instead.

## What is tested where

- **Rules**, each a static method with its own plain test ([conventions.md](conventions.md#conventions)):
  `ZoomTest`, `NaturalOrderTest`, `FolderImagesTest`, `SortingTest`, `RenamingTest`,
  `BookmarkListTest`, `ScrollingTest`, `CommandTest`, the palette's `FuzzyMatchTest`,
  `PaletteKeysTest` and `KeyChordTest`.
- **Guards** that fail when a project rule is broken: `NullMarkedPackagesTest` (every package
  `@NullMarked`), `CommandTest.everyShownChordRunsItsCommand`, `EditionTest` (the README, AGENTS.md
  and the pom name the same Pauta edition), `ImageFormatsTest.everyImageIoExtensionHasAReader`.
- **Decoders**: `NativeDecodersTest` (each native library, the P3-tagged fixtures, the HEIC grid
  turned once), `ImageIoDecoderTest`, `AnimationTest`, `MatrixTest`.
- **Orientation**: `ExifTest.mirrorThenTurnPutsTheCornerWhereTheTagSays`, all eight against the tag's
  definition.
- **Saving an edit**: `EncoderTest`, `ExifBlockTest`, `IccProfileTest`, `XmpTest`, `HistoryTest`.
- **Settings and bookmarks**: `SettingsTest`, `BookmarksTest`, `ConfigFileTest`.
- **Trash**: `TrashTest`, Linux's only, against a temporary trash.

## The format matrix

CI's packaging job opens every fixture and every variant of the format matrix in each app image
(`src/test/resources/com/mirada/matrix`, from `scripts/make-format-matrix.sh`); `MatrixTest`
decodes the matrix on the JVM. Add a variant there when a new kind of file turns up a problem.

## Looking at the UI headless

```bash
scripts/headless-run.sh photo.jpg -- 'key C-S-p' 'type rot' 'shot /tmp/palette.png'
```

The steps are listed in `src/test/java/com/mirada/ui/HeadlessDriver.java`.

- **Agents look at the UI headless, never on the desktop.** Someone works at this machine, and a
  Mirada window popping up takes their focus. `scripts/headless-run.sh` runs the app on JavaFX's
  Headless platform with software rendering, a throwaway settings folder and the headless clipboard,
  and `HeadlessDriver` (a test class, patched into the module) sends keys and clicks through
  `javafx.scene.robot.Robot` and saves screenshots. Its screen is a fixed 1000×1000 and throws on a
  window drawn past an edge, so the driver puts the window at 1000×760 in the corner and moves every
  popup inside as it shows (a menu opens where it fits, not where the desktop would put it). A shot
  is each window's scene snapshot, composed: the robot's capture reads one buffer every window draws
  into, so the main window's next frame paints over an open menu. What Mirada would start outside
  itself cannot reach the desktop: xdg-open, gdbus and dbus-send are stubs that list their calls at
  the end (Show in Folder shows as a `gdbus … ShowItems`), and the display and session bus are unset.
  Delete, Rename and saving an edit do change files, so drive a copy of a folder for those; file
  dialogs are unsupported there and fail the run. There are no window buttons, no GTK and no
  XWayland, so check those on the real desktop only when the user asks for it.
- **A link stops a headless run.** Documentation and About's links go through
  `HostServices.showDocument`, which JavaFX's Headless platform does not implement
  (`UnsupportedOperationException`), so a click on one ends the run; shoot before it.

## Timing

`MIRADA_PERF=1 MIRADA_PERF_TOUR=1` on any launcher prints each step's time
(`MIRADA_PERF_TOUR_PAUSE=0` for the no-prefetch worst case), and `scripts/measure-startup.sh` the
median launch: [building-and-packaging.md](building-and-packaging.md#start-up-measured).

## What CI runs

`.github/workflows/ci.yml`: `verify` on Linux, macOS and Windows, with the test reports uploaded
whether it passes or not; then, per target, the app image as it ships, smoke-tested on every fixture
and every variant of the format matrix, the Linux packages checked, the deb installed and the
AppImage run.
