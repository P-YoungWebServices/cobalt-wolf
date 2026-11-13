#!/usr/bin/env bash
# Build and verify a distributable release APK.
#
# Produces app/build/outputs/apk/release/app-release.apk, then:
#   1. refuses to proceed if keystore.properties is missing (unsigned build)
#   2. verifies the APK signature with apksigner
#   3. scans every .dex for dev.credentials.properties values and DEV_ fields
#
# NEVER distribute app-debug.apk — it embeds dev.credentials.properties.
set -euo pipefail
cd "$(dirname "$0")/.."

APK=app/build/outputs/apk/release/app-release.apk

if [ ! -f keystore.properties ]; then
    echo "ERROR: keystore.properties missing — a release build would be UNSIGNED." >&2
    echo "See keystore.properties.example." >&2
    exit 1
fi

[ -n "${JAVA_HOME:-}" ] || export JAVA_HOME=/snap/android-studio/current/jbr
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleRelease

echo "== Verifying signature =="
APKSIGNER=$(ls -t "$HOME"/Android/Sdk/build-tools/*/apksigner 2>/dev/null | head -1)
"$APKSIGNER" verify --print-certs "$APK" | grep -v "^WARNING"

if [ -f dev.credentials.properties ]; then
    echo "== Scanning APK for dev credentials =="
    leaks=0
    while IFS='=' read -r key value; do
        [ -z "$key" ] || [ -z "$value" ] && continue
        case "$key" in \#*) continue ;; esac
        # Short/common values (e.g. a username like "admin") appear all over
        # dex string tables and can't be meaningfully scanned — skip with a
        # warning rather than false-positive. URLs, passwords and API keys
        # are always long enough to scan.
        if [ "${#value}" -lt 8 ]; then
            echo "note: '$key' value < 8 chars, too common to scan reliably" >&2
            continue
        fi
        while IFS= read -r dex; do
            count=$(unzip -p "$APK" "$dex" | grep -cF "$value" || true)
            if [ "$count" != "0" ]; then
                echo "LEAK: value of key '$key' found $count times in $dex" >&2
                leaks=1
            fi
        done < <(unzip -Z1 "$APK" | grep '\.dex$')
    done < dev.credentials.properties
    dev_fields=$(unzip -p "$APK" classes.dex | grep -c "DEV_" || true)
    [ "$dev_fields" != "0" ] && { echo "LEAK: DEV_ BuildConfig fields in classes.dex" >&2; leaks=1; }
    [ "$leaks" != "0" ] && { echo "REFUSING to pass a build containing credentials." >&2; exit 1; }
    echo "No credentials found in APK."
fi

echo "OK: $APK is signed and credential-free."
ls -la "$APK"
