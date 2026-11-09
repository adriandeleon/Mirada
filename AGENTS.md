# Mirada

A fast, folder-oriented image viewer. JDK 25 + JavaFX 27 + Maven, modular (JPMS, module
`com.mirada`). The image half of the viewers plan; a Reader for PDF and comics may follow and share
code with it, but nothing is extracted yet.

## Pauta

Mirada is built to [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition [4.0.1](https://pauta-project.dev/4.0.1/).
Its rules: https://pauta-project.dev/4.0.1/llms.txt (every rule in one file:
https://pauta-project.dev/4.0.1/llms-full.txt). Where Mirada stands against each rule:
[docs/pauta-audit.md](docs/pauta-audit.md), written by the `pauta-audit` skill
([`.claude/skills/pauta-audit/`](.claude/skills/pauta-audit/SKILL.md)); `/pauta-audit KBD` audits one
prefix. Regenerate it, never edit it by hand.

- Before building a feature, read the rules on its page and copy from the code each rule's
  Reference names (Editora, `../Editora`, and the app template, `../javafx-app-template`).
- Cite a rule by its ID and edition: "KBD-07 (Pauta 4.0.1)".
- Read the numbered edition, never `latest/`. Moving to a newer edition is its own change:
  `pauta.version` in `pom.xml`, this section and the README change together (`EditionTest`). About
  and the version text read the number from the pom through `AppInfo`.

## Commands

- Run: `./mvnw javafx:run`, or on a file or folder: `./mvnw javafx:run -Djavafx.args=/path/to/photo.jpg`
- Test: `./mvnw test`
- Everything CI would check (tests, NullAway, the coverage floors, format): `./mvnw verify`; the floors and how the window is tested are in [docs/testing.md](docs/testing.md)
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

- Command line: `mirada --help`; `--config-dir <dir>` or `--dev` (`~/.mirada-dev`) keep settings
  and bookmarks apart from your own. One Mirada runs per config folder: a run on yours while
  your own Mirada is open hands it the file and exits; use `--dev` or `--new-instance`.

Run Maven from the project root, on JDK 25 or newer (the enforcer's floor); on 26 and later the
format check is skipped, so run `spotless:apply` on a 25. CI
(`.github/workflows/ci.yml`) runs `verify` on Linux, macOS and Windows for every push to `main`.

## How to work

- **Speed first**, then the keyboard, then the command palette, when they pull against each other
  ([docs/conventions.md](docs/conventions.md#priorities)).
- **One task, one branch, one worktree.** `git worktree add ../Mirada-<topic> -b <topic>`; never
  switch branches under another session's feet, never stash someone else's work.
- **When documentation and code disagree, the code wins**, and the documentation is fixed in the
  same change.
- **Docs change in the same pull request**, never as a follow-up: `CHANGELOG.md`, the guides in
  `docs/` and this file where they are touched, and the website's user docs (`../mirada-website`).
- **Before you push:** `./mvnw spotless:apply`, then `./mvnw verify`.

## Change checklist

- [ ] Every user action is a `Command` with a dotted id, a name, a description and a group, run
      through `ViewerWindow.run`; menus, buttons and the palette reach it by the same constant, and
      its keys are in the keymaps (`src/main/resources/com/mirada/keymaps/*.json`), never in code.
- [ ] Every string a user sees comes from `tr("key")` (`com.mirada.i18n.Messages`), its key in
      `messages.properties` and in each of the five translations (`MessagesTest`); a command's
      title is `command.<id>` and its palette line `command.<id>.desc`.
- [ ] A user-visible change has an entry under `## [Unreleased]` in `CHANGELOG.md` (Keep a
      Changelog), opening with a bold one-line summary for users.
- [ ] A new dependency has an entry in `NOTICE`, with its licence.
- [ ] Anything that removes, discards or replaces what the user made asks first through `Confirm`
      (the button is the action's verb, never OK), or can be undone.
- [ ] A rule is a static method with a plain test; a test that starts JavaFX is `@Tag("fx")`.
- [ ] New chrome is built after the first paint (`ViewerWindow.afterFirstPaint`), and start-up is
      measured again if it could have moved.
- [ ] A new package has a `package-info.java` with `@NullMarked`.
- [ ] What bit you goes into [docs/gotchas.md](docs/gotchas.md) (or packaging's list in
      [docs/building-and-packaging.md](docs/building-and-packaging.md)), in the same change.

## Documentation map

When documentation and code disagree, the code wins. [docs/README.md](docs/README.md) is the index.

| Read | Before |
|---|---|
| [docs/architecture.md](docs/architecture.md) | finding your way: the packages, the decoders, the native libraries |
| [docs/gotchas.md](docs/gotchas.md) | touching decoding, the viewport, the editor, the palette or a platform's glue |
| [docs/conventions.md](docs/conventions.md) | writing code: the priorities, rules as tested static methods, null annotations |
| [docs/testing.md](docs/testing.md) | writing a test, or looking at the UI headless |
| [docs/building-and-packaging.md](docs/building-and-packaging.md) | the codec bundle, the native image, the installers, start-up numbers |
| [docs/release.md](docs/release.md) | tagging a release, signing |
| [docs/decisions/](docs/decisions/README.md) | questioning a large choice (native image, libjpeg-turbo, codecs from source…) |
| [docs/reference/](docs/reference/README.md) | history: older measurements and experiments |
| [TODO.md](TODO.md) | what is known to be missing |

User documentation lives on the website, [mirada-project.dev/docs](https://mirada-project.dev/docs/)
(`../mirada-website`), never in this repository.
