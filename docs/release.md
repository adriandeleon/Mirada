# Releasing

How a version is tagged, built, signed and published. The installers it publishes are described in
[building-and-packaging.md](building-and-packaging.md).

## Steps

1. Set `<version>` in `pom.xml` without `-SNAPSHOT`, and turn `CHANGELOG.md`'s `## [Unreleased]`
   into the version with its date (`## [0.4.0] - 2026-11-12`, Keep a Changelog), with a new empty
   `## [Unreleased]` above it; the release notes are that section, under
   `.github/release-notes-header.md` (`scripts/release-notes.sh`).
2. Commit, then tag and push the tag: `git tag v0.1.0 && git push origin v0.1.0`.
3. The **Release** workflow (`.github/workflows/release.yml`, Pauta REL-21) runs, in order:
   - **preflight**: refuses a tag that disagrees with the pom, or a snapshot.
   - **build**, five legs: tests, packages, smoke-tests every app image, the installed msi, deb and
     AppImage, checks the MSI's size and the Linux packages (glibc floor included); on macOS signs
     with the Developer ID, then `scripts/notarize-mac.sh` signs the dmg, notarizes it with
     `notarytool` (an App Store Connect API key), staples it, checks it with `spctl` and launches
     the notarized app from the dmg on every fixture.
   - **sign-linux**: the deb and rpm of both Linux targets signed with the release key
     (`scripts/sign-linux-packages.sh`: rpmsign, a debsigs `_gpgorigin` origin signature, an `.asc`
     beside every file), on a runner that builds nothing, then checked against
     `packaging/linux/signing-key.asc` alone (`scripts/check-linux-signatures.sh`). A key other
     than the committed one is refused.
   - **check-assets**: every expected file present (`Mirada-<version>-<target>.<ext>`, each Linux
     file's `.asc`, `mirada-signing-key.asc`) and nothing else, and the changelog section; GitHub
     releases are immutable, so a missing file is caught here or never.
   - **publish**: build provenance for every file (`actions/attest-build-provenance`), then
     **JReleaser** (`jreleaser.yml`) creates the release, attaching the files with
     `checksums_sha256.txt` and its signature by the release key, `checksums_sha256.txt.asc`.
   - **winget**: the winget workflow writes the manifests from the published MSI
     (`scripts/winget-manifests.sh`), validates them, installs and uninstalls through them, and
     uploads them as `winget-manifests-<version>`.
   - **bump**: a pull request moving `main` to the next patch `-SNAPSHOT` (`chore/reopen-X.Y.Z`),
     with CI dispatched on its branch, as a pull request opened by `GITHUB_TOKEN` starts none.
4. Open a pull request to microsoft/winget-pkgs with those manifests (one version per PR, titled
   "New package: AdrianDeLeon.Mirada version 0.1.0", later "Update: AdrianDeLeon.Mirada to 0.2.0").
5. Merge the bump's pull request. If it could not be opened (the setting below is off), its summary
   links to the pushed branch.

Run the workflow by hand first for a dry run: it builds, signs, checks and attests everything, and
runs JReleaser with `--dry-run`, which publishes nothing; no winget, no bump. A re-run on a published
tag needs the release deleted first (`gh release delete vX.Y.Z`, which keeps the tag): JReleaser makes
it immutable.

**Signing** is set up from repository secrets, the family's names (Pauta REL-15, REL-16). **A tag
without them fails** (REL-28): the macOS legs in `scripts/ci-signing-credentials.sh`, before
anything builds, and sign-linux and publish without the release key. A dry run or a fork without
them signs with throwaway credentials made on the runner (a self-signed identity, a GPG key), so
every signing step and its check runs either way; nothing so signed is notarized or published.

| Secret | What |
|---|---|
| `MACOS_CERT_P12` | The Developer ID Application certificate and key (team 8JKSF797L8), a `.p12`, base64; exported with openssl's `-legacy` |
| `MACOS_CERT_PASSWORD` | The `.p12`'s password (set at the `gh secret set` prompt: a piped echo adds a newline) |
| `APPLE_API_KEY_P8` | An App Store Connect API key for `notarytool`, the `.p8`, base64 |
| `APPLE_API_KEY_ID` | Its 10-character key ID |
| `APPLE_API_ISSUER` | The issuer UUID, for a team key (none for an individual key) |
| `LINUX_GPG_KEY`, `LINUX_GPG_PASSPHRASE` | The family's release key ("Adrian De Leon (release signing)", `8486A7AF…C2C09C4A`), armored, and its passphrase |

Until 0.4.0 the workflow read `MACOS_CERTIFICATE`, `MACOS_CERTIFICATE_PASSWORD`,
`MACOS_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD` and
`LINUX_GPG_PRIVATE_KEY`; none of those is set on the repository (2026-11-08), and none is read now (the
identity is found in the `.p12`).

Repository settings the workflows rely on, which no file here can set:
- **Branch protection on `main`** requiring the checks `Format`, `Build & Test (ubuntu-latest)`,
  `Build & Test (macos-latest)` and `Build & Test (windows-latest)` (REL-19). Renaming a CI job
  renames its check: change the rule with it.
- **Settings → Actions → General → "Allow GitHub Actions to create and approve pull requests"**, for
  the bump job's pull request.

macOS: `scripts/sign-mac.sh` signs every Mach-O file in the bundle, then the bundle, with the
hardened runtime and `packaging/macos/entitlements.plist`. Windows is not signed yet (REL-17):
SmartScreen warns on first launch.

To try `jreleaser.yml` without a release: put files named as the release names them under
`artifacts/`, import a key into a throwaway `GNUPGHOME`, set `JRELEASER_GPG_KEYNAME`,
`JRELEASER_PROJECT_VERSION` and a dummy `JRELEASER_GITHUB_TOKEN`, and run JReleaser 1.26's
`jreleaser-tool-provider` jar with `full-release --dry-run --basedir "$PWD"`. The base directory
must be absolute: given `.`, its globs match nothing. The signing is COMMAND mode, gpg itself,
because MEMORY mode's signature (BouncyCastle) is a "PGP MESSAGE" that `gpg --verify` refuses.

`ReleaseSupplyChainTest` reads the workflows and scripts: it fails when a signing, notarization or
attestation step moves or goes, when a tag could publish without its secrets, and when the files
attested and the files JReleaser attaches stop being the same list. Change it with them.
