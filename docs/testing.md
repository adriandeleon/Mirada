# Testing

What the tests cover, how they run, and how to look at the UI without a window on the desktop.

## Running them

```bash
./mvnw test                       # every test, and the coverage report in target/site/jacoco
./mvnw -DexcludedGroups=fx test   # the pure tests only, without starting JavaFX
./mvnw verify                     # what CI runs: NullAway, every test, the coverage gate, the format check
```

Surefire runs the tests on the classpath, so they reach package-private members, on JavaFX's
Headless platform with software rendering and with `java.awt.headless` (`pom.xml`). A test that
starts the toolkit does it through `FxTestSupport.start` (TestFX's `FxToolkit`, over the Headless
glass: no display, no xvfb, no Monocle) and is `@Tag("fx")`, which `BuildHygieneTest` checks.
`FxTestSupport` also keeps every window inside the headless screen, which is 1000 pixels square and
throws on a window drawn past its edge. Why TestFX: [decision 0006](decisions/0006-testfx-and-coverage-gate.md).

No test reaches the desktop. Surefire points Linux's trash at `target/test-data-home`
(`XDG_DATA_HOME`) and unsets `DISPLAY`, `WAYLAND_DISPLAY` and the session bus; the update check is
off and Check for Updates asks a closed local port.

The native decoders' tests read the codec bundle from `target/codecs` (`mirada.codecs`) and skip a
library that does not load, unless `MIRADA_REQUIRE_CODECS=1`, which CI sets on Linux and macOS so a
library that stops loading fails instead.

## The coverage gate

JaCoCo measures every test run; `verify` fails when coverage falls under these floors (the
`jacoco-check` execution in `pom.xml`, Pauta PLAT-12 and QUAL-03). `BuildHygieneTest` fails when this
table and the pom disagree, so change both together.

| Scope | Counter | Floor |
|---|---|---|
| Bundle | LINE | 0.90 |
| Bundle | METHOD | 0.90 |
| Bundle | BRANCH | 0.79 |
| `com.mirada.edit` | LINE | 0.95 |
| `com.mirada.ui` | LINE | 0.93 |
| `com.mirada.settings` | LINE | 0.92 |
| `com.mirada.image` | LINE | 0.86 |
| `com.mirada.os` | LINE | 0.35 |

- **A floor is a regression net, not a target.** The whole build is held to Pauta's 0.90 of lines
  and methods and 0.79 of branches; each package of 300 lines or more has a floor about three
  points under what Linux measured (2026-11-08: lines 0.93, methods 0.93, branches 0.80; edit 0.98,
  ui 0.96, settings 0.95, image 0.89, os 0.39). macOS and Windows skip the Linux-only trash tests and
  measure about half a point less (branches 0.796 without them). When a package's coverage rises,
  raise its floor.
- **Branches are the tightest.** Many of them are defences against damaged files, which
  `DamagedFilesTest` and `DamagedImagesTest` reach by cutting and overwriting real fixtures; a new
  parser or decoder path wants the same.
- **`os` is low on purpose.** Most of it is macOS's and Windows' glue (the Objective-C bridge for
  Finder's open-files event, the application menu, the Recycle Bin through FFM, `gio trash`), which
  CI's smoke tests of the packaged app exercise, not a unit test on Linux.
- **Not measured: `com.mirada.perf`**, the start-up timer and the AOT-training tour. They act only in
  a launch made with `MIRADA_PERF` or `-Dmirada.aotTrain`, read those once as the class loads, and
  end the JVM; `-Pdist` runs the tour each time it trains the AOT cache.
- The report is `target/site/jacoco/index.html`. Each run measures afresh (`append` is off), so a
  local `verify` gates what it ran.

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
- **Trash**: `TrashTest`, Linux's only, against a temporary trash; `TrashUndoTest` through the
  window, against surefire's trash under `target/`.
- **The window, as a user drives it** (`WindowFixture`): `ViewerWindowTest` (stepping, the image,
  the panels, the palette, bookmarks and notes, renaming, the slideshow, the editor, the context
  menu), `SettingsWindowTest`, `BookmarksWindowTest`, `ToolWindowsTest`, `ChromeTest`,
  `PointerTest` (the wheel, the trackpad, dragging, the crop), `PaletteKeyboardTest`. Commands run as
  the keys and menus run them; a dialog is answered on the FX thread while `showAndWait` waits
  (`WindowFixture.answerNextDialog`), and a file dialog, which the Headless glass cannot show,
  through `FileDialogs.answeredBy`.
- **The first frame comes first and a closed window lets go**: `StartupDeferralTest` (no panel or
  tool window built and no deferred work started before the first paint: PERF-14, PERF-15, TABS-14)
  and `ClosedWindowTest` (windows opened, used and closed leave no thread, timer or tool window, and
  can be collected: PERF-13).
- **Everything that grows is bounded** (PERF-11): `ImageCacheTest`, `ThumbnailsTest`,
  `TrashUndoTest`, `HistoryTest`, `MessageLogTest`, `DebugLogTest` fill each past its cap and find
  the oldest dropped.
- **Every command, in every state** (QUAL-11, QUAL-08): `CommandSweepTest` runs the whole registry
  in an empty window, on an image, turned and zoomed, in the editor and full screen, and finds
  Mirada's real settings folders and the desktop's trash untouched afterwards.
- **Damaged files**: `DamagedFilesTest` cuts short and overwrites the fixtures of every container
  the EXIF, profile and XMP readers walk; `DamagedImagesTest` gives every decoder the format matrix
  cut short. Each must read the file or refuse it with an IOException, nothing else.
- **The build itself**: `NoticeCoverageTest` (every dependency that ships is named in NOTICE),
  `BuildHygieneTest` (LICENSE and NOTICE are packaged, the coverage gate is wired and quoted here
  right, FX tests are tagged), `ReleaseSupplyChainTest`.

## The format matrix

CI's packaging job opens every fixture and every variant of the format matrix in each app image
(`src/test/resources/com/mirada/matrix`, from `scripts/make-format-matrix.sh`); `MatrixTest`
decodes the matrix on the JVM. Add a variant there when a new kind of file turns up a problem.

## Looking at the UI headless

```bash
scripts/headless-run.sh photo.jpg -- 'key C-S-p' 'type rot' 'shot /tmp/palette.png'
```

The steps are listed in `src/test/java/com/mirada/ui/HeadlessDriver.java`. A step that fails (a key
name it does not know, such as `Return` for `Enter`) is named on stderr and the run exits 1: Mirada's
Debug Log takes uncaught exceptions, so the driver says it itself. Mirada's own options go before the
file (`scripts/headless-run.sh --new-instance photo.jpg -- …`).

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

`.github/workflows/ci.yml`: `verify` on Linux, macOS and Windows, the coverage gate included, with
the test reports and the coverage report uploaded whether it passes or not and the coverage totals
written to the job's summary; then, per target, the app image as it ships, smoke-tested on every fixture
and every variant of the format matrix, the Linux packages checked, the deb installed and the
AppImage run.
