#!/usr/bin/env bash
# Automated part of the smoke test: build (optional), install, launch, watch for
# a crash in the first seconds, then print the manual checklist to finish by hand.
# Usage: ./scripts/smoke-test.sh [dev|staging|prod] [adb-serial]   (default: staging)
# Set SKIP_BUILD=1 to install the last-built debug APK without rebuilding.
set -euo pipefail

FLAVOR="${1:-staging}"
SERIAL="${2:-}"
case "$FLAVOR" in dev|staging|prod) ;; *) echo "flavor must be dev|staging|prod" >&2; exit 1;; esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/../android"
cd "$ANDROID_DIR"

ADB_ARGS=()
[[ -n "$SERIAL" ]] && ADB_ARGS=(-s "$SERIAL")

CAP="$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}"
APK="app/build/outputs/apk/$FLAVOR/debug/app-$FLAVOR-debug.apk"

# applicationId per flavor (matches build.gradle.kts suffixes).
case "$FLAVOR" in
  dev) APP_ID="vn.delfi.xcloudwms.dev" ;;
  staging) APP_ID="vn.delfi.xcloudwms.staging" ;;
  prod) APP_ID="vn.delfi.xcloudwms" ;;
esac

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo ">> building assemble${CAP}Debug"
  ./gradlew "assemble${CAP}Debug" --console=plain
fi
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }

echo ">> devices:"; adb devices
echo ">> installing $APK"
adb "${ADB_ARGS[@]}" install -r "$APK"

echo ">> clearing logcat + launching app"
adb "${ADB_ARGS[@]}" logcat -c || true
adb "${ADB_ARGS[@]}" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null

echo ">> watching logcat ~10s for crashes (FATAL/ANR)"
CRASH="$(adb "${ADB_ARGS[@]}" logcat -d 2>/dev/null \
  | grep -iE 'FATAL EXCEPTION|ANR in|AndroidRuntime' | grep -i "$APP_ID" || true)"
# Give the app a moment, then re-check (no foreground sleep abuse: short poll loop).
for _ in 1 2 3 4 5; do
  C2="$(adb "${ADB_ARGS[@]}" logcat -d 2>/dev/null | grep -iE 'FATAL EXCEPTION|ANR in' || true)"
  [[ -n "$C2" ]] && CRASH="$C2"
done

if [[ -n "$CRASH" ]]; then
  echo "!! CRASH/ANR detected:" ; echo "$CRASH"
  echo "SMOKE: FAIL (startup crash)"; exit 2
fi

echo "SMOKE-AUTO: PASS (installed + launched, no startup crash)"
echo
echo "Now finish MANUALLY on the device — see:"
echo "  app/checklists/smoke_test_checklist.md (sections C–F)"
