#!/usr/bin/env bash
# Signs the Linux packages with the GPG key the release imported:
#
#   the .rpm      in place (rpmsign), so `rpm -K` and dnf check it once the key is imported
#   every file    a detached, armored signature beside it (<file>.asc): `gpg --verify <file>.asc`
#
# apt checks signatures on repositories, not on a downloaded .deb, so the deb is covered by its .asc
# and by the signed SHA256SUMS the release publishes. The public key goes on the release page.
#
# Usage: sign-linux.sh <installers-dir> <key-id-or-fingerprint>
set -euo pipefail
dir="${1:?installers directory required}"
key="${2:?signing key required}"

shopt -s nullglob
for rpm in "$dir"/*.rpm; do
    rpmsign --define "_gpg_name $key" --define "_gpg_sign_cmd_extra_args --batch --pinentry-mode loopback" \
        --addsign "$rpm"
    echo "[sign] $(basename "$rpm"): signed in place"
done
for file in "$dir"/*; do
    case "$file" in *.asc) continue ;; esac
    gpg --batch --yes --local-user "$key" --armor --detach-sign --output "$file.asc" "$file"
    gpg --batch --verify "$file.asc" "$file" 2> /dev/null
    echo "[sign] $(basename "$file").asc"
done
