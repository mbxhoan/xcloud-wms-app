#!/usr/bin/env bash
# Install an APK onto a connected device/PDA via adb (reinstall, keep data).
# Usage: ./scripts/install-pda.sh <path-to.apk> [adb-serial]
set -euo pipefail

APK="${1:-}"
SERIAL="${2:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "usage: $0 <path-to.apk> [adb-serial]" >&2
  exit 1
fi

ADB_ARGS=()
[[ -n "$SERIAL" ]] && ADB_ARGS=(-s "$SERIAL")

echo ">> devices:"
adb devices
echo ">> installing $APK"
adb "${ADB_ARGS[@]}" install -r "$APK"
echo ">> done. Launch the app manually and run the smoke checklist."
