# Contributing to Mirada

Thank you for helping. A bug report is easiest with **About → Copy details** pasted into the
[bug report form](https://github.com/adriandeleon/Mirada/issues/new?template=bug_report.yml); if it is
about one picture, say what kind of file it is, and attach it if you can share it.

## Build, test, format

You need **JDK 25** (Temurin is what CI uses); the Maven Wrapper fetches Maven. Run from the
repository root.

```bash
./mvnw javafx:run          # run from source; add -Djavafx.args=/path/to/photo.jpg to open one
./mvnw test                # the tests
./mvnw -DexcludedGroups=fx test   # the pure tests only, without starting JavaFX
./mvnw spotless:apply      # format (Palantir Java Format); CI fails unformatted code
./mvnw verify              # what CI runs: NullAway, every test, the format check
```

`./mvnw javafx:run` uses your real settings folder. For experiments, launch with
`--config-dir /tmp/scratch` (or `--dev`, which keeps them in `~/.mirada-dev`).

- Put a rule in a pure class and test it there; tag a test that starts JavaFX `@Tag("fx")`.
- Every package is `@NullMarked`; NullAway fails the build on a possible null. Never suppress it.
- [AGENTS.md](AGENTS.md) has the commands for packaging, the codec bundle and start-up timing.
  [docs/](docs/README.md) explains the rest: [architecture](docs/architecture.md),
  [conventions](docs/conventions.md), [testing](docs/testing.md), [gotchas](docs/gotchas.md),
  [building and packaging](docs/building-and-packaging.md) and [releasing](docs/release.md).

## Pull requests

1. Branch from `main`, one change per branch.
2. Keep a change whole in one pull request: its tests, and **its docs in the same pull request** —
   an entry under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) opening with a bold one-line
   summary for users, the guides in `docs/` it touches, and the website's user docs
   ([mirada-website](https://github.com/adriandeleon/mirada-website)) when users see it.
3. `./mvnw verify` passes locally.
4. CI runs `verify` on Linux, macOS and Windows and packages the app; it must pass.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
