# Pauta audit

Mirada at `8397bad`, audited against [Pauta 4.0.1](https://pauta-project.dev/4.0.1/) on 2026-11-08.
Scope: every rule (351). Rules from https://pauta-project.dev/4.0.1/rules.json.
Release rules (REL-03, -06, -07, -15, -16, -18, -19, -21, -28, QUAL-15) re-checked on 2026-11-08 after
the release workflow's rework.

Regenerated rather than edited: each rule was judged by its check (code, test or by hand) as the
edition's [audit procedure](https://pauta-project.dev/4.0.1/coding-agents/) says. A rule met only in
part is a fail. A rule is n.a. only when its text names apps Mirada is not (a tray, tabs, a terminal),
never because Mirada lacks a part another rule requires.

|        | Pass | Fail | Manual | N.a. |
|--------|-----:|-----:|-------:|-----:|
| MUST   | 70 | 69 | 3 | 20 |
| SHOULD | 38 | 67 | 15 | 60 |
| MAY    | 0 | 3 | 0 | 6 |

A failed MUST blocks a release.

## Failed MUSTs

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [PLAT-01](https://pauta-project.dev/4.0.1/platform/#plat-01) | MUST | fail | `pom.xml:202`: release 25, but the enforcer's range is [25,26), a ceiling (the formatter broke on 27) |
| [PLAT-06](https://pauta-project.dev/4.0.1/platform/#plat-06) | MUST | fail | `src/main/resources/com/mirada/styles/mirada.css:50`: fixed hexes in component rules (:50, :161, :490, :531, :535) |
| [PLAT-11](https://pauta-project.dev/4.0.1/platform/#plat-11) | MUST | fail | `pom.xml:295`: Headless glass and JUnit 5, no Monocle; but no TestFX (src/test/java/com/mirada/image/FxToolkit.java:22 starts the toolkit by hand) |
| [PLAT-12](https://pauta-project.dev/4.0.1/platform/#plat-12) | MUST | fail | `pom.xml`: no jacoco-maven-plugin, no jacoco-check |
| [REPO-01](https://pauta-project.dev/4.0.1/repository/#repo-01) | MUST | fail | `README.md:62`: has logo, badges, pitch, hero, install, build, licence; no highlights, command line or config folder section, no website link; a key table instead |
| [REPO-04](https://pauta-project.dev/4.0.1/repository/#repo-04) | MUST | fail | NOTICE:1 name, copyright, "licensed under the MIT License", every component; but no NoticeCoverageTest or BuildHygieneTest |
| [REPO-07](https://pauta-project.dev/4.0.1/repository/#repo-07) | MUST | fail | docs/ lacks architecture, conventions, testing, gotchas, release, building-and-packaging, decisions/ |
| [DOCS-01](https://pauta-project.dev/4.0.1/documentation/#docs-01) | MUST | fail | mirada-website/src/content/docs has getting started, features, keyboard, settings, FAQ, but no screenshots in the pages |
| [DOCS-05](https://pauta-project.dev/4.0.1/documentation/#docs-05) | MUST | fail | `.github/workflows/release.yml:327`: release notes from CHANGELOG, but no releaseNotesUrl reachable from the app |
| [DOCS-06](https://pauta-project.dev/4.0.1/documentation/#docs-06) | MUST | fail | `src/main/java/com/mirada/ui/MacMenus.java:78`: no Help → Documentation command |
| [DOCS-14](https://pauta-project.dev/4.0.1/documentation/#docs-14) | MUST | fail | `AGENTS.md`: carries the catalogs (529 lines); no CONTRIBUTING.md, no docs/reference/ |
| [DOCS-19](https://pauta-project.dev/4.0.1/documentation/#docs-19) | MUST | fail | `README.md:62`: a 28-row key table, no link to the website's docs |
| [QUAL-12](https://pauta-project.dev/4.0.1/quality/#qual-12) | MUST | fail | no message catalogs, so no parity test (descriptions are checked: CommandTest:189) |
| [PERF-01](https://pauta-project.dev/4.0.1/performance/#perf-01) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:568`: folder listing, sorting stats, settings and bookmark writes, rename and existence checks run on the FX thread |
| [PERF-03](https://pauta-project.dev/4.0.1/performance/#perf-03) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:463`: images guarded by path (wanted), but sidebar reads for the same file have no generation guard |
| [PERF-06](https://pauta-project.dev/4.0.1/performance/#perf-06) | MUST | fail | `src/main/java/com/mirada/ui/BookmarksWindow.java:101`: the filter refreshes, with a stat per mark, on every keystroke, undebounced |
| [PERF-08](https://pauta-project.dev/4.0.1/performance/#perf-08) | MUST | fail | `src/main/java/com/mirada/ui/ThumbnailStrip.java:194`: items.equals(images) is O(folder) on every step; lists are virtualized |
| [PERF-11](https://pauta-project.dev/4.0.1/performance/#perf-11) | MUST | fail | `src/test/java/com/mirada/edit/HistoryTest.java`: history and recent folders capped and tested; ImageCache, Thumbnails and the trash undo stack capped without tests |
| [PERF-12](https://pauta-project.dev/4.0.1/performance/#perf-12) | MUST | fail | `src/main/java/com/mirada/image/Animation.java:22`: one threshold; no size or heap guard before decoding |
| [PERF-13](https://pauta-project.dev/4.0.1/performance/#perf-13) | MUST | fail | no test that a closed window releases listeners and threads |
| [PERF-14](https://pauta-project.dev/4.0.1/performance/#perf-14) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:358`: panels built on first open, but no test |
| [PERF-15](https://pauta-project.dev/4.0.1/performance/#perf-15) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:296`: deferred work after the first paint, but no test |
| [KBD-01](https://pauta-project.dev/4.0.1/keyboard/#kbd-01) | MUST | fail | `src/main/java/com/mirada/ui/Command.java`: the Command enum and ViewerWindow.run are the registry, but the editor's tools, the Bookmarks window's row actions and Copy details are not commands |
| [KBD-03](https://pauta-project.dev/4.0.1/keyboard/#kbd-03) | MUST | fail | `src/main/java/com/mirada/ui/Command.java:20`: titles and descriptions are English literals, no catalog |
| [KBD-09](https://pauta-project.dev/4.0.1/keyboard/#kbd-09) | MUST | fail | `src/main/java/com/mirada/ui/Command.java:397`: bindings hard-coded; no Emacs or CUA keymap JSON |
| [KBD-11](https://pauta-project.dev/4.0.1/keyboard/#kbd-11) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:684`: user keys only for the palette |
| [KBD-17](https://pauta-project.dev/4.0.1/keyboard/#kbd-17) | MUST | fail | `src/main/java/com/mirada/ui/InfoPanel.java:86`: icon-only buttons built in four places, no single helper; InfoPanel's lack accessible text |
| [WIN-01](https://pauta-project.dev/4.0.1/window-shell/#win-01) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:815`: no status bar or echo area; feedback is a fading toast |
| [WIN-07](https://pauta-project.dev/4.0.1/window-shell/#win-07) | MUST | fail | `src/main/java/com/mirada/ui/AboutWindow.java`: version, build, commit, Pauta edition, runtime, license, links; no update-check result |
| [ICON-01](https://pauta-project.dev/4.0.1/icons-and-color/#icon-01) | MUST | fail | `src/main/java/com/mirada/ui/Theme.java:59`: window listener exists, but no ui-font.css and no Inter |
| [ICON-02](https://pauta-project.dev/4.0.1/icons-and-color/#icon-02) | MUST | fail | no fonts/inter, no Font.loadFont |
| [ICON-08](https://pauta-project.dev/4.0.1/icons-and-color/#icon-08) | MUST | fail | `src/main/resources/com/mirada/styles/mirada.css:50`: hexes outside .root (:50, :161, :490, :531, :535) and literal white/black/rgba |
| [ICON-10](https://pauta-project.dev/4.0.1/icons-and-color/#icon-10) | MUST | fail | `src/main/resources/com/mirada/styles/mirada.css:260`: Cupertino Light's accent text #007aff is 3.5–4.0:1 on its subtle fills; Accent.java:31 aims at 3:1 |
| [ICON-11](https://pauta-project.dev/4.0.1/icons-and-color/#icon-11) | MUST | fail | `src/main/resources/com/mirada/styles/mirada.css:121`: fg-emphasis on accent rows, but white on #007aff is 4.0:1 and nothing checks it |
| [ICON-12](https://pauta-project.dev/4.0.1/icons-and-color/#icon-12) | MUST | fail | no -focus-ring token, no :focus-visible block |
| [ICON-20](https://pauta-project.dev/4.0.1/icons-and-color/#icon-20) | MUST | fail | `src/main/java/com/mirada/ui/OnScreenControls.java:41`: zoom button's glyphs differ from the zoom menu's; InfoPanel's note button wears RENAME (InfoPanel.java:139) |
| [ICON-21](https://pauta-project.dev/4.0.1/icons-and-color/#icon-21) | MUST | fail | `src/main/java/com/mirada/ui/InfoPanel.java:86`: two icon-only buttons with a tooltip and no accessible text; no one helper |
| [TBAR-01](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-01) | MUST | fail | `src/main/java/com/mirada/ui/InfoPanel.java:88`: setTooltip without setAccessibleText; TitleBar sets them from different strings |
| [TBAR-03](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-03) | MUST | fail | `src/main/java/com/mirada/ui/TitleBar.java:220`: tooltips typed ("Rotate Right (R)"), also OnScreenControls.java:39, Editor.java:90 |
| [TBAR-08](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-08) | MUST | fail | `src/main/java/com/mirada/ui/OnScreenControls.java:39`: Previous and Next are in no in-window menu on Linux and Windows |
| [TABS-14](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-14) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:358`: panels built on first use, but no test that none is built before the first paint |
| [MENU-14](https://pauta-project.dev/4.0.1/menus/#menu-14) | MUST | fail | `src/main/java/com/mirada/ui/Icons.java:95`: every row has an icon, but borrowed ones (Bookmarks wears Note's, About Info's, Hide Thumbnails Close's) and no FX test |
| [MENU-15](https://pauta-project.dev/4.0.1/menus/#menu-15) | MUST | fail | `src/main/java/com/mirada/ui/MenuChord.java:37`: in-window chords shown in -state-key, but the Mac bar hides bare chords and they aren't Inter |
| [MENU-16](https://pauta-project.dev/4.0.1/menus/#menu-16) | MUST | fail | no Inter, nothing pinned on .context-menu |
| [MENU-02](https://pauta-project.dev/4.0.1/menus/#menu-02) | MUST | fail | `src/main/java/com/mirada/ui/MacMenus.java:152`: the macOS bar registers real Cmd accelerators (in-window menus use MenuChord, which never matches) |
| [MENU-06](https://pauta-project.dev/4.0.1/menus/#menu-06) | MUST | fail | `src/main/java/com/mirada/ui/Popups.java:34`: an open menu closes before the next, but no test |
| [MENU-07](https://pauta-project.dev/4.0.1/menus/#menu-07) | MUST | fail | `src/main/java/com/mirada/ui/Popups.java:36`: the outside press is consumed |
| [MENU-09](https://pauta-project.dev/4.0.1/menus/#menu-09) | MUST | fail | no Menu key or Shift+F10 handling |
| [SET-02](https://pauta-project.dev/4.0.1/settings/#set-02) | MUST | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:179`: one page of cards; no page sidebar, no search field |
| [SET-03](https://pauta-project.dev/4.0.1/settings/#set-03) | MUST | fail | no Settings search and no ranking test |
| [SET-06](https://pauta-project.dev/4.0.1/settings/#set-06) | MUST | fail | `src/main/java/com/mirada/ui/Command.java:120`: no palette command for theme, thumbnails/properties at start, controls delay, slideshow speed, palette keys |
| [SET-09](https://pauta-project.dev/4.0.1/settings/#set-09) | MUST | fail | `src/main/java/com/mirada/settings/Settings.java`: no SCHEMA_VERSION or migration in settings or bookmarks |
| [SET-10](https://pauta-project.dev/4.0.1/settings/#set-10) | MUST | fail | no version, so no too-new backup <name>.v<n>.bak |
| [SET-12](https://pauta-project.dev/4.0.1/settings/#set-12) | MUST | fail | no Export or Import of the config folder |
| [DIAG-01](https://pauta-project.dev/4.0.1/diagnostics/#diag-01) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:815`: messages are a two-second toast; no Message Log |
| [DIAG-02](https://pauta-project.dev/4.0.1/diagnostics/#diag-02) | MUST | fail | no java.util.logging capture, no mirada-session.log |
| [DIAG-03](https://pauta-project.dev/4.0.1/diagnostics/#diag-03) | MUST | fail | no Debug Log window |
| [DIAG-04](https://pauta-project.dev/4.0.1/diagnostics/#diag-04) | MUST | fail | `src/main/java/com/mirada/App.java:38`: handler installed first in main, no dialog, said over the window; but there is no Debug Log to route to (DIAG-02) |
| [DIAG-05](https://pauta-project.dev/4.0.1/diagnostics/#diag-05) | MUST | fail | no update check, About line, Check for Updates command or setting |
| [DIAG-06](https://pauta-project.dev/4.0.1/diagnostics/#diag-06) | MUST | fail | no Report a Problem or redactor |
| [CLI-05](https://pauta-project.dev/4.0.1/command-line/#cli-05) | MUST | fail | no --report |
| [CLI-07](https://pauta-project.dev/4.0.1/command-line/#cli-07) | MUST | fail | `src/main/java/com/mirada/App.java:68`: macOS Apple Events reach the running app, but no single instance per config folder |
| [INTL-01](https://pauta-project.dev/4.0.1/i18n/#intl-01) | MUST | fail | no messages*.properties; every string is an English literal |
| [INTL-08](https://pauta-project.dev/4.0.1/i18n/#intl-08) | MUST | fail | no catalogs, no parity test |
| [PAL-02](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-02) | MUST | fail | `src/main/java/com/mirada/ui/Command.java:337`: inPalette() leaves out Escape, Command Palette and the four Pan commands |
| [ABOUT-02](https://pauta-project.dev/4.0.1/templates/about/#about-02) | MUST | fail | `src/main/java/com/mirada/ui/AboutWindow.java:81`: version, build, commit, Pauta edition, runtime, license, links, same text as --version; no update-check result |
| [MLOG-01](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-01) | MUST | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:815`: messages are a two-second toast with no log (WIN-01 requires the status bar MLOG presumes) |
| [REL-19](https://pauta-project.dev/4.0.1/release/#rel-19) | MUST | fail | `.github/workflows/ci.yml:15`: push to main and every pull request; Format (`spotless:check`, :35) and Build & Test (`./mvnw verify`, :55) are separate jobs. Missing: the coverage gate (PLAT-12, REL-27: no JaCoCo), and the two checks are not required on main yet (branch protection, a repository setting the owner turns on) |
| [REL-27](https://pauta-project.dev/4.0.1/release/#rel-27) | MUST | fail | `.github/workflows/ci.yml:61`: surefire reports uploaded always; no JaCoCo report or coverage summary |

## Other failures

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [PLAT-07](https://pauta-project.dev/4.0.1/platform/#plat-07) | SHOULD | fail | no Inter, no Font.loadFont, no ui-font.css |
| [PLAT-13](https://pauta-project.dev/4.0.1/platform/#plat-13) | SHOULD | fail | no java.util.logging; System.err in 8 places (src/main/java/com/mirada/ui/ViewerWindow.java:619) |
| [PLAT-14](https://pauta-project.dev/4.0.1/platform/#plat-14) | SHOULD | fail | `src/main/java/com/mirada/settings/Settings.java:456`: .properties, no schema version |
| [REPO-08](https://pauta-project.dev/4.0.1/repository/#repo-08) | MAY | fail | no TODO.md (AGENTS.md "Not done") |
| [DOCS-03](https://pauta-project.dev/4.0.1/documentation/#docs-03) | SHOULD | fail | `mirada-website/astro.config.mjs:27`: docs not versioned; no docs URL in the app |
| [DOCS-04](https://pauta-project.dev/4.0.1/documentation/#docs-04) | SHOULD | fail | `mirada-website/scripts/GenCommands.java`: generates commands and version, but What's new is hand-written and no check:docs |
| [DOCS-07](https://pauta-project.dev/4.0.1/documentation/#docs-07) | SHOULD | fail | `src/main/java/com/mirada/ui/MacMenus.java:78`: Help: Command Palette, Keyboard Shortcuts; no Documentation |
| [DOCS-10](https://pauta-project.dev/4.0.1/documentation/#docs-10) | MAY | fail | no per-command docs |
| [DOCS-11](https://pauta-project.dev/4.0.1/documentation/#docs-11) | MAY | fail | no Help-for-page links in Settings |
| [DOCS-13](https://pauta-project.dev/4.0.1/documentation/#docs-13) | SHOULD | fail | no docs/decisions/ |
| [DOCS-15](https://pauta-project.dev/4.0.1/documentation/#docs-15) | SHOULD | fail | no stacked Javadoc today, but no test guards it |
| [QUAL-03](https://pauta-project.dev/4.0.1/quality/#qual-03) | SHOULD | fail | no JaCoCo floors |
| [QUAL-08](https://pauta-project.dev/4.0.1/quality/#qual-08) | SHOULD | fail | tests use @TempDir, but nothing checks the home folder stays untouched |
| [QUAL-11](https://pauta-project.dev/4.0.1/quality/#qual-11) | SHOULD | fail | no command sweep |
| [QUAL-13](https://pauta-project.dev/4.0.1/quality/#qual-13) | SHOULD | fail | `src/test/java/com/mirada/settings/SettingsTest.java:114`: the file round-trips; no FX test of the Settings window's controls |
| [QUAL-14](https://pauta-project.dev/4.0.1/quality/#qual-14) | SHOULD | fail | `src/test/java/com/mirada/ui/AccentTest.java:36`: contrast for the accent only, not the stylesheet's tokens |
| [PERF-04](https://pauta-project.dev/4.0.1/performance/#perf-04) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:92`: details and EXIF virtual-thread executors are unnamed |
| [PERF-16](https://pauta-project.dev/4.0.1/performance/#perf-16) | SHOULD | fail | `src/main/java/com/mirada/App.java:55`: settings read on the FX thread in start() |
| [PERF-17](https://pauta-project.dev/4.0.1/performance/#perf-17) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:1494`: AOT cache trained; mirada.css shipped as .css, not .bss |
| [PERF-20](https://pauta-project.dev/4.0.1/performance/#perf-20) | SHOULD | fail | no FX-thread stack sampler |
| [PERF-21](https://pauta-project.dev/4.0.1/performance/#perf-21) | SHOULD | fail | only FuzzyMatchTest guards a hot path |
| [KBD-02](https://pauta-project.dev/4.0.1/keyboard/#kbd-02) | SHOULD | fail | `src/main/java/com/mirada/ui/Command.java`: enum constants, no dotted ids |
| [KBD-04](https://pauta-project.dev/4.0.1/keyboard/#kbd-04) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:249`: the palette greys with a reason; menus disable without one |
| [KBD-10](https://pauta-project.dev/4.0.1/keyboard/#kbd-10) | SHOULD | fail | no IntelliJ, VS Code or Sublime keymaps |
| [KBD-12](https://pauta-project.dev/4.0.1/keyboard/#kbd-12) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:143`: edits only the palette keys |
| [KBD-13](https://pauta-project.dev/4.0.1/keyboard/#kbd-13) | SHOULD | fail | `src/main/java/com/mirada/ui/Editor.java:90`: chords typed into text ("Ctrl+Z" on a Mac too) |
| [KBD-16](https://pauta-project.dev/4.0.1/keyboard/#kbd-16) | SHOULD | fail | no :focus-visible layer in mirada.css |
| [KBD-18](https://pauta-project.dev/4.0.1/keyboard/#kbd-18) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:68`: on/off rows are CheckBoxes, not a switch |
| [WIN-04](https://pauta-project.dev/4.0.1/window-shell/#win-04) | SHOULD | fail | `src/main/java/com/mirada/ui/NoteDialog.java:32`: each dialog styles itself; no Dialogs.styled |
| [WIN-08](https://pauta-project.dev/4.0.1/window-shell/#win-08) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:529`: window place restored (WindowPlacementTest), the open folder isn't; no --no-session |
| [WIN-09](https://pauta-project.dev/4.0.1/window-shell/#win-09) | SHOULD | fail | `src/main/java/com/mirada/ui/Command.java:415`: Ctrl/Cmd + - 0 zoom the image; the zoom isn't kept |
| [ICON-05](https://pauta-project.dev/4.0.1/icons-and-color/#icon-05) | SHOULD | fail | `src/main/java/com/mirada/ui/Theme.java:83`: Cupertino, not Caret & Ink |
| [ICON-06](https://pauta-project.dev/4.0.1/icons-and-color/#icon-06) | SHOULD | fail | `src/main/resources/com/mirada/styles/mirada.css:128`: -state-key is Editora's ladder (9.1/8.2:1 light), but no test |
| [ICON-09](https://pauta-project.dev/4.0.1/icons-and-color/#icon-09) | SHOULD | fail | `src/main/resources/com/mirada/styles/mirada.css:116`: fg-subtle only for disabled text, but no test |
| [ICON-13](https://pauta-project.dev/4.0.1/icons-and-color/#icon-13) | SHOULD | fail | `src/main/resources/com/mirada/styles/mirada.css:129`: periwinkle is a ladder, but no test |
| [ICON-14](https://pauta-project.dev/4.0.1/icons-and-color/#icon-14) | SHOULD | fail | `src/test/java/com/mirada/ui/AccentTest.java`: does WCAG arithmetic but never reads the stylesheets |
| [TABS-10](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-10) | SHOULD | fail | `src/main/java/com/mirada/ui/ViewerWindow.java:589`: title "name — Mirada", without the folder |
| [TABS-15](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-15) | SHOULD | fail | strip layout persists; open panels are a start setting, not the last state |
| [MENU-03](https://pauta-project.dev/4.0.1/menus/#menu-03) | SHOULD | fail | `src/main/java/com/mirada/ui/TitleBar.java:246`: the header menu refreshes chords on showing but never greys a row |
| [MENU-12](https://pauta-project.dev/4.0.1/menus/#menu-12) | SHOULD | fail | no danger class on Move to Trash or Remove |
| [SET-05](https://pauta-project.dev/4.0.1/settings/#set-05) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:169`: one Settings object per process; a second process never sees a change and overwrites it |
| [SET-08](https://pauta-project.dev/4.0.1/settings/#set-08) | SHOULD | fail | `src/main/java/com/mirada/settings/Settings.java:99`: names are constants, but .properties, with window place and recent folders in the preferences file |
| [SET-17](https://pauta-project.dev/4.0.1/settings/#set-17) | SHOULD | fail | no Settings Sync |
| [DIAG-07](https://pauta-project.dev/4.0.1/diagnostics/#diag-07) | SHOULD | fail | no Doctor page (a row per codec library would fit) |
| [CLI-03](https://pauta-project.dev/4.0.1/command-line/#cli-03) | SHOULD | fail | no --new-instance (every launch is a new process today) |
| [CLI-08](https://pauta-project.dev/4.0.1/command-line/#cli-08) | SHOULD | fail | no advisory lock on the config folder |
| [CLI-10](https://pauta-project.dev/4.0.1/command-line/#cli-10) | SHOULD | fail | `src/main/java/com/mirada/os/Reveal.java:27`: gio, xdg-open, gdbus started without the login shell's PATH (all OS tools today) |
| [INTL-02](https://pauta-project.dev/4.0.1/i18n/#intl-02) | SHOULD | fail | no language list or picker |
| [INTL-07](https://pauta-project.dev/4.0.1/i18n/#intl-07) | SHOULD | fail | `src/main/java/com/mirada/ui/SettingsWindow.java:40`: raw English literals passed to controls |
| [PAL-05](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-05) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:368`: selects the first enabled row, but no test with a disabled top match |
| [PAL-07](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-07) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:242`: Esc, C-g and the scrim cancel, but focus is not restored |
| [PAL-08](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-08) | SHOULD | fail | no per-command docs page; F1 opens the palette |
| [QOPEN-02](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-02) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:37`: the picker card has no title naming the command |
| [QOPEN-11](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-11) | SHOULD | fail | `src/main/java/com/mirada/ui/CommandPalette.java:172`: a second card is refused rather than replacing the first |
| [QOPEN-12](https://pauta-project.dev/4.0.1/templates/quick-open/#qopen-12) | SHOULD | fail | `src/main/java/com/mirada/ui/BookmarksWindow.java:221`: the Bookmarks list maps keys by hand, not through PaletteKeys |
| [CONF-05](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-05) | SHOULD | fail | `src/main/java/com/mirada/ui/Editor.java`: no Save / Discard / Cancel question |
| [CONF-12](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-12) | SHOULD | fail | `src/test/java/com/mirada/ui/ConfirmTest.java`: the words come from pure builders, tested; no capture hook for UI tests |
| [MLOG-02](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-02) | SHOULD | fail | no Message Log |
| [MLOG-03](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-03) | SHOULD | fail | no Message Log |
| [MLOG-04](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-04) | SHOULD | fail | no Message Log |
| [MLOG-05](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-05) | SHOULD | fail | no Message Log |
| [MLOG-06](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-06) | SHOULD | fail | no Message Log |
| [MLOG-07](https://pauta-project.dev/4.0.1/templates/message-log/#mlog-07) | SHOULD | fail | no Message Log |
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
| [PERF-18](https://pauta-project.dev/4.0.1/performance/#perf-18) | SHOULD | manual | costs recorded in commits and AGENTS.md "Start-up, measured" |
| [PERF-22](https://pauta-project.dev/4.0.1/performance/#perf-22) | SHOULD | manual | `AGENTS.md`: records before/after numbers |
| [KBD-15](https://pauta-project.dev/4.0.1/keyboard/#kbd-15) | SHOULD | manual | `src/main/java/com/mirada/ui/OnScreenControls.java:198`: chrome out of the Tab order; Tab through Settings, Bookmarks and dialogs by hand |
| [KBD-20](https://pauta-project.dev/4.0.1/keyboard/#kbd-20) | SHOULD | manual | no translations to try; labels wrap |
| [WIN-06](https://pauta-project.dev/4.0.1/window-shell/#win-06) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:1312`: empty window offers Open Image and Open Folder only |
| [TBAR-06](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-06) | SHOULD | manual | `src/main/resources/com/mirada/styles/mirada.css:36`: one band per edge; hairline against the image |
| [TABS-23](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-23) | SHOULD | manual | the properties sidebar follows the image shown |
| [MENU-05](https://pauta-project.dev/4.0.1/menus/#menu-05) | MUST | manual | `src/main/java/com/mirada/ui/ThumbnailStrip.java:105`: the strip's menu is the strip's, not the clicked image's; bookmark row actions aren't commands |
| [MENU-08](https://pauta-project.dev/4.0.1/menus/#menu-08) | MUST | manual | `src/main/java/com/mirada/ui/Popups.java:48`: hide-on-escape on; focus stays |
| [MENU-10](https://pauta-project.dev/4.0.1/menus/#menu-10) | SHOULD | manual | `src/main/java/com/mirada/ui/BookmarksWindow.java:413`: bookmark rows select first; the strip's menu is strip-wide |
| [CLI-04](https://pauta-project.dev/4.0.1/command-line/#cli-04) | SHOULD | manual | `src/main/java/com/mirada/App.java:64`: no session is restored, but --no-session is opened as a path |
| [CLI-06](https://pauta-project.dev/4.0.1/command-line/#cli-06) | SHOULD | manual | `src/main/java/com/mirada/App.java:64`: only the first argument opens |
| [INTL-04](https://pauta-project.dev/4.0.1/i18n/#intl-04) | SHOULD | manual | English only; Settings rows wrap (src/main/java/com/mirada/ui/SettingsWindow.java:276) |
| [WELC-02](https://pauta-project.dev/4.0.1/templates/welcome/#welc-02) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:99`: empty state offers Open Image and Open Folder; chords only in tooltips, no Open Recent or palette |
| [ABOUT-07](https://pauta-project.dev/4.0.1/templates/about/#about-07) | MUST | manual | this audit; 4.0.1 is honest only once its failed MUSTs pass |
| [CONF-07](https://pauta-project.dev/4.0.1/templates/confirm-dialog/#conf-07) | SHOULD | manual | `src/main/java/com/mirada/ui/ViewerWindow.java:1082`: Move to Trash asks nothing and offers Ctrl+Z (Linux only) |

## Passed and not applicable

| ID | Level | Result | Evidence |
|----|-------|--------|----------|
| [PLAT-02](https://pauta-project.dev/4.0.1/platform/#plat-02) | MUST | pass | `pom.xml:388`: dist profile: jpackage app image with jLinkOptions; the native image replaces the runtime on four targets |
| [PLAT-03](https://pauta-project.dev/4.0.1/platform/#plat-03) | MUST | pass | `pom.xml:28`: javafx.version 27 |
| [PLAT-04](https://pauta-project.dev/4.0.1/platform/#plat-04) | MUST | pass | `src/main/java/module-info.java:1`: module com.mirada, no opens |
| [PLAT-05](https://pauta-project.dev/4.0.1/platform/#plat-05) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:83`: AtlantaFX 3.0.0 Cupertino Light and Dark |
| [PLAT-08](https://pauta-project.dev/4.0.1/platform/#plat-08) | MUST | pass | mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties committed |
| [PLAT-09](https://pauta-project.dev/4.0.1/platform/#plat-09) | MUST | pass | `pom.xml:261`: -Xep:NullAway:ERROR; @NullMarked everywhere (NullMarkedPackagesTest) |
| [PLAT-10](https://pauta-project.dev/4.0.1/platform/#plat-10) | MUST | pass | `pom.xml:217`: palantirJavaFormat; spotless check bound to verify |
| [REPO-02](https://pauta-project.dev/4.0.1/repository/#repo-02) | MUST | pass | `CHANGELOG.md:3`: Keep a Changelog 1.1 and SemVer, "## [Unreleased]" with Added/Changed/Fixed, bold-led entries |
| [REPO-03](https://pauta-project.dev/4.0.1/repository/#repo-03) | MUST | pass | LICENSE:3 MIT License, Copyright (c) 2026 Adrián Arturo De León Saldivar |
| [REPO-05](https://pauta-project.dev/4.0.1/repository/#repo-05) | MUST | pass | `AGENTS.md:64`: commands, one worktree per task, the change checklist; CLAUDE.md points to it |
| [REPO-06](https://pauta-project.dev/4.0.1/repository/#repo-06) | MUST | pass | `CONTRIBUTING.md:13`: build, test and format commands and the pull-request steps |
| [REPO-09](https://pauta-project.dev/4.0.1/repository/#repo-09) | MUST | pass | `.github/ISSUE_TEMPLATE/bug_report.yml`: fields id: environment and id: report |
| [REPO-10](https://pauta-project.dev/4.0.1/repository/#repo-10) | MUST | pass | branding/mirada-icon.svg with png, icns, ico; pom.xml:67 jpackage.icon |
| [DOCS-02](https://pauta-project.dev/4.0.1/documentation/#docs-02) | MUST | pass | `mirada-website/src/content/docs/*.md`: each with category and order; no copy here |
| [DOCS-09](https://pauta-project.dev/4.0.1/documentation/#docs-09) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:212`: every command has a description, shown in the palette; CommandTest:189 non-blank |
| [DOCS-12](https://pauta-project.dev/4.0.1/documentation/#docs-12) | MUST | pass | `docs/README.md:1`: for contributors, user docs on the website, the code wins, every guide listed |
| [DOCS-16](https://pauta-project.dev/4.0.1/documentation/#docs-16) | MUST | pass | `AGENTS.md:68`: docs change in the same pull request (CHANGELOG, docs/, website); CONTRIBUTING.md:31 |
| [DOCS-17](https://pauta-project.dev/4.0.1/documentation/#docs-17) | MUST | pass | `AGENTS.md:66`: the code wins, and the docs are fixed in the same change (also docs/README.md) |
| [DOCS-18](https://pauta-project.dev/4.0.1/documentation/#docs-18) | SHOULD | pass | `src/test/java/com/mirada/ui/HeadlessDriver.java:116`: renders the real window to PNG (1×, not 2×) |
| [QUAL-01](https://pauta-project.dev/4.0.1/quality/#qual-01) | MUST | pass | `pom.xml:261`: NullAway an error; NullMarkedPackagesTest |
| [QUAL-02](https://pauta-project.dev/4.0.1/quality/#qual-02) | SHOULD | pass | no @SuppressWarnings("NullAway") in src/main |
| [QUAL-04](https://pauta-project.dev/4.0.1/quality/#qual-04) | SHOULD | n/a | no coverage floors quoted anywhere yet (applies with PLAT-12) |
| [QUAL-05](https://pauta-project.dev/4.0.1/quality/#qual-05) | MUST | pass | pure rules with plain tests: Zoom, FolderImages, NaturalOrder, Renaming, BookmarkList, Command.unavailable (CommandTest:229) |
| [QUAL-06](https://pauta-project.dev/4.0.1/quality/#qual-06) | MUST | pass | `src/test/java/com/mirada/image/ShrinkTest.java:14`: @Tag("fx") on every test that boots the toolkit |
| [QUAL-07](https://pauta-project.dev/4.0.1/quality/#qual-07) | SHOULD | pass | Thread.sleep only in HeadlessDriver, not a test surefire runs |
| [QUAL-09](https://pauta-project.dev/4.0.1/quality/#qual-09) | MUST | pass | `scripts/headless-run.sh`: with HeadlessDriver: the real window, robot keys and clicks |
| [QUAL-15](https://pauta-project.dev/4.0.1/quality/#qual-15) | SHOULD | pass | `src/test/java/com/mirada/ReleaseSupplyChainTest.java:259`: fails when a signing, notarization or attestation step moves or goes, or a tag could publish without its secrets |
| [PERF-02](https://pauta-project.dev/4.0.1/performance/#perf-02) | MUST | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:463`: background results return through Platform.runLater |
| [PERF-05](https://pauta-project.dev/4.0.1/performance/#perf-05) | MUST | pass | `src/main/java/com/mirada/image/ImageCache.java:126`: every supplyAsync has an executor; no Process subclass |
| [PERF-07](https://pauta-project.dev/4.0.1/performance/#perf-07) | MUST | n/a | nothing streams to the FX thread |
| [PERF-09](https://pauta-project.dev/4.0.1/performance/#perf-09) | SHOULD | pass | `src/main/java/com/mirada/ui/ImageViewport.java:280`: layout allocates nothing |
| [PERF-10](https://pauta-project.dev/4.0.1/performance/#perf-10) | SHOULD | pass | no applyCss(); pseudo-classes for state |
| [PERF-19](https://pauta-project.dev/4.0.1/performance/#perf-19) | SHOULD | pass | `scripts/measure-startup.sh`: median of n launches; Startup.mark phases |
| [KBD-05](https://pauta-project.dev/4.0.1/keyboard/#kbd-05) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:198`: C-S-p and M-x both open the palette (Cmd-S-p on macOS) |
| [KBD-06](https://pauta-project.dev/4.0.1/keyboard/#kbd-06) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:335`: fuzzy over title and keywords; rows show chords |
| [KBD-07](https://pauta-project.dev/4.0.1/keyboard/#kbd-07) | MUST | pass | `src/main/java/com/mirada/ui/CommandPalette.java:238`: in-scene overlay, no Popup |
| [KBD-08](https://pauta-project.dev/4.0.1/keyboard/#kbd-08) | SHOULD | pass | `src/main/java/com/mirada/ui/ViewerWindow.java:1150`: one picker for every list choice |
| [KBD-14](https://pauta-project.dev/4.0.1/keyboard/#kbd-14) | SHOULD | n/a | no multi-key prefixes |
| [WIN-02](https://pauta-project.dev/4.0.1/window-shell/#win-02) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:48`: light, dark and follow-the-system, live |
| [WIN-03](https://pauta-project.dev/4.0.1/window-shell/#win-03) | SHOULD | pass | `src/main/java/com/mirada/ui/TitleBar.java:193`: HeaderBar with StageStyle.EXTENDED; system menu bar on macOS (MacMenus.java:141) |
| [WIN-05](https://pauta-project.dev/4.0.1/window-shell/#win-05) | MUST | pass | `src/main/java/com/mirada/ui/Confirm.java:57`: one helper: question names the thing, button the action, Escape cancels; Move to Trash records an undo (Linux) or goes to the system trash |
| [WIN-10](https://pauta-project.dev/4.0.1/window-shell/#win-10) | MAY | n/a | no modes |
| [ICON-04](https://pauta-project.dev/4.0.1/icons-and-color/#icon-04) | SHOULD | n/a | ships no theme file; AtlantaFX Cupertino unchanged (src/main/java/com/mirada/ui/Theme.java:83) |
| [ICON-07](https://pauta-project.dev/4.0.1/icons-and-color/#icon-07) | MUST | pass | `src/main/resources/com/mirada/styles/mirada.css:128`: -state-key only in .root, a ladder() of -color-bg-default |
| [ICON-15](https://pauta-project.dev/4.0.1/icons-and-color/#icon-15) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:84`: Application.setUserAgentStylesheet, live |
| [ICON-16](https://pauta-project.dev/4.0.1/icons-and-color/#icon-16) | MUST | pass | `src/main/java/com/mirada/ui/Theme.java:54`: colorScheme listener re-applies; WindowsTest |
| [ICON-17](https://pauta-project.dev/4.0.1/icons-and-color/#icon-17) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:227`: unknown theme falls back to SYSTEM; SettingsTest:138 |
| [ICON-18](https://pauta-project.dev/4.0.1/icons-and-color/#icon-18) | SHOULD | pass | no semantic colours used as decoration |
| [ICON-19](https://pauta-project.dev/4.0.1/icons-and-color/#icon-19) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:125`: one glyph family ("icon") |
| [ICON-23](https://pauta-project.dev/4.0.1/icons-and-color/#icon-23) | SHOULD | n/a | draws no file-type glyphs (thumbnails) |
| [ICON-24](https://pauta-project.dev/4.0.1/icons-and-color/#icon-24) | MUST | pass | `src/main/java/com/mirada/ui/Icons.java:14`: hand-drawn glyphs, no fonts; NOTICE credits AtlantaFX |
| [TBAR-02](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-02) | MUST | pass | `src/main/java/com/mirada/ui/TitleBar.java:180`: header, OSD and badge buttons out of the Tab order |
| [TBAR-04](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-04) | SHOULD | pass | no separators; groups by spacing (src/main/java/com/mirada/ui/TitleBar.java:186) |
| [TBAR-05](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-05) | SHOULD | n/a | no group is ever hidden |
| [TBAR-07](https://pauta-project.dev/4.0.1/toolbars-and-tooltips/#tbar-07) | SHOULD | pass | `src/main/java/com/mirada/ui/TitleBar.java:177`: info toggle selected while the sidebar shows |
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
| [TABS-16](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-16) | SHOULD | n/a | no dockable tool windows |
| [TABS-17](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-17) | SHOULD | n/a | no stripe buttons |
| [TABS-18](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-18) | SHOULD | n/a | no floating panels |
| [TABS-19](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-19) | SHOULD | pass | `src/main/java/com/mirada/ui/InfoPanel.java:65`: panels never take focus |
| [TABS-20](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-20) | SHOULD | n/a | no stripe buttons |
| [TABS-21](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-21) | SHOULD | n/a | no bottom tool windows |
| [TABS-22](https://pauta-project.dev/4.0.1/tabs-and-panels/#tabs-22) | SHOULD | n/a | no stripe buttons |
| [MENU-01](https://pauta-project.dev/4.0.1/menus/#menu-01) | MUST | pass | `src/main/java/com/mirada/ui/MacMenus.java:38`: menus are lists of Command; titles, gates from Command |
| [MENU-04](https://pauta-project.dev/4.0.1/menus/#menu-04) | SHOULD | n/a | no Simple mode |
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
| [SET-01](https://pauta-project.dev/4.0.1/settings/#set-01) | MUST | pass | `src/main/java/com/mirada/ui/Command.java:163`: SETTINGS is Ctrl+, (Cmd+, on macOS), in the palette |
| [SET-04](https://pauta-project.dev/4.0.1/settings/#set-04) | MUST | pass | `src/main/java/com/mirada/ui/SettingsWindow.java:64`: every control writes through; no Save or Apply |
| [SET-07](https://pauta-project.dev/4.0.1/settings/#set-07) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:90`: one resolver: --config-dir/--dev, then MIRADA_CONFIG_DIR, then the default (~/.mirada; the platform folder on macOS and Windows) |
| [SET-11](https://pauta-project.dev/4.0.1/settings/#set-11) | MUST | pass | `src/main/java/com/mirada/settings/ConfigFile.java:44`: clamped on read; an undecodable file kept as .corrupt.bak, an unreadable one never written over; ConfigFileTest (malformed \u escape) |
| [SET-13](https://pauta-project.dev/4.0.1/settings/#set-13) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java`: no secrets anywhere |
| [SET-14](https://pauta-project.dev/4.0.1/settings/#set-14) | MUST | n/a | asks for no credential |
| [SET-15](https://pauta-project.dev/4.0.1/settings/#set-15) | MUST | n/a | no repository URL; the report's redaction is DIAG-06 |
| [SET-16](https://pauta-project.dev/4.0.1/settings/#set-16) | MUST | pass | no option weakens security |
| [SET-18](https://pauta-project.dev/4.0.1/settings/#set-18) | SHOULD | n/a | no Sync (SET-17) |
| [SET-19](https://pauta-project.dev/4.0.1/settings/#set-19) | SHOULD | n/a | no Sync (SET-17) |
| [SET-20](https://pauta-project.dev/4.0.1/settings/#set-20) | SHOULD | n/a | no Sync (SET-17) |
| [SET-21](https://pauta-project.dev/4.0.1/settings/#set-21) | SHOULD | n/a | no Sync (SET-17) |
| [DIAG-08](https://pauta-project.dev/4.0.1/diagnostics/#diag-08) | SHOULD | n/a | no user documents |
| [CLI-01](https://pauta-project.dev/4.0.1/command-line/#cli-01) | MUST | pass | `src/main/java/com/mirada/App.java:47`: --help and --version print fixed English (Launch.USAGE, AppInfo.versionText) and return before launch; LaunchTest |
| [CLI-02](https://pauta-project.dev/4.0.1/command-line/#cli-02) | MUST | pass | `src/main/java/com/mirada/Launch.java:63`: --config-dir and --dev parsed in main, before Settings.defaultFile reads anything; LaunchTest |
| [CLI-09](https://pauta-project.dev/4.0.1/command-line/#cli-09) | SHOULD | pass | `src/main/java/com/mirada/App.java:30`: java.awt.headless set as main's first statement |
| [INTL-03](https://pauta-project.dev/4.0.1/i18n/#intl-03) | SHOULD | n/a | no catalogs |
| [INTL-05](https://pauta-project.dev/4.0.1/i18n/#intl-05) | SHOULD | n/a | no CJK catalog |
| [INTL-06](https://pauta-project.dev/4.0.1/i18n/#intl-06) | SHOULD | n/a | no right-to-left catalog |
| [INTL-09](https://pauta-project.dev/4.0.1/i18n/#intl-09) | SHOULD | n/a | no catalogs |
| [INTL-10](https://pauta-project.dev/4.0.1/i18n/#intl-10) | SHOULD | n/a | no MessageFormat; numbers formatted with Locale.ROOT |
| [INTL-11](https://pauta-project.dev/4.0.1/i18n/#intl-11) | SHOULD | n/a | nothing translated |
| [PAL-01](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-01) | MUST | pass | `src/main/java/com/mirada/settings/Settings.java:198`: palette keys C-S-p M-x f1 (Cmd-S-p on macOS); one CommandPalette per window (ViewerWindow.java:267) |
| [PAL-03](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-03) | MUST | pass | `src/main/java/com/mirada/ui/CommandPalette.java:238`: card and scrim in the root StackPane; no Popup |
| [PAL-04](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-04) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:236`: opens empty, listing every command; from chord, header menu, context menu, Mac Help menu |
| [PAL-06](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-06) | SHOULD | pass | `src/main/java/com/mirada/ui/CommandPalette.java:273`: hides, then runs; disabled rows do nothing |
| [PAL-09](https://pauta-project.dev/4.0.1/templates/command-palette/#pal-09) | SHOULD | n/a | no keymaps to switch; chips re-read on every open (CommandPalette.java:232) |
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
| [ABOUT-03](https://pauta-project.dev/4.0.1/templates/about/#about-03) | SHOULD | pass | `src/main/java/com/mirada/ui/AboutWindow.java:90`: Copy details in the content, relabelled Copied |
| [ABOUT-04](https://pauta-project.dev/4.0.1/templates/about/#about-04) | SHOULD | n/a | no update check yet |
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
| [SYNC-02](https://pauta-project.dev/4.0.1/templates/settings-sync/#sync-02) | SHOULD | n/a | no Settings Sync |
| [REL-01](https://pauta-project.dev/4.0.1/release/#rel-01) | MUST | pass | `.github/workflows/release.yml:79`: five-target matrix, one leg each; pom.xml dist profile; native image inside jpackage's app image on 4 targets, jlink on macos-x64; dmg by hdiutil |
| [REL-03](https://pauta-project.dev/4.0.1/release/#rel-03) | MUST | pass | `jreleaser.yml:76`: JReleaser only creates the release (files.globs, checksums_sha256.txt and its signature); `.github/workflows/release.yml:546` runs it in the publish job after the build, sign-linux and asset-check jobs |
| [REL-06](https://pauta-project.dev/4.0.1/release/#rel-06) | SHOULD | pass | `.github/workflows/release.yml:593`: the bump job opens a pull request moving main to the next patch -SNAPSHOT, with CI dispatched on its branch; `pom.xml:9` is 0.4.0-SNAPSHOT |
| [REL-07](https://pauta-project.dev/4.0.1/release/#rel-07) | SHOULD | pass | `.github/workflows/release.yml:418`: check-assets fails before publishing on any expected file missing, or any unexpected one |
| [REL-15](https://pauta-project.dev/4.0.1/release/#rel-15) | MUST | pass | `.github/workflows/release.yml:149`: imports MACOS_CERT_P12 and MACOS_CERT_PASSWORD (`scripts/ci-signing-credentials.sh`), hardened runtime and entitlements (`scripts/sign-mac.sh`); `scripts/notarize-mac.sh:60` notarizes with APPLE_API_KEY_P8, APPLE_API_KEY_ID and APPLE_API_ISSUER, staples, runs spctl and launches the notarized app from the dmg (:87). The secrets are not set on the repository yet |
| [REL-16](https://pauta-project.dev/4.0.1/release/#rel-16) | MUST | pass | `.github/workflows/release.yml:318`: a sign-linux job reads LINUX_GPG_KEY and LINUX_GPG_PASSPHRASE, runs `scripts/sign-linux-packages.sh` (rpmsign, debsigs `_gpgorigin`, .asc beside every file) and `scripts/check-linux-signatures.sh` against the committed `packaging/linux/signing-key.asc`, which is attached as mirada-signing-key.asc |
| [REL-18](https://pauta-project.dev/4.0.1/release/#rel-18) | MUST | pass | `.github/workflows/release.yml:504`: attest-build-provenance on every file before JReleaser; `src/test/java/com/mirada/ReleaseSupplyChainTest.java:380` keeps the subject-path equal to jreleaser.yml's files.globs |
| [REL-21](https://pauta-project.dev/4.0.1/release/#rel-21) | MUST | pass | `.github/workflows/release.yml:26`: version tags and workflow_dispatch; preflight, five-target matrix, sign-linux, check-assets, attestations, JReleaser (`full-release --dry-run` on a dispatch, :546), winget, then the bump (:593) |
| [REL-28](https://pauta-project.dev/4.0.1/release/#rel-28) | MUST | pass | `scripts/ci-signing-credentials.sh:35`: a tag without the four macOS secrets fails before the keychain; sign-linux (`.github/workflows/release.yml:318`) and the checksum key (:519) fail a tag without LINUX_GPG_KEY; dry runs and forks sign with throwaway credentials |
| [REL-02](https://pauta-project.dev/4.0.1/release/#rel-02) | SHOULD | pass | `.github/workflows/release.yml:280`: renames every file to Mirada-<version>-<target>.<ext>; check-assets (:418) expects those names |
| [REL-04](https://pauta-project.dev/4.0.1/release/#rel-04) | SHOULD | pass | `scripts/release-notes.sh`: the version's CHANGELOG.md section under .github/release-notes-header.md, refused on a tag if empty; JReleaser's changelog.external (`jreleaser.yml:53`) |
| [REL-05](https://pauta-project.dev/4.0.1/release/#rel-05) | MUST | pass | `.github/workflows/release.yml:68`: preflight fails when tag != pom version, refuses a snapshot |
| [REL-08](https://pauta-project.dev/4.0.1/release/#rel-08) | SHOULD | pass | packaging/linux/Mirada.desktop:13 StartupWMClass=com.mirada.App |
| [REL-10](https://pauta-project.dev/4.0.1/release/#rel-10) | SHOULD | pass | `pom.xml`: aot-train runs scripts/aot-train.sh with -XX:AOTCacheOutput (the native image replaces it on four targets) |
| [REL-11](https://pauta-project.dev/4.0.1/release/#rel-11) | MUST | pass | `jreleaser.yml:76`: dmg, msi, deb, rpm, AppImage and tar.gz with their .asc, and checksums_sha256.txt (`scripts/build-installers.sh:6` makes them) |
| [REL-22](https://pauta-project.dev/4.0.1/release/#rel-22) | MUST | pass | `.github/workflows/ci.yml:42`: every external action pinned to a 40-character SHA with its version |
| [REL-23](https://pauta-project.dev/4.0.1/release/#rel-23) | MUST | pass | `.github/dependabot.yml:4`: github-actions and maven, weekly |
| [REL-24](https://pauta-project.dev/4.0.1/release/#rel-24) | MUST | pass | `.github/workflows/ci.yml:19`: contents: read at the top of all five workflows; write only in release and codecs publish jobs |
| [REL-25](https://pauta-project.dev/4.0.1/release/#rel-25) | SHOULD | pass | `.github/workflows/ci.yml:43`: setup-java 25 with cache: maven (GraalVM 25.4 for native legs) |
| [REL-26](https://pauta-project.dev/4.0.1/release/#rel-26) | MUST | pass | `.github/workflows/ci.yml:27`: timeout-minutes on every job; cancel-in-progress only for pull_request |
| [REL-29](https://pauta-project.dev/4.0.1/release/#rel-29) | MUST | pass | `.github/workflows/release.yml:170`: scripts/smoke-test.sh opens every fixture in each packaged app, the installed msi, deb and AppImage, and the notarized app from the dmg, before publishing |
