#!/usr/bin/env bash
# The macOS signing identity for a release leg, in a temporary keychain, exported to later steps
# through GITHUB_ENV (MIRADA_MAC_IDENTITY, MIRADA_MAC_KEYCHAIN) and GITHUB_OUTPUT (notarize=true|false).
# The release workflow's "Import signing certificate (macOS)" step; Linux is signed in its own job
# (sign-linux, scripts/sign-linux-packages.sh), so there is nothing to do here on Linux or Windows.
#
# From the repository's secrets, the family's names (Pauta REL-15):
#   MACOS_CERT_P12        the Developer ID Application certificate and key, a .p12, base64
#   MACOS_CERT_PASSWORD   its password
#   APPLE_API_KEY_P8      an App Store Connect API key, the .p8, base64 (notarytool, notarize-mac.sh)
#   APPLE_API_KEY_ID      its 10-character key ID
# (APPLE_API_ISSUER, the issuer UUID, only for a team key; notarize-mac.sh reads it.)
#
# A tag push without all four fails here, before anything builds: a release is immutable, and an
# unsigned or unnotarized dmg is what this exists to stop (REL-28). Any other run (a dry run by
# hand, a fork) signs with a throwaway self-signed identity made on this runner, so every signing
# step and its checks still run; it cannot be notarized, and nothing signed with it is published.
#
# Usage: ci-signing-credentials.sh
set -euo pipefail
env_file="${GITHUB_ENV:?run inside GitHub Actions}"
out_file="${GITHUB_OUTPUT:?run inside GitHub Actions}"
tag=false
[ "${GITHUB_EVENT_NAME:-}" = push ] && [ "${GITHUB_REF_TYPE:-}" = tag ] && tag=true

if [ "$(uname -s)" != Darwin ]; then
    echo "[signing] nothing to import here: only macOS signs on the build legs"
    exit 0
fi

missing=()
for secret in MACOS_CERT_P12 MACOS_CERT_PASSWORD APPLE_API_KEY_P8 APPLE_API_KEY_ID; do
    [ -n "${!secret:-}" ] || missing+=("$secret")
done
if $tag && [ "${#missing[@]}" -gt 0 ]; then
    echo "::error title=macOS signing secrets missing::${missing[*]} not set; refusing to release a dmg that is not signed and notarized"
    exit 1
fi

keychain="$RUNNER_TEMP/mirada-signing.keychain-db"
password="$(openssl rand -hex 16)"
p12="$RUNNER_TEMP/mirada-signing.p12"
if [ -n "${MACOS_CERT_P12:-}" ] && [ -n "${MACOS_CERT_PASSWORD:-}" ]; then
    printf '%s' "$MACOS_CERT_P12" | base64 --decode > "$p12"
    p12_password="$MACOS_CERT_PASSWORD"
    kind="Developer ID"
else
    # A self-signed code-signing certificate, trusted on this runner only.
    identity="Mirada Test Signing"
    p12_password="test"
    # The system's openssl (LibreSSL), whose .p12 the keychain imports on every macOS.
    /usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=$identity" \
        -keyout "$RUNNER_TEMP/test.key" -out "$RUNNER_TEMP/test.pem" \
        -addext "keyUsage=critical,digitalSignature" -addext "extendedKeyUsage=critical,codeSigning" 2> /dev/null
    /usr/bin/openssl pkcs12 -export -inkey "$RUNNER_TEMP/test.key" -in "$RUNNER_TEMP/test.pem" \
        -out "$p12" -passout "pass:$p12_password" -name "$identity"
    sudo security add-trusted-cert -d -r trustRoot -p codeSign \
        -k /Library/Keychains/System.keychain "$RUNNER_TEMP/test.pem"
    rm -f "$RUNNER_TEMP/test.key"
    kind="throwaway (no secrets: ${missing[*]} unset)"
    echo "::warning title=macOS signed with a throwaway identity::no Developer ID secrets, so this run's dmg is signed by a certificate made for it and not notarized"
fi

security create-keychain -p "$password" "$keychain"
security set-keychain-settings -lut 3600 "$keychain" # no auto-lock mid-build
security unlock-keychain -p "$password" "$keychain"
echo "MIRADA_MAC_KEYCHAIN=$keychain" >> "$env_file"
if ! security import "$p12" -k "$keychain" -P "$p12_password" -T /usr/bin/codesign; then
    rm -f "$p12"
    echo "::error title=Certificate import failed::MACOS_CERT_PASSWORD does not open MACOS_CERT_P12 (set the secret at the gh prompt: a piped echo adds a newline), or the .p12 was exported without openssl's -legacy"
    exit 1
fi
rm -f "$p12"
# Lets codesign use the key without the GUI prompt a runner can never answer, and puts the keychain
# on the search list before the login keychain, so codesign finds the identity by name.
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$password" "$keychain" > /dev/null
security list-keychains -d user -s "$keychain" $(security list-keychains -d user | tr -d '"')

if [ "$kind" = "Developer ID" ]; then
    identity="$(security find-identity -v -p codesigning "$keychain" |
        awk -F'"' '/Developer ID Application/ { print $2; exit }')"
    if [ -z "$identity" ]; then
        security find-identity -v -p codesigning "$keychain"
        echo "::error title=No Developer ID identity::MACOS_CERT_P12 holds no valid Developer ID Application identity"
        exit 1
    fi
fi
security find-identity -v -p codesigning "$keychain"

notarize=false
[ -n "${APPLE_API_KEY_P8:-}" ] && [ -n "${APPLE_API_KEY_ID:-}" ] && [ "$kind" = "Developer ID" ] && notarize=true
echo "MIRADA_MAC_IDENTITY=$identity" >> "$env_file"
echo "notarize=$notarize" >> "$out_file"
echo "[signing] macOS: $kind identity \"$identity\"; notarize: $notarize"
