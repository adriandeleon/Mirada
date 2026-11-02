#!/usr/bin/env bash
# Signs the dmg with the Developer ID that signed the app inside it, has Apple notarize it, and
# staples the ticket on, so it opens on any Mac without "Open Anyway", online or not.
#
# Needs MIRADA_MAC_IDENTITY (as sign-mac.sh) and, for notarizing, an app-specific password:
#   APPLE_ID, APPLE_TEAM_ID, APPLE_APP_PASSWORD
# Without the Apple account it signs and stops; with an ad hoc identity it does nothing, as an ad hoc
# signature on a dmg means nothing to Gatekeeper.
#
# Usage: notarize-mac.sh <Mirada.dmg>
set -euo pipefail
dmg="${1:?dmg required}"
identity="${MIRADA_MAC_IDENTITY:--}"
if [ "$identity" = "-" ]; then
    echo "[notarize] no Developer ID: $dmg stays unsigned"
    exit 0
fi
# Apple's timestamp server does not always answer; tried again, as sign-mac.sh does.
for attempt in 1 2 3 4 5; do
    codesign --force --sign "$identity" --timestamp "$dmg" && break
    [ "$attempt" -lt 5 ] || exit 1
    echo "[notarize] codesign failed, trying again ($attempt of 5)" >&2
    sleep 5
done
codesign --verify --verbose=2 "$dmg"
if [ -z "${APPLE_ID:-}" ] || [ -z "${APPLE_TEAM_ID:-}" ] || [ -z "${APPLE_APP_PASSWORD:-}" ]; then
    echo "[notarize] signed; no Apple account given, so not notarized"
    exit 0
fi
xcrun notarytool submit "$dmg" --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" \
    --password "$APPLE_APP_PASSWORD" --wait --timeout 30m
xcrun stapler staple "$dmg"
xcrun stapler validate "$dmg"
spctl --assess --type open --context context:primary-signature --verbose=2 "$dmg"
echo "[notarize] $dmg notarized and stapled"
