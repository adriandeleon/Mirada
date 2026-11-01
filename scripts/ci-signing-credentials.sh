#!/usr/bin/env bash
# The release workflow's signing credentials, set up for this runner and exported to later steps
# through GITHUB_ENV:
#
#   macOS   MIRADA_MAC_IDENTITY, the code-signing identity in a temporary keychain
#   Linux   MIRADA_GPG_KEY, the fingerprint of the key in the runner's keyring
#
# From the repository's secrets when they are set (MACOS_CERTIFICATE, a base64 .p12, with
# MACOS_CERTIFICATE_PASSWORD and MACOS_SIGNING_IDENTITY; LINUX_GPG_PRIVATE_KEY, armored, with
# LINUX_GPG_PASSPHRASE if it has one). Without them, on a run started by hand: throwaway
# credentials made here, so a dry run exercises every signing step and its checks; nothing signed
# with them is published. A tag without the secrets builds unsigned, as before.
set -euo pipefail
env_file="${GITHUB_ENV:?run inside GitHub Actions}"
dry_run=false
[ "${GITHUB_EVENT_NAME:-}" = workflow_dispatch ] && dry_run=true

case "$(uname -s)" in
Darwin)
    keychain="$RUNNER_TEMP/signing.keychain-db"
    password="$(openssl rand -hex 16)"
    p12="$RUNNER_TEMP/signing.p12"
    if [ -n "${MACOS_CERTIFICATE:-}" ]; then
        printf '%s' "$MACOS_CERTIFICATE" | base64 --decode > "$p12"
        p12_password="${MACOS_CERTIFICATE_PASSWORD:-}"
        identity="${MACOS_SIGNING_IDENTITY:?MACOS_SIGNING_IDENTITY must name the Developer ID}"
        kind="Developer ID"
    elif $dry_run; then
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
        kind="throwaway (dry run)"
    else
        echo "[signing] no Developer ID secrets: the macOS build stays ad hoc"
        exit 0
    fi
    security create-keychain -p "$password" "$keychain"
    security set-keychain-settings -lut 3600 "$keychain"
    security unlock-keychain -p "$password" "$keychain"
    security import "$p12" -k "$keychain" -P "$p12_password" -T /usr/bin/codesign
    security set-key-partition-list -S apple-tool:,apple: -s -k "$password" "$keychain" > /dev/null
    # Searched before the login keychain, so codesign finds the identity by name.
    security list-keychains -d user -s "$keychain" $(security list-keychains -d user | tr -d '"')
    rm -f "$p12"
    security find-identity -v -p codesigning "$keychain"
    echo "MIRADA_MAC_IDENTITY=$identity" >> "$env_file"
    echo "[signing] macOS: $kind identity \"$identity\""
    ;;
Linux)
    if [ -n "${LINUX_GPG_PRIVATE_KEY:-}" ]; then
        printf '%s\n' "$LINUX_GPG_PRIVATE_KEY" | gpg --batch --import
        kind="release key"
    elif $dry_run; then
        gpg --batch --passphrase '' --quick-generate-key "Mirada test signing <test@invalid>" rsa3072 sign 1d
        kind="throwaway key (dry run)"
    else
        echo "[signing] no GPG secret: the Linux packages stay unsigned"
        exit 0
    fi
    mkdir -p ~/.gnupg && chmod 700 ~/.gnupg
    echo "pinentry-mode loopback" >> ~/.gnupg/gpg.conf
    if [ -n "${LINUX_GPG_PASSPHRASE:-}" ]; then
        printf '%s' "$LINUX_GPG_PASSPHRASE" > ~/.gnupg/passphrase && chmod 600 ~/.gnupg/passphrase
        echo "passphrase-file $HOME/.gnupg/passphrase" >> ~/.gnupg/gpg.conf
    fi
    key=$(gpg --list-secret-keys --with-colons | awk -F: '/^fpr/ { print $10; exit }')
    echo "MIRADA_GPG_KEY=$key" >> "$env_file"
    echo "[signing] Linux: $kind $key"
    ;;
esac
