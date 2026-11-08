# Releasing

How a version is tagged, built, signed and published. The installers it publishes are described in
[building-and-packaging.md](building-and-packaging.md).

## Steps

1. Set `<version>` in `pom.xml` without `-SNAPSHOT`, and turn `CHANGELOG.md`'s `## [Unreleased]`
   into the version with its date (`## [0.4.0] - 2026-11-12`, Keep a Changelog), with a new empty
   `## [Unreleased]` above it; the release notes are that section.
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
