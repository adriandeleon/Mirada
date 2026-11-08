## Install

**macOS**: open the DMG (`macos-arm64` for Apple silicon, `macos-x64` for Intel) and drag Mirada to
Applications. The app is signed with a Developer ID and notarized by Apple.

**Windows**: run the `windows-x64` MSI. It is not Authenticode-signed yet, so SmartScreen warns
about an unknown publisher: **More info → Run anyway**.

**Linux**: the DEB (`sudo apt install ./Mirada-*.deb`) or the RPM (`sudo dnf install ./Mirada-*.rpm`)
for your architecture (`linux-x64` or `linux-arm64`). Without root, the `AppImage` runs as it is
(`chmod +x` it first), and the `tar.gz` runs from wherever it is unpacked: `bin/Mirada`.

**Check a download**: `gpg --import mirada-signing-key.asc && gpg --verify checksums_sha256.txt.asc`,
then `sha256sum --check --ignore-missing checksums_sha256.txt`. Each Linux file also has its own
signature (`gpg --verify <file>.asc`), and every download its build provenance:
`gh attestation verify <file> --repo adriandeleon/Mirada`.

