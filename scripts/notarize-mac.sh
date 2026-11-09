#!/usr/bin/env bash
# Signs the dmg with the identity that signed the app inside it, has Apple notarize it, staples the
# ticket, checks the result as Gatekeeper will, and then launches the notarized app from the dmg
# once (Pauta REL-15): the hardened runtime, the entitlements and every library the first image
# needs, as a user's Mac will run them.
#
# Needs MIRADA_MAC_IDENTITY (ci-signing-credentials.sh) and, to notarize, an App Store Connect API key:
#   APPLE_API_KEY_P8    the .p8, base64
#   APPLE_API_KEY_ID    its 10-character key ID
#   APPLE_API_ISSUER    the issuer UUID, for a team key (an individual key has none)
# MIRADA_NOTARIZE=true requires them. Without it (a dry run with the throwaway identity, which Apple
# would refuse) the dmg is signed, the app in it checked and launched, and nothing is notarized. A
# tag never gets here without the key: ci-signing-credentials.sh fails it first.
#
# Usage: notarize-mac.sh <Mirada.dmg> <folder-of-images>
set -euo pipefail
dmg="${1:?dmg required}"
images="${2:?folder of images required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
identity="${MIRADA_MAC_IDENTITY:?MIRADA_MAC_IDENTITY must name the signing identity}"
notarize="${MIRADA_NOTARIZE:-false}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
mnt="$(mktemp -d)"
detach() { hdiutil detach "$mnt" > /dev/null 2>&1 || true; }
trap detach EXIT

# The app inside must still carry our signature, intact: anything the dmg wrap changed in the bundle
# breaks the seal. Seconds to check here; Apple takes minutes to say only "invalid".
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$dmg" > /dev/null
codesign --display --verbose=2 "$mnt"/Mirada.app 2>&1 | grep -qxF "Authority=$identity" ||
    { codesign --display --verbose=2 "$mnt"/Mirada.app || true
      echo "::error::the app inside $(basename "$dmg") is not signed by $identity"; exit 1; }
codesign --verify --deep --strict --verbose=2 "$mnt"/Mirada.app
detach

# Apple's timestamp server does not always answer; tried again, as sign-mac.sh does.
for attempt in 1 2 3 4 5; do
    codesign --force --sign "$identity" --timestamp "$dmg" && break
    [ "$attempt" -lt 5 ] || exit 1
    echo "[notarize] codesign failed, trying again ($attempt of 5)" >&2
    sleep 5
done
codesign --verify --verbose=2 "$dmg"

if [ "$notarize" = true ]; then
    # Easy to swap when copying them off App Store Connect: the issuer is the UUID above the key
    # list, the key ID the 10 characters in the key's row. Said here before Apple says "401".
    uuid='^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$'
    if [ -n "${APPLE_API_ISSUER:-}" ] && ! [[ "$APPLE_API_ISSUER" =~ $uuid ]]; then
        echo "::error::APPLE_API_ISSUER must be the Issuer ID, the UUID shown above the team key list"; exit 1
    fi
    [[ "${APPLE_API_KEY_ID:-}" =~ ^[A-Z0-9]{10}$ ]] ||
        { echo "::error::APPLE_API_KEY_ID must be the 10-character Key ID"; exit 1; }
    [ -n "${APPLE_API_KEY_P8:-}" ] || { echo "::error::APPLE_API_KEY_P8 is not set"; exit 1; }
    key="${RUNNER_TEMP:-$(mktemp -d)}/AuthKey_${APPLE_API_KEY_ID}.p8"
    printf '%s' "$APPLE_API_KEY_P8" | base64 --decode > "$key"
    auth=(--key "$key" --key-id "$APPLE_API_KEY_ID")
    [ -n "${APPLE_API_ISSUER:-}" ] && auth+=(--issuer "$APPLE_API_ISSUER")

    out="$(xcrun notarytool submit "$dmg" "${auth[@]}" --wait --timeout 30m --output-format json)" || true
    echo "$out"
    id="$(printf '%s' "$out" | jq -r '.id // empty')"
    status="$(printf '%s' "$out" | jq -r '.status // empty')"
    # Apple's log names each file it objected to; printed whether or not it accepted.
    [ -n "$id" ] && { xcrun notarytool log "$id" "${auth[@]}" || true; }
    rm -f "$key"
    [ "$status" = Accepted ] ||
        { echo "::error::notarization returned '${status:-no result}' for $(basename "$dmg"); Apple's log is above"; exit 1; }

    # The ticket can take a moment to reach Apple's CDN, and the fetch can drop: tried again.
    for attempt in 1 2 3 4 5; do
        xcrun stapler staple "$dmg" && break
        [ "$attempt" = 5 ] && { echo "::error::notarized, but the ticket could not be stapled"; exit 1; }
        sleep 20
    done
    xcrun stapler validate "$dmg"
    spctl --assess --type open --context context:primary-signature --verbose=2 "$dmg"
fi

hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$dmg" > /dev/null
app="$mnt/Mirada.app"
codesign --verify --deep --strict --verbose=2 "$app"
if [ "$notarize" = true ]; then
    spctl --assess --type execute --verbose=2 "$app"
fi
# Launch the app itself, from the dmg, as a user's Mac runs it: every image in the folder must show.
bash "$root/scripts/smoke-test.sh" "$app/Contents/MacOS/Mirada" "$images"

if [ "$notarize" = true ]; then
    echo "[notarize] $(basename "$dmg") signed, notarized, stapled and launched"
    echo "- $(basename "$dmg"): signed by $identity, notarized, stapled and launched" >> "$summary"
else
    echo "[notarize] $(basename "$dmg") signed by $identity and launched; not notarized"
    echo "- $(basename "$dmg"): signed by $identity (not notarized) and launched" >> "$summary"
fi
