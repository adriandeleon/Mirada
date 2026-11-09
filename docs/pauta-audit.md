# Pauta audit

Mirada at `b82eefc` and the translation work on it, audited against [Pauta 4.0.1](https://pauta-project.dev/4.0.1/) on 2026-11-08.
Scope: every rule (351). Rules from https://pauta-project.dev/4.0.1/rules.json.

Regenerated rather than edited: each rule was judged by its check (code, test or by hand) as the
edition's [audit procedure](https://pauta-project.dev/4.0.1/coding-agents/) says. A rule met only in
part is a fail. A rule is n.a. only when its text names apps Mirada is not (a tray, tabs, a terminal),
never because Mirada lacks a part another rule requires.

|        | Pass | Fail | Manual | N.a. |
|--------|-----:|-----:|-------:|-----:|
| MUST   | 135 | 1 | 6 | 20 |
| SHOULD | 71 | 40 | 16 | 53 |
| MAY    | 1 | 2 | 0 | 6 |

A failed MUST blocks a release.

## Failed MUSTs

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [REL-19](https://pauta-project.dev/4.0.1/release/#rel-19) | MUST | fail | `.github/workflows/ci.yml:15`: push to main and every pull request; Format (`spotless:check`) and Build & Test (`./mvnw verify`, the JaCoCo gate included) are separate jobs. Missing: the two checks are not required on main yet (branch protection, a repository setting the owner turns on) |

## Other failures

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [PLAT-14](https://pauta-project.dev/4.0.1/platform/#plat-14) | SHOULD | fail | `src/main/java/com/mirada/settings/Settings.java:456`: .properties, no schema version |
| [DOCS-03](https://pauta-project.dev/4.0.1/documentation/#docs-03) | SHOULD | fail | `mirada-website/astro.config.mjs:27`: docs not versioned; no docs URL in the app |
| [DOCS-04](https://pauta-project.dev/4.0.1/documentation/#docs-04) | SHOULD | fail | mirada-website (branch `docs-screenshots-whats-new`, not yet on main): `scripts/GenCommands.java` and `scripts/GenWhatsNew.java` generate the commands, the version and What's new; no Roadmap from TODO.md and no `npm run check:docs` |
| [DOCS-10](https://pauta-project.dev/4.0.1/documentation/#docs-10) | MAY | fail | no per-command docs |
| [DOCS-11](https://pauta-project.dev/4.0.1/documentation/#docs-11) | MAY | fail | no Help-for-page links in Settings |
| [DOCS-15](https://pauta-project.dev/4.0.1/documentation/#docs-15) | SHOULD | fail | no stacked Javadoc today, but no test guards it |
| [QUAL-14](https://pauta-project.dev/4.0.1/quality/#qual-14) | SHOULD | fail | `src/test/java/com/mirada/ui/AccentTest.java:36`: contrast for the accent only, not the stylesheet's tokens |
| [PERF-17](https://pauta-project.dev/4.0.1/performance/#perf-17) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:1947`: AOT cache trained; mirada.css shipped as .css, not .bss |
| [KBD-04](https://pauta-project.dev/4.0.1/keyboard/#kbd-04) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:249`: the palette greys with a reason; menus disable without one |
| [KBD-10](https://pauta-project.dev/4.0.1/keyboard/#kbd-10) | SHOULD | fail | no IntelliJ, VS Code or Sublime keymaps |
| [KBD-12](https://pauta-project.dev/4.0.1/keyboard/#kbd-12) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:264`: picks the keymap and edits the palette's keys; other keys only as `keys.<id>` in the file, no editor listing every command |
| [KBD-13](https://pauta-project.dev/4.0.1/keyboard/#kbd-13) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:217`: chords typed into descriptions ("T toggles it", "F5 starts it"); the editor's and Bookmarks window's tooltips and hints now read the keymap (Editor.keyOf) |
| [KBD-16](https://pauta-project.dev/4.0.1/keyboard/#kbd-16) | SHOULD | fail | `src/main/resources/com/mirada/styles/mirada.css:791`: one `:focus-visible` layer at the end, but no FX test tabs to each control family |
| [KBD-18](https://pauta-project.dev/4.0.1/keyboard/#kbd-18) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:68`: on/off rows are CheckBoxes, not a switch |
| [WIN-04](https://pauta-project.dev/4.0.1/window-shell/#win-04) | SHOULD | fail | `src/main/java/com/mirada/ui/NoteDialog.java:32`: each dialog styles itself; no Dialogs.styled |
| [WIN-08](https://pauta-project.dev/4.0.1/window-shell/#win-08) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:529`: window place restored (WindowPlacementTest), the open folder isn't; no --no-session |
| [WIN-09](https://pauta-project.dev/4.0.1/window-shell/#win-09) | SHOULD | fail | `src/main/java/com/mirada/ui/Command.java:415`: Ctrl/Cmd + - 0 zoom the image; the zoom isn't kept |
| [ICON-05](https://pauta-project.dev/4.0.1/icons-and-color/#icon-05) | SHOULD | fail | `src/main/java/com/mirada/ui/Theme.java:83`: Cupertino, not Caret & Ink |
| [ICON-09](https://pauta-project.dev/4.0.1/icons-and-color/#icon-09) | SHOULD | fail | `src/main/resources/com/mirada/styles/mirada.css:116`: fg-subtle only for disabled text, but no test |
| [TABS-10](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-10) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:589`: title "name — Mirada", without the folder |
| [TABS-15](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-15) | SHOULD | fail | strip layout persists; open panels are a start setting, not the last state |
| [MENU-03](https://pauta-project.dev/4.0.1/menus/#menu-03) | SHOULD | fail | `src/main/java/com/mirada/ui/TitleBar.java:246`: the header menu refreshes chords on showing but never greys a row |
| [MENU-12](https://pauta-project.dev/4.0.1/menus/#menu-12) | SHOULD | fail | no danger class on Move to Trash or Remove |
| [SET-08](https://pauta-project.dev/4.0.1/settings/#set-08) | SHOULD | fail | `src/main/java/com/mirada/settings/Settings.java:124`: file names are constants, but .properties, with the window's place and recent folders (`:52`) in the preferences file, no separate state file |
| [SET-17](https://pauta-project.dev/4.0.1/settings/#set-17) | SHOULD | fail | no Settings Sync |
| [DIAG-07](https://pauta-project.dev/4.0.1/diagnostics/#diag-07) | SHOULD | fail | no Doctor page (a row per codec library would fit) |
| [PAL-05](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-05) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:368`: selects the first enabled row, but no test with a disabled top match |
| [PAL-07](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-07) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:242`: Esc, C-g and the scrim cancel, but focus is not restored |
| [PAL-08](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-08) | SHOULD | fail | no per-command docs page; F1 opens the palette |
| [QOPEN-02](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-02) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:37`: the picker card has no title naming the command |
| [QOPEN-11](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-11) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:172`: a second card is refused rather than replacing the first |
| [QOPEN-12](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-12) | SHOULD | fail | `src/main/java/com/mirada/ui/BookmarksWindow.java:221`: the Bookmarks list maps keys by hand, not through PaletteKeys |
| [CONF-05](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-05) | SHOULD | fail | `src/main/java/com/mirada/ui/Editor.java`: no Save / Discard / Cancel question |
| [CONF-12](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-12) | SHOULD | fail | `src/test/java/com/mirada/ui/ConfirmTest.java`: the words come from pure builders, tested; no capture hook for UI tests |
| [MLOG-05](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-05) | SHOULD | fail | `src/main/java/com/mirada/diagnostics/MessageLog.java:14`: a message's severity is said where it is raised (the direct channel), since its words are translated; keys are not named by severity (`status.*.failed`), so no test classifies them |
| [SYNC-01](https://pauta-project.dev/4.0.1/templates/settings-sync/#sync-01) | SHOULD | fail | no Settings Sync |
| [REL-09](https://pauta-project.dev/4.0.1/release/#rel-09) | SHOULD | fail | `scripts/build-appimage.sh:84`: metainfo generated only inside the AppImage; no packaging/linux/io.github.adriandeleon.Mirada.metainfo.xml |
| [REL-12](https://pauta-project.dev/4.0.1/release/#rel-12) | SHOULD | fail | `.github/workflows/release.yml:15`: no Homebrew cask (needs notarization) |
| [REL-13](https://pauta-project.dev/4.0.1/release/#rel-13) | SHOULD | fail | `.github/workflows/winget.yml`: writes and tests the manifests, but the winget-pkgs PR is opened by hand, not by JReleaser |
| [REL-14](https://pauta-project.dev/4.0.1/release/#rel-14) | SHOULD | fail | no PACKAGES_TOKEN, no packagers |
| [REL-17](https://pauta-project.dev/4.0.1/release/#rel-17) | SHOULD | fail | `.github/workflows/release.yml:11`: the MSI is unsigned (Pauta's own open decision) |
| [REL-20](https://pauta-project.dev/4.0.1/release/#rel-20) | SHOULD | fail | `.github/workflows/ci.yml:37`: the full suite runs on three OSes per push, but no full-tests.yml (daily, full-ci label, flaky summary) |

## Manual checks

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [DOCS-08](https://pauta-project.dev/4.0.1/documentation/#docs-08) | SHOULD | manual | `src/main/java/com/mirada/ui/ShortcutsWindow.java`: lists every key by group, no search field |
| [QUAL-10](https://pauta-project.dev/4.0.1/quality/#qual-10) | SHOULD | manual | guard tests exist (NullMarkedPackagesTest, CommandTest.everyShownChordRunsItsCommand); seen failing is not recorded |
| [PERF-18](https://pauta-project.dev/4.0.1/performance/#perf-18) | SHOULD | manual | costs recorded in commits and docs/building-and-packaging.md "Start-up, measured" |
| [PERF-22](https://pauta-project.dev/4.0.1/performance/#perf-22) | SHOULD | manual | `docs/building-and-packaging.md`, `docs/reference/startup-jlink.md`: record before/after numbers |
| [KBD-15](https://pauta-project.dev/4.0.1/keyboard/#kbd-15) | SHOULD | manual | `src/main/java/com/mirada/ui/OnScreenControls.java:198`: chrome out of the Tab order; Tab through Settings, Bookmarks and dialogs by hand |
| [KBD-20](https://pauta-project.dev/4.0.1/keyboard/#kbd-20) | SHOULD | manual | no translations to try; labels wrap |
| [WIN-06](https://pauta-project.dev/4.0.1/window-shell/#win-06) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:1312`: empty window offers Open Image and Open Folder only |
| [TBAR-06](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-06) | SHOULD | manual | `src/main/resources/com/mirada/styles/mirada.css:36`: one band per edge; hairline against the image |
| [TABS-23](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-23) | SHOULD | manual | the properties sidebar follows the image shown |
| [MENU-05](https://pauta-project.dev/4.0.1/menus/#menu-05) | MUST | manual | `src/main/java/com/mirada/ui/ThumbnailStrip.java:105`: the strip's menu is the strip's, not the clicked image's; bookmark row actions aren't commands |
| [MENU-08](https://pauta-project.dev/4.0.1/menus/#menu-08) | MUST | manual | `src/main/java/com/mirada/ui/Popups.java:48`: hide-on-escape on; focus stays |
| [MENU-10](https://pauta-project.dev/4.0.1/menus/#menu-10) | SHOULD | manual | `src/main/java/com/mirada/ui/BookmarksWindow.java:413`: bookmark rows select first; the strip's menu is strip-wide |
| [SET-02](https://pauta-project.dev/4.0.1/settings/#set-02) | MUST | manual | `src/main/java/com/mirada/ui/SettingsWindow.java:245`: the search field on top, the sidebar of pages with icons (`SettingsPages.java:20`) on the left, the page on the right; seen headless (scripts/headless-run.sh, Ctrl+,): open Settings and look |
| [SET-06](https://pauta-project.dev/4.0.1/settings/#set-06) | MUST | manual | `src/main/java/com/mirada/ui/SettingsPages.java:59`: every row names its palette command (THEME, KEYMAP, HIDE_CONTROLS_AFTER, SLIDESHOW_SPEED ask in the palette; the rest flip; `SettingsCommands.java:62`); `SettingsPagesTest.java:128` fails a setting command without a row; walk the rows in Settings |
| [DIAG-03](https://pauta-project.dev/4.0.1/diagnostics/#diag-03) | MUST | manual | `src/main/java/com/mirada/ui/DebugLogWindow.java:29`: an unowned, modeless Stage with Refresh, Copy, Clear, Export…, Report a Problem…; open it from Help and keep working in the main window |
| [CLI-04](https://pauta-project.dev/4.0.1/command-line/#cli-04) | SHOULD | manual | `src/main/java/com/mirada/Launch.java:81`: `--no-session` taken; Mirada restores no session, so only the named file opens (seen headless); LaunchTest.noSessionIsTakenAndOpensOnlyWhatIsNamed |
| [CLI-06](https://pauta-project.dev/4.0.1/command-line/#cli-06) | SHOULD | manual | `src/main/java/com/mirada/App.java:180`: only the first argument opens, the rest are ignored; a handed-off launch likewise (`App.java:201`) |
| [INTL-03](https://pauta-project.dev/4.0.1/i18n/#intl-03) | SHOULD | manual | the five catalogs were translated in full against Editora's terms; a value equal to English is a name or a pattern (Mirada, JPEG, `{0} · {1}`); no test catches an untranslated sentence, so it is checked in review |
| [INTL-04](https://pauta-project.dev/4.0.1/i18n/#intl-04) | SHOULD | manual | German and Spanish looked at headless at 1000 px: Settings rows wrap (`SettingsWindow.java:276`), the editor's bar wraps its three groups to a second row instead of cutting (`Editor.java:160`); Italian, French and Portuguese not looked at |
| [WELC-02](https://pauta-project.dev/4.0.1/templates/welcome/#welc-02) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:99`: empty state offers Open Image and Open Folder; chords only in tooltips, no Open Recent or palette |
| [ABOUT-07](https://pauta-project.dev/4.0.1/templates/about/#about-07) | MUST | manual | this audit; 4.0.1 is honest only once its failed MUSTs pass |
| [CONF-07](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-07) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:1082`: Move to Trash asks nothing and offers Ctrl+Z (Linux only) |

## Passed and not applicable

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [PLAT-01](https://pauta-project.dev/4.0.1/platform/#plat-01) | MUST | pass | `pom.xml:233`: maven.compiler.release 25 (`pom.xml:17`), enforcer requireJavaVersion `[25,)`; on 26+ the formatter-needs-jdk25 profile skips only the format check |
| [PLAT-02](https://pauta-project.dev/4.0.1/platform/#plat-02) | MUST | pass | `pom.xml:388`: dist profile: jpackage app image with jLinkOptions; the native image replaces the runtime on four targets |
| [PLAT-03](https://pauta-project.dev/4.0.1/platform/#plat-03) | MUST | pass | `pom.xml:28`: javafx.version 27 |
| [PLAT-04](https://pauta-project.dev/4.0.1/platform/#plat-04) | MUST | pass | `src/main/java/module-info.java:1`: module com.mirada, no opens |
| [PLAT-05](https://pauta-project.dev/4.0.1/platform/#plat-05) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:83`: AtlantaFX 3.0.0 Cupertino Light and Dark |
| [PLAT-06](https://pauta-project.dev/4.0.1/platform/#plat-06) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:9`: every colour named once in the `.root` block, from `-color-*` roles or ladders of them; no rule after it holds a fixed colour (StyleContrastTest.noRuleAfterTheTokenBlockHoldsAFixedColour) |
| [PLAT-07](https://pauta-project.dev/4.0.1/platform/#plat-07) | SHOULD | pass | `src/main/java/com/mirada/ui/Fonts.java:46`: the four Inter faces and OFL.txt, registered before the first window is styled; `ui-font.css` on every scene |
| [PLAT-08](https://pauta-project.dev/4.0.1/platform/#plat-08) | MUST | pass | mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties committed |
| [PLAT-09](https://pauta-project.dev/4.0.1/platform/#plat-09) | MUST | pass | `pom.xml:261`: -Xep:NullAway:ERROR; @NullMarked everywhere (NullMarkedPackagesTest) |
| [PLAT-10](https://pauta-project.dev/4.0.1/platform/#plat-10) | MUST | pass | `pom.xml:217`: palantirJavaFormat; spotless check bound to verify |
| [PLAT-11](https://pauta-project.dev/4.0.1/platform/#plat-11) | MUST | pass | `pom.xml:325`: surefire sets glass.platform Headless; TestFX 4.0.18 (test scope) starts the toolkit (`src/test/java/com/mirada/ui/FxTestSupport.java:40`), every FX test `@Tag("fx")` (`BuildHygieneTest.everyTestThatStartsTheToolkitIsTaggedFx`); no Monocle; no Maven run under xvfb (xvfb stays only for the packaged GTK app's smoke test, ci.yml:217) |
| [PLAT-12](https://pauta-project.dev/4.0.1/platform/#plat-12) | MUST | pass | `pom.xml` jacoco-check at verify: BUNDLE LINE 0.90, METHOD 0.90, BRANCH 0.79 (Linux measures 0.93, 0.93, 0.80; without the Linux-only trash tests 0.92, 0.92, 0.796), and a LINE floor per large package; com.mirada.perf (timing and AOT-training launches only) not measured |
| [PLAT-13](https://pauta-project.dev/4.0.1/platform/#plat-13) | SHOULD | pass | `src/main/java/com/mirada/diagnostics/DebugLog.java:114`: a handler on the root JUL logger; the code logs through java.util.logging only (System.out is the CLI and the perf trace) |
| [REPO-01](https://pauta-project.dev/4.0.1/repository/#repo-01) | MUST | pass | `README.md:1`: 156 lines: logo, badges, pitch, hero, highlights, install, command line, config folder, build, licence; the rest links into mirada-project.dev/docs |
| [REPO-02](https://pauta-project.dev/4.0.1/repository/#repo-02) | MUST | pass | `CHANGELOG.md:3`: Keep a Changelog 1.1 and SemVer, "## [Unreleased]" with Added/Changed/Fixed, bold-led entries |
| [REPO-03](https://pauta-project.dev/4.0.1/repository/#repo-03) | MUST | pass | LICENSE:3 MIT License, Copyright (c) 2026 Adrián Arturo De León Saldivar |
| [REPO-04](https://pauta-project.dev/4.0.1/repository/#repo-04) | MUST | pass | NOTICE:1 name, copyright, "licensed under the MIT License", every component with groupId:artifactId; `src/test/java/com/mirada/NoticeCoverageTest.java:57` (every shipping pom dependency named), `src/test/java/com/mirada/BuildHygieneTest.java` (LICENSE and NOTICE in the jar's META-INF/mirada, add-notices in -Pdist, checked in the Linux packages, --license-file) |
| [REPO-05](https://pauta-project.dev/4.0.1/repository/#repo-05) | MUST | pass | `AGENTS.md:55`: commands, one worktree per task, the change checklist; CLAUDE.md points to it |
| [REPO-06](https://pauta-project.dev/4.0.1/repository/#repo-06) | MUST | pass | `CONTRIBUTING.md:13`: build, test and format commands and the pull-request steps |
| [REPO-07](https://pauta-project.dev/4.0.1/repository/#repo-07) | MUST | pass | `docs/README.md:14`: architecture.md, conventions.md, testing.md, gotchas.md, release.md, building-and-packaging.md and decisions/ all exist |
| [REPO-08](https://pauta-project.dev/4.0.1/repository/#repo-08) | MAY | pass | `TODO.md:1`: the open list, moved from AGENTS.md's "Not done" |
| [REPO-09](https://pauta-project.dev/4.0.1/repository/#repo-09) | MUST | pass | `.github/ISSUE_TEMPLATE/bug_report.yml`: fields id: environment and id: report |
| [REPO-10](https://pauta-project.dev/4.0.1/repository/#repo-10) | MUST | pass | branding/mirada-icon.svg with png, icns, ico; pom.xml:67 jpackage.icon |
| [DOCS-01](https://pauta-project.dev/4.0.1/documentation/#docs-01) | MUST | pass | mirada-website (branch `docs-screenshots-whats-new`, not yet on main): `src/content/docs/` has getting started, a page per area, keyboard, settings and FAQ; eight pages show screenshots of the real window in light and dark (`scripts/capture-docs-shots.sh`, `src/lib/rehype-doc-figures.mjs`) |
| [DOCS-02](https://pauta-project.dev/4.0.1/documentation/#docs-02) | MUST | pass | `mirada-website/src/content/docs/*.md`: each with category and order; no copy here |
| [DOCS-05](https://pauta-project.dev/4.0.1/documentation/#docs-05) | MUST | pass | `src/main/java/com/mirada/ui/AboutWindow.java:79`: About opens `AppInfo.releaseNotesUrl()` (Mirada has no Welcome page); mirada-website (branch `docs-screenshots-whats-new`, not yet on main): `scripts/GenWhatsNew.java` generates `src/lib/whatsnew.ts`, the home block and `/whats-new`, from CHANGELOG.md |
| [DOCS-06](https://pauta-project.dev/4.0.1/documentation/#docs-06) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:193`: `DOCUMENTATION` opens mirada-project.dev/docs (ViewerWindow:766), in the palette, right after Command Palette in the Mac Help menu (MacMenus:78) and the header's menu (TitleBar:230), and as a link in the empty window (ViewerWindow:1347); MacMenusTest, CommandTest |
| [DOCS-07](https://pauta-project.dev/4.0.1/documentation/#docs-07) | SHOULD | pass | `src/main/java/com/mirada/ui/MacMenus.java:78`: Help: Command Palette, Documentation, Keyboard Shortcuts; About is in the application menu; Mirada has no updates, Message Log, Debug Log or Report a Problem rows |
| [DOCS-09](https://pauta-project.dev/4.0.1/documentation/#docs-09) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:212`: every command has a description, shown in the palette; CommandTest:189 non-blank |
| [DOCS-12](https://pauta-project.dev/4.0.1/documentation/#docs-12) | MUST | pass | `docs/README.md:1`: for contributors, user docs on the website, the code wins, every guide listed |
| [DOCS-13](https://pauta-project.dev/4.0.1/documentation/#docs-13) | SHOULD | pass | `docs/decisions/README.md:1`: six records (native image, libjpeg-turbo, codecs from source, .properties, no TestFX, then TestFX and the coverage gate), each with context, decision and consequences |
| [DOCS-14](https://pauta-project.dev/4.0.1/documentation/#docs-14) | MUST | pass | `AGENTS.md:84`: 101 lines, commands, worktrees and the checklist, with a documentation map into docs/; CONTRIBUTING.md:25 links into docs/; history in docs/reference/ |
| [DOCS-16](https://pauta-project.dev/4.0.1/documentation/#docs-16) | MUST | pass | `AGENTS.md:63`: docs change in the same pull request (CHANGELOG, docs/, website); CONTRIBUTING.md:33; docs/conventions.md:23 |
| [DOCS-17](https://pauta-project.dev/4.0.1/documentation/#docs-17) | MUST | pass | `AGENTS.md:61`: the code wins, and the docs are fixed in the same change (also docs/README.md) |
| [DOCS-18](https://pauta-project.dev/4.0.1/documentation/#docs-18) | SHOULD | pass | `src/test/java/com/mirada/ui/HeadlessDriver.java:116`: renders the real window to PNG (1×, not 2×) |
| [DOCS-19](https://pauta-project.dev/4.0.1/documentation/#docs-19) | MUST | pass | `README.md:32`: links into mirada-project.dev/docs (getting started, keyboard, Settings, FAQ, a page per area); no key table or feature catalog |
| [QUAL-01](https://pauta-project.dev/4.0.1/quality/#qual-01) | MUST | pass | `pom.xml:261`: NullAway an error; NullMarkedPackagesTest |
| [QUAL-02](https://pauta-project.dev/4.0.1/quality/#qual-02) | SHOULD | pass | no @SuppressWarnings("NullAway") in src/main |
| [QUAL-03](https://pauta-project.dev/4.0.1/quality/#qual-03) | SHOULD | pass | `pom.xml` jacoco-check: a PACKAGE LINE floor for each package of 300+ lines (edit 0.95, ui 0.93, settings 0.92, image 0.86, os 0.35), about three under measured |
| [QUAL-04](https://pauta-project.dev/4.0.1/quality/#qual-04) | SHOULD | pass | `docs/testing.md#the-coverage-gate` quotes the floors; `BuildHygieneTest.theCoverageFloorsQuotedInTheTestingGuideAreTheOnesTheBuildEnforces` compares them with the pom (seen failing with one floor changed in the table) |
| [QUAL-05](https://pauta-project.dev/4.0.1/quality/#qual-05) | MUST | pass | pure rules with plain tests: Zoom, FolderImages, NaturalOrder, Renaming, BookmarkList, Command.unavailable (CommandTest:229) |
| [QUAL-06](https://pauta-project.dev/4.0.1/quality/#qual-06) | MUST | pass | `src/test/java/com/mirada/BuildHygieneTest.java`: everyTestThatStartsTheToolkitIsTaggedFx holds @Tag("fx") on every test that boots the toolkit |
| [QUAL-07](https://pauta-project.dev/4.0.1/quality/#qual-07) | SHOULD | pass | Thread.sleep only in HeadlessDriver, not a test surefire runs |
| [QUAL-08](https://pauta-project.dev/4.0.1/quality/#qual-08) | SHOULD | pass | `src/test/java/com/mirada/ui/CommandSweepTest.java`: after every command in every state, Mirada's real settings folders and the desktop's trash are as they were; surefire points the trash at target/ and unsets the display and session bus |
| [QUAL-09](https://pauta-project.dev/4.0.1/quality/#qual-09) | MUST | pass | `scripts/headless-run.sh`: with HeadlessDriver: the real window, robot keys and clicks |
| [QUAL-11](https://pauta-project.dev/4.0.1/quality/#qual-11) | SHOULD | pass | `src/test/java/com/mirada/ui/CommandSweepTest.java`: every command of the registry run (or found unavailable, with its reason) in an empty window, on an image, turned and zoomed, in the editor and full screen; the context menu greys the same |
| [QUAL-12](https://pauta-project.dev/4.0.1/quality/#qual-12) | MUST | pass | `src/test/java/com/mirada/i18n/MessagesTest.java:103`: every language has exactly the base's keys, and `:129` every `command.<id>` has its `.desc`; `src/test/java/com/mirada/ui/CommandCatalogTest.java` holds every Command to both |
| [QUAL-13](https://pauta-project.dev/4.0.1/quality/#qual-13) | SHOULD | pass | `src/test/java/com/mirada/ui/SettingsWindowTest.java`: everyControlSetInTheWindowSurvivesARestart sets every control, reloads the settings and reads each back in a new window |
| [QUAL-15](https://pauta-project.dev/4.0.1/quality/#qual-15) | SHOULD | pass | `src/test/java/com/mirada/ReleaseSupplyChainTest.java:259`: fails when a signing, notarization or attestation step moves or goes, or a tag could publish without its secrets |
| [PERF-01](https://pauta-project.dev/4.0.1/performance/#perf-01) | MUST | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:108`: listing, sort stats, rename, existence checks, details, export, icon, keymap and codec reads on named virtual threads; settings, bookmarks and the session log written on `FileWrites` (`src/main/java/com/mirada/settings/FileWrites.java:22`); only App.start/stop join, as Editora's do |
| [PERF-02](https://pauta-project.dev/4.0.1/performance/#perf-02) | MUST | pass | `src/main/java/com/mirada/ui/LatestOnly.java:49`: every background result is delivered on Platform::runLater; trash, copy, rename, save and Bookmarks reads likewise |
| [PERF-03](https://pauta-project.dev/4.0.1/performance/#perf-03) | MUST | pass | `src/main/java/com/mirada/ui/LatestOnly.java:81`: listing, sorting, sidebar details, bookmark and recent-folder checks go through LatestOnly, generation checked again on the FX thread (LatestOnlyTest); Bookmarks.current guards its reads and writes (`src/main/java/com/mirada/settings/Bookmarks.java:172`) |
| [PERF-04](https://pauta-project.dev/4.0.1/performance/#perf-04) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:108`: every executor and thread is named and a daemon (window-files-, metadata-, decoder-, previews, config-files, settings-read, fx-sampler); virtual threads named too |
| [PERF-05](https://pauta-project.dev/4.0.1/performance/#perf-05) | MUST | pass | `src/main/java/com/mirada/image/ImageCache.java:153`: every supplyAsync/runAsync has an executor; no Process subclass |
| [PERF-06](https://pauta-project.dev/4.0.1/performance/#perf-06) | MUST | pass | `src/main/java/com/mirada/ui/BookmarksWindow.java:128`: text filters go through FxThrottle (Editora's; also MessageLogView), stats left out of the filter; layout passes are JavaFX's, one per pulse; the palette filters per key as Editora's does |
| [PERF-07](https://pauta-project.dev/4.0.1/performance/#perf-07) | MUST | n/a | nothing streams to the FX thread |
| [PERF-08](https://pauta-project.dev/4.0.1/performance/#perf-08) | MUST | pass | `src/main/java/com/mirada/ui/ThumbnailStrip.java:206`: a step compares the folder's list by identity, never per image (HotPathsTest); growing lists are ListViews |
| [PERF-09](https://pauta-project.dev/4.0.1/performance/#perf-09) | SHOULD | pass | `src/main/java/com/mirada/ui/ImageViewport.java:280`: layout allocates nothing |
| [PERF-10](https://pauta-project.dev/4.0.1/performance/#perf-10) | SHOULD | pass | no applyCss(); pseudo-classes for state |
| [PERF-11](https://pauta-project.dev/4.0.1/performance/#perf-11) | MUST | pass | filled past the cap, oldest dropped: `src/test/java/com/mirada/image/ImageCacheTest.java` (images and previews), `ThumbnailsTest.pastItsCapTheOldestThumbnailIsDroppedAndTheNewestKept`, `ui/TrashUndoTest.pastItsDepthTheOldestTrashedFileCanNoLongerBePutBack`, `DebugLogTest.pastItsCapTheSessionFileStartsOverWithANoteAndTheNewestLines`, `MessageLogTest`, `HistoryTest` |
| [PERF-12](https://pauta-project.dev/4.0.1/performance/#perf-12) | MUST | pass | `src/main/java/com/mirada/image/DecodeGuard.java:65`: every decoder weighs the header's size against named thresholds (Animation.MAX_PIXELS, the heap less MARGIN_BYTES, MAX_PIXELS) before decoding; PNG/GIF/BMP by ImageHeader before JavaFX (DecodeGuardTest) |
| [PERF-13](https://pauta-project.dev/4.0.1/performance/#perf-13) | MUST | pass | `src/test/java/com/mirada/ui/ClosedWindowTest.java:67`: three windows opened, used and closed leave no decoder, preview or thumbnail thread, no non-daemon thread, no timer playing, no tool window, and are collected (fixed: `ViewerWindow.release`) |
| [PERF-14](https://pauta-project.dev/4.0.1/performance/#perf-14) | MUST | pass | `src/test/java/com/mirada/ui/StartupDeferralTest.java:77`: no panel, tool window, floating controls or badge built before the first paint; those the settings open at start are |
| [PERF-15](https://pauta-project.dev/4.0.1/performance/#perf-15) | MUST | pass | `src/test/java/com/mirada/ui/StartupDeferralTest.java:77`: the update check, the bookmarks read, Open Recent's write, the window icon and the header's menu and tooltips start only after the first paint |
| [PERF-16](https://pauta-project.dev/4.0.1/performance/#perf-16) | SHOULD | pass | `src/main/java/com/mirada/App.java:84`: main starts the settings read on settings-read before launch; start takes it |
| [PERF-19](https://pauta-project.dev/4.0.1/performance/#perf-19) | SHOULD | pass | `scripts/measure-startup.sh`: median of n launches; Startup.mark phases |
| [PERF-20](https://pauta-project.dev/4.0.1/performance/#perf-20) | SHOULD | pass | `src/main/java/com/mirada/perf/FxSampler.java:21`: MIRADA_PERF_SAMPLE=1 samples the FX thread every 5 ms, prints stalls of 100 ms and a tally at exit |
| [PERF-21](https://pauta-project.dev/4.0.1/performance/#perf-21) | SHOULD | pass | `src/test/java/com/mirada/ui/HotPathsTest.java:50`: the strip's per-step comparison, headless, fails on the old code (5,000 reads a step) |
| [KBD-01](https://pauta-project.dev/4.0.1/keyboard/#kbd-01) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:20`: one enum is the registry (id, title, run through ViewerWindow.run); the editor's tools (Editor.java:224), the Bookmarks window's row actions (BookmarksWindow.java:299) and About's Copy details (`help.copyDetails`) are commands, run by their buttons, keys, menus and the palette |
| [KBD-02](https://pauta-project.dev/4.0.1/keyboard/#kbd-02) | SHOULD | pass | `src/main/java/com/mirada/ui/Command.java:393`: every id is `<area>.<camelCase>` (`go.next`, `editor.save`, `help.copyDetails`); CommandTest.everyIdIsDottedAndGroupedByArea |
| [KBD-03](https://pauta-project.dev/4.0.1/keyboard/#kbd-03) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:222`: a title is `tr("command.<id>")` and `:245` its line `command.<id>.desc` (with `commandOn.<id>.mac`/`.windows` where a platform names a thing its own way); `CommandCatalogTest` fails on a command without either |
| [KBD-05](https://pauta-project.dev/4.0.1/keyboard/#kbd-05) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:198`: C-S-p and M-x both open the palette (Cmd-S-p on macOS) |
| [KBD-06](https://pauta-project.dev/4.0.1/keyboard/#kbd-06) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:335`: fuzzy over title and keywords; rows show chords |
| [KBD-07](https://pauta-project.dev/4.0.1/keyboard/#kbd-07) | MUST | pass | `src/main/java/com/mirada/ui/CommandPalette.java:238`: in-scene overlay, no Popup |
| [KBD-08](https://pauta-project.dev/4.0.1/keyboard/#kbd-08) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:1150`: one picker for every list choice |
| [KBD-09](https://pauta-project.dev/4.0.1/keyboard/#kbd-09) | MUST | pass | `src/main/resources/com/mirada/keymaps/`: emacs.json, cua.json and cua.mac.json (Cmd for Ctrl); CommandTest.theEmacsAndCuaKeymapsShipWithAMacCua |
| [KBD-11](https://pauta-project.dev/4.0.1/keyboard/#kbd-11) | MUST | pass | `src/main/java/com/mirada/ui/Keybindings.java:55`: `layered` applies base keymap (and its strip-at-the-side bindings) → user overrides (`keys.<id>`, `palette.keys`); no plugins; CommandTest.theUsersKeysGoOnTopAndWin |
| [KBD-14](https://pauta-project.dev/4.0.1/keyboard/#kbd-14) | SHOULD | n/a | no multi-key prefixes |
| [KBD-17](https://pauta-project.dev/4.0.1/keyboard/#kbd-17) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:173`: `Icons.button` and `Icons.name` (:202) set tooltip and accessible text together; TitleBar, OnScreenControls, MarksBadge and InfoPanel use them (MenusFxTest.everyIconButtonIsNamedAndWearsItsCommandsGlyph) |
| [WIN-01](https://pauta-project.dev/4.0.1/window-shell/#win-01) | MUST | pass | `src/main/java/com/mirada/ui/StatusBar.java:97`: the echo label takes `status-echo-error` or `status-echo-warn` by severity; its click runs `Command.MESSAGE_LOG` (:69); Status Bar toggles it |
| [WIN-02](https://pauta-project.dev/4.0.1/window-shell/#win-02) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:48`: light, dark and follow-the-system, live |
| [WIN-03](https://pauta-project.dev/4.0.1/window-shell/#win-03) | SHOULD | pass | `src/main/java/com/mirada/ui/TitleBar.java:193`: HeaderBar with StageStyle.EXTENDED; system menu bar on macOS (MacMenus.java:141) |
| [WIN-05](https://pauta-project.dev/4.0.1/window-shell/#win-05) | MUST | pass | `src/main/java/com/mirada/ui/Confirm.java:57`: one helper: question names the thing, button the action, Escape cancels; Move to Trash records an undo (Linux) or goes to the system trash |
| [WIN-07](https://pauta-project.dev/4.0.1/window-shell/#win-07) | MUST | pass | `src/main/java/com/mirada/ui/AboutWindow.java:155`: version, build, edition from AppInfo (:92) and the update checker's last answer (`UpdateCoordinator.summary`) |
| [WIN-10](https://pauta-project.dev/4.0.1/window-shell/#win-10) | MAY | n/a | no modes |
| [ICON-01](https://pauta-project.dev/4.0.1/icons-and-color/#icon-01) | MUST | pass | `src/main/java/com/mirada/ui/Fonts.java:94`: a `Window.getWindows()` listener puts `ui-font.css` on every scene and on a scene a window is given later; no font-family in mirada.css (StyleContrastTest.theUiFontIsTheSceneSheetsNotTheAppSheets) |
| [ICON-02](https://pauta-project.dev/4.0.1/icons-and-color/#icon-02) | MUST | pass | `src/main/resources/com/mirada/fonts/inter/`: the four faces and OFL.txt; `Font.loadFont` from `Fonts.load` (`src/main/java/com/mirada/App.java:117`), awaited before the window is shown (:135) |
| [ICON-04](https://pauta-project.dev/4.0.1/icons-and-color/#icon-04) | SHOULD | n/a | ships no theme file; AtlantaFX Cupertino unchanged (src/main/java/com/mirada/ui/Theme.java:83) |
| [ICON-06](https://pauta-project.dev/4.0.1/icons-and-color/#icon-06) | SHOULD | pass | `src/main/resources/com/mirada/styles/mirada.css:31`: -state-key is Editora's ladder; StyleContrastTest.theKeyboardPeriwinkleReadsInBothThemes resolves it on both themes' grounds |
| [ICON-07](https://pauta-project.dev/4.0.1/icons-and-color/#icon-07) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:31`: -state-key only in the `.root` block, a ladder() of -color-bg-default |
| [ICON-08](https://pauta-project.dev/4.0.1/icons-and-color/#icon-08) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:9`: no hex, rgb(a) or named colour outside the `.root` block; StyleContrastTest fails one |
| [ICON-10](https://pauta-project.dev/4.0.1/icons-and-color/#icon-10) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:18`: the four roles' `-fg` overridden as ladders; StyleContrastTest.tintedTextReachesAaOnItsOwnFillAndOnBothGrounds computes each on its subtle fill and both grounds, light and dark; Accent.java does the same for the system accent (AccentTest) |
| [ICON-11](https://pauta-project.dev/4.0.1/icons-and-color/#icon-11) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:19`: the emphasis fills darkened until `-color-fg-emphasis` reads at 4.5:1; StyleContrastTest.inkOnAnEmphasisFillIsTheThemesOnEmphasisColour, and the rules on emphasis fills use it |
| [ICON-12](https://pauta-project.dev/4.0.1/icons-and-color/#icon-12) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:34`: `-focus-ring: -color-accent-emphasis`, drawn by the `:focus-visible` block at the end (:791); StyleContrastTest.theFocusRingIsAtLeastThreeToOneOnEverySurface |
| [ICON-13](https://pauta-project.dev/4.0.1/icons-and-color/#icon-13) | SHOULD | pass | `src/main/resources/com/mirada/styles/mirada.css:31`: periwinkle and the role overrides are ladders of the ground; StyleContrastTest resolves each in both themes |
| [ICON-14](https://pauta-project.dev/4.0.1/icons-and-color/#icon-14) | SHOULD | pass | `src/test/java/com/mirada/ui/StyleContrastTest.java`: reads Cupertino's palette and mirada.css, resolves ladders, WCAG arithmetic, no toolkit |
| [ICON-15](https://pauta-project.dev/4.0.1/icons-and-color/#icon-15) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:84`: Application.setUserAgentStylesheet, live |
| [ICON-16](https://pauta-project.dev/4.0.1/icons-and-color/#icon-16) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:54`: colorScheme listener re-applies; WindowsTest |
| [ICON-17](https://pauta-project.dev/4.0.1/icons-and-color/#icon-17) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:227`: unknown theme falls back to SYSTEM; SettingsTest:138 |
| [ICON-18](https://pauta-project.dev/4.0.1/icons-and-color/#icon-18) | SHOULD | pass | no semantic colours used as decoration |
| [ICON-19](https://pauta-project.dev/4.0.1/icons-and-color/#icon-19) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:125`: one glyph family ("icon") |
| [ICON-20](https://pauta-project.dev/4.0.1/icons-and-color/#icon-20) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:173`: a button takes its command's glyph from `forCommand`, as the menus do; the zoom button Actual Size's or Zoom to Fit's, the note buttons Note's (MenusFxTest.everyIconButtonIsNamedAndWearsItsCommandsGlyph) |
| [ICON-21](https://pauta-project.dev/4.0.1/icons-and-color/#icon-21) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:173`: every icon-only button built by `Icons.button` or named by `Icons.name` (MenusFxTest) |
| [ICON-23](https://pauta-project.dev/4.0.1/icons-and-color/#icon-23) | SHOULD | n/a | draws no file-type glyphs (thumbnails) |
| [ICON-24](https://pauta-project.dev/4.0.1/icons-and-color/#icon-24) | MUST | pass | `NOTICE`: Inter (OFL-1.1) and AtlantaFX credited; the icons are hand-drawn (src/main/java/com/mirada/ui/Icons.java:25) |
| [TBAR-01](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-01) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:202`: `Icons.name` sets the tooltip and the accessible text from one title (MenusFxTest) |
| [TBAR-02](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-02) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:173`: `Icons.button` takes buttons out of the Tab order; the header's toggle and menu too (TitleBar.java:193) |
| [TBAR-03](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-03) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:212`: "title (chord)" from the command's title and `Command.chord()`, rebuilt on a palette-key or strip-axis change (`ViewerWindow.relabelButtons`, `src/main/java/com/mirada/ui/ViewerWindow.java:790`); the editor's from KeyCombinations (Editor.java:433); IconsTest, MenusFxTest |
| [TBAR-04](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-04) | SHOULD | pass | no separators; groups by spacing (src/main/java/com/mirada/ui/TitleBar.java:186) |
| [TBAR-05](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-05) | SHOULD | n/a | no group is ever hidden |
| [TBAR-07](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-07) | SHOULD | pass | `src/main/java/com/mirada/ui/TitleBar.java:177`: info toggle selected while the sidebar shows |
| [TBAR-08](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-08) | MUST | pass | `src/main/java/com/mirada/ui/TitleBar.java:256`: Previous and Next in the header's menu, beside the palette and the Mac's Go menu (MenusFxTest.previousAndNextAreInTheHeadersMenu) |
| [TBAR-09](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-09) | MAY | n/a | header not customizable |
| [TBAR-10](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-10) | MUST | n/a | the header bar has no right-click menu |
| [TBAR-12](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-12) | SHOULD | n/a | no tool-window stripes |
| [TBAR-13](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-13) | SHOULD | n/a | no stripes |
| [TABS-01](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-01) | MUST | n/a | no document tabs |
| [TABS-02](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-02) | MUST | n/a | no document tabs |
| [TABS-03](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-03) | MUST | n/a | no document tabs |
| [TABS-04](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-04) | MUST | n/a | no document tabs |
| [TABS-05](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-05) | MUST | n/a | no document tabs |
| [TABS-06](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-06) | MUST | n/a | no document tabs |
| [TABS-07](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-07) | SHOULD | n/a | no document tabs |
| [TABS-08](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-08) | SHOULD | n/a | no document tabs |
| [TABS-09](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-09) | SHOULD | n/a | no document tabs (Go to Image plays the switcher's part) |
| [TABS-11](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-11) | SHOULD | n/a | no tab strip |
| [TABS-12](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-12) | SHOULD | n/a | no tab bar |
| [TABS-13](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-13) | MUST | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:742`: INFO, THUMBNAILS, BOOKMARKS are commands; header toggle runs the same |
| [TABS-14](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-14) | MUST | pass | `src/test/java/com/mirada/ui/StartupDeferralTest.java:77`: no closed panel (sidebar, strip, Bookmarks, Settings, About, the editor) built before the first paint |
| [TABS-16](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-16) | SHOULD | n/a | no dockable tool windows |
| [TABS-17](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-17) | SHOULD | n/a | no stripe buttons |
| [TABS-18](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-18) | SHOULD | n/a | no floating panels |
| [TABS-19](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-19) | SHOULD | pass | `src/main/java/com/mirada/ui/InfoPanel.java:65`: panels never take focus |
| [TABS-20](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-20) | SHOULD | n/a | no stripe buttons |
| [TABS-21](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-21) | SHOULD | n/a | no bottom tool windows |
| [TABS-22](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-22) | SHOULD | n/a | no stripe buttons |
| [MENU-14](https://pauta-project.dev/4.0.1/menus/#menu-14) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:132`: own glyphs for Bookmarks and Notes, About, Remove (a mark); Hide Thumbnails wears Thumbnails'; MenusFxTest.everyRowOfEveryMenuHasAGlyph opens the header, zoom, image and bookmark-row menus |
| [MENU-15](https://pauta-project.dev/4.0.1/menus/#menu-15) | MUST | pass | `src/main/java/com/mirada/ui/MacMenus.java:109`: the Mac bar writes every bound chord into the title; in-window rows show it in `-state-key` at Inter 11px (MenuChord, ui-font.css); MacMenusTest.theBarHasNoAcceleratorsAndShowsEveryChord |
| [MENU-16](https://pauta-project.dev/4.0.1/menus/#menu-16) | MUST | pass | `src/main/resources/com/mirada/styles/ui-font.css:19`: Inter 14px pinned on `.context-menu` and its rows, keys 11px; MenusFxTest.menusAreInterFourteenWhateverTheyOpenFrom reads it from the header's, the image's and a bookmark row's menus |
| [MENU-01](https://pauta-project.dev/4.0.1/menus/#menu-01) | MUST | pass | `src/main/java/com/mirada/ui/MacMenus.java:38`: menus are lists of Command; titles, gates from Command |
| [MENU-02](https://pauta-project.dev/4.0.1/menus/#menu-02) | MUST | pass | `src/main/java/com/mirada/ui/MacMenus.java:109`: no `setAccelerator` on the Mac bar, the chord in the title, no icons; in-window rows show `MenuChord`, which never matches (MacMenusTest) |
| [MENU-04](https://pauta-project.dev/4.0.1/menus/#menu-04) | SHOULD | n/a | no Simple mode |
| [MENU-06](https://pauta-project.dev/4.0.1/menus/#menu-06) | MUST | pass | `src/main/java/com/mirada/ui/Popups.java:85`: `Popups.show` closes every open menu before the next; MenusFxTest.oneRightClickMenuAtATimeAndThePressThatClosesItStillLands |
| [MENU-07](https://pauta-project.dev/4.0.1/menus/#menu-07) | MUST | pass | `src/main/java/com/mirada/ui/Popups.java:52`: a press in the window closes the menus and is not consumed; the menu buttons keep auto-hide; MenusFxTest |
| [MENU-09](https://pauta-project.dev/4.0.1/menus/#menu-09) | MUST | pass | `src/main/java/com/mirada/ui/Popups.java:93`: the Menu key and Shift+F10 open the image's menu (ViewerWindow.java:777) and the selected bookmark row's, scrolled into view and anchored under it (BookmarksWindow.java:236); MenusFxTest, PopupsTest |
| [MENU-11](https://pauta-project.dev/4.0.1/menus/#menu-11) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:1380`: every right-click menu built for its showing |
| [MENU-13](https://pauta-project.dev/4.0.1/menus/#menu-13) | MAY | n/a | no terminal |
| [TRAY-01](https://pauta-project.dev/4.0.1/tray-and-background/#tray-01) | MUST | n/a | no tray: no SystemTray, TrayIcon or StatusNotifierItem in src/main |
| [TRAY-02](https://pauta-project.dev/4.0.1/tray-and-background/#tray-02) | MUST | n/a | no tray: no SystemTray, TrayIcon or StatusNotifierItem in src/main |
| [TRAY-03](https://pauta-project.dev/4.0.1/tray-and-background/#tray-03) | MUST | n/a | no tray; a close quits (src/main/java/com/mirada/ui/ViewerWindow.java:738) |
| [TRAY-04](https://pauta-project.dev/4.0.1/tray-and-background/#tray-04) | MUST | n/a | no tray Quit; the place is saved on close and from App.stop (src/main/java/com/mirada/App.java:47) |
| [TRAY-05](https://pauta-project.dev/4.0.1/tray-and-background/#tray-05) | SHOULD | n/a | no tray, no --minimized |
| [TRAY-06](https://pauta-project.dev/4.0.1/tray-and-background/#tray-06) | SHOULD | n/a | no start at login |
| [TRAY-07](https://pauta-project.dev/4.0.1/tray-and-background/#tray-07) | SHOULD | n/a | no start at login |
| [TRAY-08](https://pauta-project.dev/4.0.1/tray-and-background/#tray-08) | SHOULD | n/a | no start at login |
| [TRAY-09](https://pauta-project.dev/4.0.1/tray-and-background/#tray-09) | MUST | n/a | sends no desktop notifications |
| [TRAY-10](https://pauta-project.dev/4.0.1/tray-and-background/#tray-10) | MUST | n/a | sends no desktop notifications |
| [TRAY-11](https://pauta-project.dev/4.0.1/tray-and-background/#tray-11) | SHOULD | n/a | sends no desktop notifications |
| [TRAY-12](https://pauta-project.dev/4.0.1/tray-and-background/#tray-12) | MAY | n/a | no dock badge |
| [TRAY-13](https://pauta-project.dev/4.0.1/tray-and-background/#tray-13) | SHOULD | n/a | no tray to hide behind |
| [TRAY-14](https://pauta-project.dev/4.0.1/tray-and-background/#tray-14) | SHOULD | pass | `src/main/java/com/mirada/App.java:30`: headless decided in main (Trash.headless), headful only on macOS for Move to Trash |
| [SET-01](https://pauta-project.dev/4.0.1/settings/#set-01) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:173`: SETTINGS (`app.settings`) is a palette command; `src/main/resources/com/mirada/keymaps/cua.json:38` binds C-, and `cua.mac.json:38` Cmd-, |
| [SET-03](https://pauta-project.dev/4.0.1/settings/#set-03) | MUST | pass | `src/main/java/com/mirada/ui/SettingsSearch.java:27`: title, description, visible text, card and page, keywords; `src/test/java/com/mirada/ui/SettingsPagesTest.java:29` ranks a title hit above a keyword hit, `:37` a whole word above part of one; `SettingsWindow.java:308` hides rows and cards and opens the best page (SettingsWindowTest.java:261) |
| [SET-04](https://pauta-project.dev/4.0.1/settings/#set-04) | MUST | pass | `src/main/java/com/mirada/ui/SettingsWindow.java:183`: every control writes through and follows the settings; no Save or Apply, no text field (the search is not a setting) |
| [SET-05](https://pauta-project.dev/4.0.1/settings/#set-05) | SHOULD | pass | `src/main/java/com/mirada/settings/Settings.java:655`: another process's change is read as a window comes forward (`ViewerWindow.java:279`) and merged before each write; `src/test/java/com/mirada/ui/SettingsCommandsTest.java:237` changes one window and finds it applied in the other; CrossProcessSettingsTest:63 |
| [SET-07](https://pauta-project.dev/4.0.1/settings/#set-07) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:114`: one resolver: --config-dir/--dev, then MIRADA_CONFIG_DIR, then the default (~/.mirada; the platform folder on macOS and Windows) |
| [SET-09](https://pauta-project.dev/4.0.1/settings/#set-09) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:74` and `Bookmarks.java:75` declare SCHEMA_VERSION; `Migrations.java:40` one step per version, the identity for the additive v1 to v2; MigrationsTest.java:29 checks the chain |
| [SET-10](https://pauta-project.dev/4.0.1/settings/#set-10) | MUST | pass | `src/main/java/com/mirada/settings/ConfigFile.java:119`: a newer file is copied to <name>.v<n>.bak and the defaults load; `src/test/java/com/mirada/settings/MigrationsTest.java:82` (`tooNewSettingsAreBackedUpAndDefaultsLoaded`), `:99` for bookmarks |
| [SET-11](https://pauta-project.dev/4.0.1/settings/#set-11) | MUST | pass | `src/main/java/com/mirada/settings/ConfigFile.java:98`: clamped on read; an undecodable file kept as .corrupt.bak, an unreadable one never written over; ConfigFileTest.java:23 (malformed \u escape), SettingsTest (out of range) |
| [SET-12](https://pauta-project.dev/4.0.1/settings/#set-12) | MUST | pass | `src/main/java/com/mirada/settings/ConfigExport.java:28` mirada-config-<stamp>.zip of the folder, `ConfigImport.java:192` replaces only the ticked parts; ConfigExportImportTest.java:44 (name and contents), `:100` (ticked parts), SettingsCommandsTest.java:129 (the commands) |
| [SET-13](https://pauta-project.dev/4.0.1/settings/#set-13) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java`: no secrets anywhere |
| [SET-14](https://pauta-project.dev/4.0.1/settings/#set-14) | MUST | n/a | asks for no credential |
| [SET-15](https://pauta-project.dev/4.0.1/settings/#set-15) | MUST | n/a | no repository URL; the report's redaction is DIAG-06 |
| [SET-16](https://pauta-project.dev/4.0.1/settings/#set-16) | MUST | pass | no option weakens security |
| [SET-18](https://pauta-project.dev/4.0.1/settings/#set-18) | SHOULD | n/a | no Sync (SET-17) |
| [SET-19](https://pauta-project.dev/4.0.1/settings/#set-19) | SHOULD | n/a | no Sync (SET-17) |
| [SET-20](https://pauta-project.dev/4.0.1/settings/#set-20) | SHOULD | n/a | no Sync (SET-17) |
| [SET-21](https://pauta-project.dev/4.0.1/settings/#set-21) | SHOULD | n/a | no Sync (SET-17) |
| [DIAG-01](https://pauta-project.dev/4.0.1/diagnostics/#diag-01) | MUST | pass | `src/test/java/com/mirada/diagnostics/MessageLogTest.java:17`: newest first, evicts past 200, clears; HH:mm:ss in `MessageLogView`; opened from the echo area, Esc closes (`DiagnosticsUiTest.java:76`) |
| [DIAG-02](https://pauta-project.dev/4.0.1/diagnostics/#diag-02) | MUST | pass | `src/test/java/com/mirada/diagnostics/DebugLogTest.java:42`: bounded buffer; session file written with mirada-session.prev.log kept (:102); root handler at `DebugLog.java:114` |
| [DIAG-04](https://pauta-project.dev/4.0.1/diagnostics/#diag-04) | MUST | pass | `src/main/java/com/mirada/App.java:44`: `DebugLog.install()` is main's first statement; the uncaught handler logs to the Debug Log and says it in red (:97), no dialog |
| [DIAG-05](https://pauta-project.dev/4.0.1/diagnostics/#diag-05) | MUST | pass | `src/test/java/com/mirada/update/UpdateCoordinatorTest.java:79`: after the delay, once a run, not off or not due; scheduled after the first paint (`ViewerWindow.java:356`); Check for Updates (:872), Settings switch |
| [DIAG-06](https://pauta-project.dev/4.0.1/diagnostics/#diag-06) | MUST | pass | `src/test/java/com/mirada/report/RedactorTest.java:15`: home, user, e-mail, URL credentials, tokens; `ProblemReportTest.java:44`: short in the link, long by paste; `ReportProblemWindow` |
| [DIAG-08](https://pauta-project.dev/4.0.1/diagnostics/#diag-08) | SHOULD | n/a | no user documents |
| [CLI-01](https://pauta-project.dev/4.0.1/command-line/#cli-01) | MUST | pass | `src/main/java/com/mirada/App.java:63`: `printed` writes fixed English (Launch.USAGE, AppInfo.versionText) and main exits before `launch`; AppTest.versionAndHelpPrintWithoutAWindow |
| [CLI-02](https://pauta-project.dev/4.0.1/command-line/#cli-02) | MUST | pass | `src/main/java/com/mirada/Launch.java:78`: --config-dir and --dev parsed first; `App.java:84` resolves the settings file from them before anything reads it; LaunchTest |
| [CLI-03](https://pauta-project.dev/4.0.1/command-line/#cli-03) | SHOULD | pass | `src/main/java/com/mirada/Launch.java:77`: --new-instance sets `newInstance`, and `handsOff()` false skips the hand-off (`App.java:90`); LaunchTest.aLaunchHandsOffToTheRunningMiradaUnlessToldToStartItsOwn |
| [CLI-05](https://pauta-project.dev/4.0.1/command-line/#cli-05) | MUST | pass | `src/test/java/com/mirada/report/ReportCommandTest.java:28`: `--report` writes the redacted problem-report.txt and opens the issue, no GUI (`App.java:66`) |
| [CLI-07](https://pauta-project.dev/4.0.1/command-line/#cli-07) | MUST | pass | `src/test/java/com/mirada/os/SingleInstanceTest.java:24`: the first claim is primary and listens, a second launch's files reach it; `AppTest.java:105` shows the handed file in the window; Apple Events via `MacOpenFiles` |
| [CLI-08](https://pauta-project.dev/4.0.1/command-line/#cli-08) | SHOULD | pass | `src/test/java/com/mirada/os/InstanceLockTest.java:63`: the lock taken from this JVM and a second process (InstanceLockProbe): primary, secondary, presence seen and gone |
| [CLI-09](https://pauta-project.dev/4.0.1/command-line/#cli-09) | SHOULD | pass | `src/main/java/com/mirada/App.java:56`: java.awt.headless set as main's first statement (headless but on macOS, where Trash is AWT's Desktop) |
| [CLI-10](https://pauta-project.dev/4.0.1/command-line/#cli-10) | SHOULD | pass | `src/main/java/com/mirada/os/ToolPath.java:50`: Reveal, Trash's gio and Browser start through `ToolPath.process`: own PATH, then the login shell's, program resolved on it; ToolPathTest |
| [INTL-01](https://pauta-project.dev/4.0.1/i18n/#intl-01) | MUST | pass | `src/main/resources/com/mirada/i18n/`: `messages.properties` (English, the base) and `messages_es`, `_pt`, `_fr`, `_it`, `_de`, 618 keys each |
| [INTL-02](https://pauta-project.dev/4.0.1/i18n/#intl-02) | SHOULD | pass | `src/main/java/com/mirada/i18n/Messages.java:35`: the six languages, which Settings › General › Language and **Language…** in the palette list (`SettingsPages.java:91`); `MessagesTest.LANGUAGES` walks the same five |
| [INTL-05](https://pauta-project.dev/4.0.1/i18n/#intl-05) | SHOULD | n/a | no CJK catalog |
| [INTL-06](https://pauta-project.dev/4.0.1/i18n/#intl-06) | SHOULD | n/a | no right-to-left catalog |
| [INTL-07](https://pauta-project.dev/4.0.1/i18n/#intl-07) | SHOULD | pass | every string a control shows is `tr("…")` in `com.mirada.i18n.Messages`; `src/test/java/com/mirada/i18n/MessagesTest.java:240` fails on a literal key the base lacks. English on purpose: the `--help`/`--version` text, About's copied environment block and the problem report's body (for maintainers), format and product names |
| [INTL-08](https://pauta-project.dev/4.0.1/i18n/#intl-08) | MUST | pass | `src/test/java/com/mirada/i18n/MessagesTest.java:103`: missing or extra keys fail, `:119` an empty value; `everyLineIsACommentAKeyOrAContinuation` catches conflict markers |
| [INTL-09](https://pauta-project.dev/4.0.1/i18n/#intl-09) | SHOULD | pass | `src/test/java/com/mirada/i18n/MessagesTest.java:154`: a lone apostrophe beside a placeholder fails, and `aValueWithoutAPlaceholderNeverDoublesAnApostrophe` a doubled one without |
| [INTL-10](https://pauta-project.dev/4.0.1/i18n/#intl-10) | SHOULD | pass | `src/test/java/com/mirada/i18n/MessagesTest.java:200`: every `{n,number,#}` of the base (zoom percent, image size, HTTP status, error codes) formats ungrouped in each language |
| [INTL-11](https://pauta-project.dev/4.0.1/i18n/#intl-11) | SHOULD | pass | `src/main/java/com/mirada/Launch.java:32`: the usage text is a literal; `AppInfo.versionText()` and `--report`'s output stay English, outside the catalogs |
| [PAL-01](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-01) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:198`: palette keys C-S-p M-x f1 (Cmd-S-p on macOS); one CommandPalette per window (ViewerWindow.java:267) |
| [PAL-02](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-02) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:317`: the palette lists every command, greyed with a reason where it cannot run, titled by `command.<id>` and described by `command.<id>.desc` (`:222`, `:245`); `MessagesTest.everyCommandTitleHasItsDescription` |
| [PAL-03](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-03) | MUST | pass | `src/main/java/com/mirada/ui/CommandPalette.java:238`: card and scrim in the root StackPane; no Popup |
| [PAL-04](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-04) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:236`: opens empty, listing every command; from chord, header menu, context menu, Mac Help menu |
| [PAL-06](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-06) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:273`: hides, then runs; disabled rows do nothing |
| [PAL-09](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-09) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:251`: a keymap or key change in the settings calls `refreshKeys` on both palettes (ViewerWindow.followKeys), which re-reads the opener chip and every row's chord; the legend is the palette's own keys |
| [QOPEN-01](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-01) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:1150`: Go to Image, Open Recent, Go to Bookmark all CommandPalette.Choices |
| [QOPEN-03](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-03) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:220`: items read at call time and snapshotted |
| [QOPEN-04](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-04) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:307`: supplier order kept; current pre-selected (:294) |
| [QOPEN-05](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-05) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:319`: ties keep the supplier's index |
| [QOPEN-06](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-06) | SHOULD | pass | `src/main/java/com/mirada/ui/PaletteKeys.java:60`: arrows, pages, Ctrl/Cmd+Home/End; hides first |
| [QOPEN-07](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-07) | SHOULD | pass | `src/main/java/com/mirada/ui/PaletteKeys.java:54`: Esc, C-g, scrim cancel; no previews to undo (focus: PAL-07) |
| [QOPEN-08](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-08) | MAY | n/a | no previews |
| [QOPEN-09](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-09) | MAY | n/a | no row actions |
| [QOPEN-10](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-10) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:971`: empty pickers return with a message; placeholder sentence (CommandPalette.java:235) |
| [WELC-01](https://pauta-project.dev/4.0.1/templates/welcome/#welc-01) | SHOULD | n/a | the window is its content (WELC-02) |
| [WELC-03](https://pauta-project.dev/4.0.1/templates/welcome/#welc-03) | SHOULD | n/a | no Welcome page |
| [WELC-04](https://pauta-project.dev/4.0.1/templates/welcome/#welc-04) | SHOULD | n/a | no tabs |
| [WELC-05](https://pauta-project.dev/4.0.1/templates/welcome/#welc-05) | SHOULD | n/a | no Welcome page |
| [WELC-06](https://pauta-project.dev/4.0.1/templates/welcome/#welc-06) | SHOULD | n/a | no Welcome page (Open Recent drops gone folders, ViewerWindow.java:1162) |
| [WELC-07](https://pauta-project.dev/4.0.1/templates/welcome/#welc-07) | SHOULD | n/a | no remove-from-recent |
| [WELC-08](https://pauta-project.dev/4.0.1/templates/welcome/#welc-08) | SHOULD | n/a | no numbered entries |
| [WELC-09](https://pauta-project.dev/4.0.1/templates/welcome/#welc-09) | SHOULD | n/a | no SFTP |
| [WELC-10](https://pauta-project.dev/4.0.1/templates/welcome/#welc-10) | SHOULD | n/a | no Customize section |
| [WELC-11](https://pauta-project.dev/4.0.1/templates/welcome/#welc-11) | SHOULD | n/a | no Welcome tab |
| [ABOUT-01](https://pauta-project.dev/4.0.1/templates/about/#about-01) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:173`: ABOUT in the header menu and the macOS application menu |
| [ABOUT-02](https://pauta-project.dev/4.0.1/templates/about/#about-02) | MUST | pass | `src/main/java/com/mirada/ui/AboutWindow.java:92`: `AppInfo.environment`, the --version text, and the update checker's answer (:155) |
| [ABOUT-03](https://pauta-project.dev/4.0.1/templates/about/#about-03) | SHOULD | pass | `src/main/java/com/mirada/ui/AboutWindow.java:90`: Copy details in the content, relabelled Copied |
| [ABOUT-04](https://pauta-project.dev/4.0.1/templates/about/#about-04) | SHOULD | pass | `src/main/java/com/mirada/update/UpdateCoordinator.java:178`: "not checked" with no answer or the check off (`UpdateCoordinatorTest.java:160`) |
| [ABOUT-05](https://pauta-project.dev/4.0.1/templates/about/#about-05) | MUST | pass | `src/main/java/com/mirada/ui/AboutWindow.java:75`: "Built to Pauta 4.0.1" link; "Pauta: 4.0.1" right after the commit (AppInfo.versionText) |
| [ABOUT-06](https://pauta-project.dev/4.0.1/templates/about/#about-06) | MUST | pass | `src/main/java/com/mirada/AppInfo.java:60`: pautaUrl() is the numbered page, never latest; EditionTest |
| [ABOUT-08](https://pauta-project.dev/4.0.1/templates/about/#about-08) | MUST | pass | `pom.xml:26`: pauta.version filtered into build-info.properties, read by AppInfo; README and AGENTS.md checked by EditionTest |
| [CONF-01](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-01) | MUST | pass | `src/main/java/com/mirada/ui/Editor.java:224`: discarding an edit (Cancel, Escape, Close Window, the close button) and replacing a file asks through Confirm; trash records an undo |
| [CONF-02](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-02) | MUST | pass | `src/main/java/com/mirada/ui/BookmarksWindow.java:322`: "Remove the bookmark and note?" names the thing |
| [CONF-03](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-03) | MUST | pass | `src/main/java/com/mirada/ui/Confirm.java:63`: the action button is the verb (Remove, Discard, Replace) in the danger style; ConfirmTest |
| [CONF-04](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-04) | MUST | pass | `src/main/java/com/mirada/ui/BookmarksWindow.java:324`: CANCEL_CLOSE, orElse(CANCEL) |
| [CONF-06](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-06) | SHOULD | n/a | no running programs |
| [CONF-08](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-08) | SHOULD | pass | `src/main/java/com/mirada/ui/Confirm.java:73`: asks before anything changes; true only for the action button |
| [CONF-09](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-09) | SHOULD | n/a | no bulk destructive commands |
| [CONF-10](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-10) | SHOULD | pass | `src/main/java/com/mirada/ui/Confirm.java:29`: only removing a note says "This cannot be undone."; ConfirmTest |
| [CONF-11](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-11) | SHOULD | n/a | no force alternatives |
| [FIND-01](https://pauta-project.dev/4.0.1/templates/find-bar/#find-01) | MUST | n/a | no text to search; lists filter (FIND-03) |
| [FIND-02](https://pauta-project.dev/4.0.1/templates/find-bar/#find-02) | MUST | n/a | no find bar |
| [FIND-03](https://pauta-project.dev/4.0.1/templates/find-bar/#find-03) | MUST | pass | `src/main/java/com/mirada/ui/BookmarksWindow.java:101`: the Bookmarks filter filters as typed; Escape empties, then closes |
| [FIND-04](https://pauta-project.dev/4.0.1/templates/find-bar/#find-04) | SHOULD | n/a | no find bar |
| [FIND-05](https://pauta-project.dev/4.0.1/templates/find-bar/#find-05) | SHOULD | n/a | no find bar |
| [FIND-06](https://pauta-project.dev/4.0.1/templates/find-bar/#find-06) | MUST | n/a | no find bar |
| [FIND-07](https://pauta-project.dev/4.0.1/templates/find-bar/#find-07) | MUST | n/a | no find bar |
| [FIND-08](https://pauta-project.dev/4.0.1/templates/find-bar/#find-08) | SHOULD | n/a | no terminal |
| [FIND-09](https://pauta-project.dev/4.0.1/templates/find-bar/#find-09) | SHOULD | n/a | no find field |
| [FIND-10](https://pauta-project.dev/4.0.1/templates/find-bar/#find-10) | SHOULD | n/a | no find |
| [FIND-11](https://pauta-project.dev/4.0.1/templates/find-bar/#find-11) | SHOULD | n/a | no replace |
| [FIND-12](https://pauta-project.dev/4.0.1/templates/find-bar/#find-12) | SHOULD | n/a | no replace |
| [FIND-13](https://pauta-project.dev/4.0.1/templates/find-bar/#find-13) | SHOULD | n/a | not a list app |
| [FIND-14](https://pauta-project.dev/4.0.1/templates/find-bar/#find-14) | SHOULD | pass | `src/main/java/com/mirada/ui/BookmarksWindow.java:224`: Escape clears, then closes |
| [FIND-15](https://pauta-project.dev/4.0.1/templates/find-bar/#find-15) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:696`: chrome out of the Tab order; window keys stand aside for text fields |
| [MLOG-01](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-01) | MUST | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:935`: every status-bar message goes to one `MessageLog`; `MessageLogView.java:155` lists it |
| [MLOG-02](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-02) | SHOULD | pass | `src/main/java/com/mirada/ui/StatusBar.java:69`: the echo label and the unread count (:72) run `Command.MESSAGE_LOG` |
| [MLOG-03](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-03) | SHOULD | pass | `src/main/java/com/mirada/ui/MessageLogView.java:120`: Escape and C-g close it; the backdrop closes it on a press (:58) |
| [MLOG-04](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-04) | SHOULD | pass | `src/main/java/com/mirada/ui/MessageLogView.java:107`: shortcut+C on the list runs Copy Messages, the selection or what is listed |
| [MLOG-06](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-06) | SHOULD | pass | `src/main/java/com/mirada/ui/MessageLogView.java:127`: opening marks the log read; `MessageLog.clear` zeroes the count (`src/main/java/com/mirada/diagnostics/MessageLog.java:99`) |
| [MLOG-07](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-07) | SHOULD | pass | `src/test/java/com/mirada/diagnostics/MessageLogTest.java:79`: anEvictedErrorStillCounts |
| [SYNC-02](https://pauta-project.dev/4.0.1/templates/settings-sync/#sync-02) | SHOULD | n/a | no Settings Sync |
| [REL-01](https://pauta-project.dev/4.0.1/release/#rel-01) | MUST | pass | `.github/workflows/release.yml:79`: five-target matrix, one leg each; pom.xml dist profile; native image inside jpackage's app image on 4 targets, jlink on macos-x64; dmg by hdiutil |
| [REL-02](https://pauta-project.dev/4.0.1/release/#rel-02) | SHOULD | pass | `.github/workflows/release.yml:280`: renames every file to Mirada-<version>-<target>.<ext>; check-assets (:418) expects those names |
| [REL-03](https://pauta-project.dev/4.0.1/release/#rel-03) | MUST | pass | `jreleaser.yml:76`: JReleaser only creates the release (files.globs, checksums_sha256.txt and its signature); `.github/workflows/release.yml:546` runs it in the publish job after the build, sign-linux and asset-check jobs |
| [REL-04](https://pauta-project.dev/4.0.1/release/#rel-04) | SHOULD | pass | `scripts/release-notes.sh`: the version's CHANGELOG.md section under .github/release-notes-header.md, refused on a tag if empty; JReleaser's changelog.external (`jreleaser.yml:53`) |
| [REL-05](https://pauta-project.dev/4.0.1/release/#rel-05) | MUST | pass | `.github/workflows/release.yml:68`: preflight fails when tag != pom version, refuses a snapshot |
| [REL-06](https://pauta-project.dev/4.0.1/release/#rel-06) | SHOULD | pass | `.github/workflows/release.yml:593`: the bump job opens a pull request moving main to the next patch -SNAPSHOT, with CI dispatched on its branch; `pom.xml:9` is 0.4.0-SNAPSHOT |
| [REL-07](https://pauta-project.dev/4.0.1/release/#rel-07) | SHOULD | pass | `.github/workflows/release.yml:418`: check-assets fails before publishing on any expected file missing, or any unexpected one |
| [REL-08](https://pauta-project.dev/4.0.1/release/#rel-08) | SHOULD | pass | packaging/linux/Mirada.desktop:13 StartupWMClass=com.mirada.App |
| [REL-10](https://pauta-project.dev/4.0.1/release/#rel-10) | SHOULD | pass | `pom.xml`: aot-train runs scripts/aot-train.sh with -XX:AOTCacheOutput (the native image replaces it on four targets) |
| [REL-11](https://pauta-project.dev/4.0.1/release/#rel-11) | MUST | pass | `jreleaser.yml:76`: dmg, msi, deb, rpm, AppImage and tar.gz with their .asc, and checksums_sha256.txt (`scripts/build-installers.sh:6` makes them) |
| [REL-15](https://pauta-project.dev/4.0.1/release/#rel-15) | MUST | pass | `.github/workflows/release.yml:149`: imports MACOS_CERT_P12 and MACOS_CERT_PASSWORD (`scripts/ci-signing-credentials.sh`), hardened runtime and entitlements (`scripts/sign-mac.sh`); `scripts/notarize-mac.sh:60` notarizes with APPLE_API_KEY_P8, APPLE_API_KEY_ID and APPLE_API_ISSUER, staples, runs spctl and launches the notarized app from the dmg (:87). The secrets are not set on the repository yet |
| [REL-16](https://pauta-project.dev/4.0.1/release/#rel-16) | MUST | pass | `.github/workflows/release.yml:318`: a sign-linux job reads LINUX_GPG_KEY and LINUX_GPG_PASSPHRASE, runs `scripts/sign-linux-packages.sh` (rpmsign, debsigs `_gpgorigin`, .asc beside every file) and `scripts/check-linux-signatures.sh` against the committed `packaging/linux/signing-key.asc`, which is attached as mirada-signing-key.asc |
| [REL-18](https://pauta-project.dev/4.0.1/release/#rel-18) | MUST | pass | `.github/workflows/release.yml:504`: attest-build-provenance on every file before JReleaser; `src/test/java/com/mirada/ReleaseSupplyChainTest.java:380` keeps the subject-path equal to jreleaser.yml's files.globs |
| [REL-21](https://pauta-project.dev/4.0.1/release/#rel-21) | MUST | pass | `.github/workflows/release.yml:26`: version tags and workflow_dispatch; preflight, five-target matrix, sign-linux, check-assets, attestations, JReleaser (`full-release --dry-run` on a dispatch, :546), winget, then the bump (:593) |
| [REL-22](https://pauta-project.dev/4.0.1/release/#rel-22) | MUST | pass | `.github/workflows/ci.yml:42`: every external action pinned to a 40-character SHA with its version |
| [REL-23](https://pauta-project.dev/4.0.1/release/#rel-23) | MUST | pass | `.github/dependabot.yml:4`: github-actions and maven, weekly |
| [REL-24](https://pauta-project.dev/4.0.1/release/#rel-24) | MUST | pass | `.github/workflows/ci.yml:19`: contents: read at the top of all five workflows; write only in release and codecs publish jobs |
| [REL-25](https://pauta-project.dev/4.0.1/release/#rel-25) | SHOULD | pass | `.github/workflows/ci.yml:43`: setup-java 25 with cache: maven (GraalVM 25.4 for native legs) |
| [REL-26](https://pauta-project.dev/4.0.1/release/#rel-26) | MUST | pass | `.github/workflows/ci.yml:27`: timeout-minutes on every job; cancel-in-progress only for pull_request |
| [REL-27](https://pauta-project.dev/4.0.1/release/#rel-27) | MUST | pass | `.github/workflows/ci.yml:87`: surefire reports and the JaCoCo report uploaded under if: always(); "Summarise coverage" appends lines, methods and branches to $GITHUB_STEP_SUMMARY |
| [REL-28](https://pauta-project.dev/4.0.1/release/#rel-28) | MUST | pass | `scripts/ci-signing-credentials.sh:35`: a tag without the four macOS secrets fails before the keychain; sign-linux (`.github/workflows/release.yml:318`) and the checksum key (:519) fail a tag without LINUX_GPG_KEY; dry runs and forks sign with throwaway credentials |
| [REL-29](https://pauta-project.dev/4.0.1/release/#rel-29) | MUST | pass | `.github/workflows/release.yml:170`: scripts/smoke-test.sh opens every fixture in each packaged app, the installed msi, deb and AppImage, and the notarized app from the dmg, before publishing |
