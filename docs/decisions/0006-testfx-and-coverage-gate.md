# 0006. TestFX over the Headless glass, and a coverage gate at `verify`

Accepted, 2026-11-08. Supersedes [0005](0005-no-testfx-yet.md).

## Context

Pauta 4.0.1 asks for TestFX over JavaFX's Headless glass (PLAT-11), a JaCoCo check at `verify` of
0.90 lines and methods with per-package floors (PLAT-12, QUAL-03), and tests of the window itself:
nothing optional built before the first paint (PERF-14, TABS-14), deferred work started after it
(PERF-15), and a closed window that leaves nothing running (PERF-13). Mirada's tests were rules
tested on their own plus a few that made an `Image`; 47 % of its lines were run by a test, and
almost none of the window's.

## Decision

- TestFX (`testfx-core`, `testfx-junit5`, test scope) starts the toolkit once on the Headless glass
  (`FxTestSupport.start`), and its `FxToolkit.cleanupStages` closes what each test opened. Every test
  that starts the toolkit is `@Tag("fx")`; no Monocle, no xvfb.
- The window is tested as a user drives it (`WindowFixture`): commands run as the keys, palette and
  menus run them, keys go through the scene's filter, dialogs are answered on the FX thread while
  `showAndWait` waits, and file dialogs, which the Headless glass cannot show, go through one seam,
  `FileDialogs`, that a test answers. What is checked is what the user would see or find: the title,
  the panels, the status bar's words, the files, the clipboard.
- JaCoCo gates `verify`: the floors in [testing.md](../testing.md#the-coverage-gate), which
  `BuildHygieneTest` holds to the pom. The start-up timer and the AOT-training tour
  (`com.mirada.perf`) are not measured: they act only in launches made for them and end the JVM.

## Consequences

- `./mvnw verify` runs about 500 tests in 15 seconds on this machine, and fails when coverage falls.
- Writing the window tests found three bugs, fixed with them: the tool windows outlived the viewer
  and kept the app running; a closed window's decoder, preview and thumbnail threads (the Bookmarks
  window's too) were never stopped; and the Bookmarks and Notes window threw for a folder marked
  with no image marked in it.
- Branches were the hardest of the three to raise: many are defences against damaged files, reached
  by cutting short and overwriting real fixtures (`DamagedFilesTest`, `DamagedImagesTest`) rather
  than one test per branch. Linux measures 0.80 against the 0.79 floor, the other platforms about
  half a point less, so a branch-heavy change without tests can fail the gate there first.
- A test of the window must not reach the desktop: surefire puts Linux's trash under `target/`
  (`XDG_DATA_HOME`) and unsets the display and the session bus; Show in Folder is not driven.
