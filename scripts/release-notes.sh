#!/usr/bin/env bash
# Writes the release notes JReleaser publishes (jreleaser.yml, changelog.external): how to install
# and check a download (.github/release-notes-header.md), then this version's section of CHANGELOG.md
# (Pauta REL-04). The javafx-app-template's script.
#
# A section starts at a "## " heading naming the version (`## [1.2.3] - 2026-11-06`, `## 1.2.3`,
# `## v1.2.3`) and ends at the next "## " heading. Fails when there is none, so a release cannot go
# out with an empty changelog; --allow-missing (dry runs) falls back to the Unreleased section.
#
# Usage: release-notes.sh <version> <out-file> [--allow-missing]
set -euo pipefail

version="${1:?version required}"
out="${2:?output file required}"
allow_missing="${3:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
changelog="$root/CHANGELOG.md"
[ -f "$changelog" ] || { echo "::error::no CHANGELOG.md"; exit 1; }

section() {
    awk -v v="$1" '
        /^## / {
            if (inside) exit
            h = $0; sub(/^## +\[?v?/, "", h)
            if (index(h, v) == 1 && substr(h, length(v) + 1) ~ /^($|[] ])/) { inside = 1; next }
        }
        inside { print }
    ' "$changelog"
}

body="$(section "$version")"
if [ -z "${body//[[:space:]]/}" ]; then
    if [ "$allow_missing" != "--allow-missing" ]; then
        echo "::error::CHANGELOG.md has no section for $version; add \"## [$version] - $(date +%F)\" and re-tag"
        exit 1
    fi
    echo "::warning::CHANGELOG.md has no section for $version; using Unreleased"
    body="$(section Unreleased)"
fi

mkdir -p "$(dirname "$out")"
{
    cat "$root/.github/release-notes-header.md"
    echo "## Changes"
    echo
    printf '%s\n' "$body"
} > "$out"
echo "release notes for $version written to $out"
