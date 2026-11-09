#!/usr/bin/env bash
# Writes the winget manifests for a released MSI: the version, installer and default-locale files
# winget-pkgs takes, in the folder layout it wants (manifests/a/AdrianDeLeon/Mirada/<version>), for
# a pull request to microsoft/winget-pkgs. The installer URL is the GitHub release's, so the MSI
# must be the one published there: its SHA-256 goes into the manifest.
#
# The MSI's ProductCode comes from msitools' msiinfo where it is installed, else from Windows
# Installer itself (on Windows); winget matches an installed copy to the package by it, and by the
# upgrade code, which never changes (scripts/build-installers.sh).
#
# Usage: winget-manifests.sh <version> <msi> <out-dir>
set -euo pipefail

version="${1:?version required}"
msi="${2:?msi required}"
out="${3:?output directory required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$msi" ] || { echo "no msi at $msi" >&2; exit 1; }

id=AdrianDeLeon.Mirada
schema=1.12.0
repo=https://github.com/adriandeleon/Mirada
upgrade_code="{390E8E75-FB71-413F-9F7D-A70507695E4C}"

sha256=$(sha256sum "$msi" | cut -d' ' -f1 | tr '[:lower:]' '[:upper:]')
if command -v msiinfo >/dev/null 2>&1; then
    product_code=$(msiinfo export "$msi" Property | awk -F'\t' '$1 == "ProductCode" { print $2 }' | tr -d '\r')
elif command -v powershell.exe >/dev/null 2>&1; then
    product_code=$(MSI="$(cygpath -w "$msi")" SQL="SELECT Value FROM Property WHERE Property = 'ProductCode'" \
        powershell.exe -NoProfile -Command '
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $db = $installer.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $installer, @($env:MSI, 0))
        $view = $db.OpenView($env:SQL)
        $view.Execute()
        $record = $view.Fetch()
        $record.StringData(1)' | tr -d '\r')
else
    echo "no msiinfo (msitools) and not on Windows: cannot read the ProductCode" >&2
    exit 1
fi
[[ "$product_code" =~ ^\{[0-9A-Fa-f-]{36}\}$ ]] || { echo "not a ProductCode: '$product_code'" >&2; exit 1; }

# The file associations the MSI registers, from the list every installer reads.
extensions=$(awk '!/^#/ && NF { print "- " $1 }' "$root/packaging/file-types.txt")

dir="$out/manifests/a/AdrianDeLeon/Mirada/$version"
mkdir -p "$dir"
header="# Created by Mirada's scripts/winget-manifests.sh"

cat > "$dir/$id.yaml" <<YAML
$header
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.version.$schema.schema.json

PackageIdentifier: $id
PackageVersion: $version
DefaultLocale: en-US
ManifestType: version
ManifestVersion: $schema
YAML

cat > "$dir/$id.installer.yaml" <<YAML
$header
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.installer.$schema.schema.json

PackageIdentifier: $id
PackageVersion: $version
InstallerLocale: en-US
InstallerType: wix
Scope: user
InstallModes:
- interactive
- silent
- silentWithProgress
UpgradeBehavior: install
FileExtensions:
$extensions
ProductCode: '$product_code'
AppsAndFeaturesEntries:
- DisplayName: Mirada
  Publisher: Adrian De Leon
  ProductCode: '$product_code'
  UpgradeCode: '$upgrade_code'
Installers:
- Architecture: x64
  InstallerUrl: $repo/releases/download/v$version/$(basename "$msi")
  InstallerSha256: $sha256
ManifestType: installer
ManifestVersion: $schema
YAML

cat > "$dir/$id.locale.en-US.yaml" <<YAML
$header
# yaml-language-server: \$schema=https://aka.ms/winget-manifest.defaultLocale.$schema.schema.json

PackageIdentifier: $id
PackageVersion: $version
PackageLocale: en-US
Publisher: Adrian De Leon
PublisherUrl: https://github.com/adriandeleon
PublisherSupportUrl: $repo/issues
Author: Adrian De Leon
PackageName: Mirada
PackageUrl: $repo
License: MIT
LicenseUrl: $repo/blob/main/LICENSE
Copyright: Copyright (c) 2026 Adrián Arturo De León Saldivar
CopyrightUrl: $repo/blob/main/LICENSE
ShortDescription: A fast, folder-oriented image viewer
Description: |-
  Open one photo and the rest of its folder is already there, in the order you'd number it, one
  arrow key away. JPEG, PNG, GIF, BMP, WebP, TIFF, PSD, HEIC, AVIF and JPEG XL, turned upright and
  animated where the file is; crop, turn and flip, and save a copy that keeps its EXIF and colour
  profile. Everything is reachable from the keyboard and a command palette.
Moniker: mirada
Tags:
- image-viewer
- photo-viewer
- heic
- avif
- jpeg-xl
- webp
- exif
ReleaseNotesUrl: $repo/releases/tag/v$version
ManifestType: defaultLocale
ManifestVersion: $schema
YAML

echo "[winget] $dir"
ls "$dir"
