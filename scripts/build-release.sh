#!/usr/bin/env bash
# Build a release artifact (APK by default, AAB optional) for the given flavor.
# Signing happens only if app/android/keystore.properties exists (see docs/11).
# Usage: ./scripts/build-release.sh [dev|staging|prod] [apk|aab]   (default: prod apk)
set -euo pipefail

FLAVOR="${1:-prod}"
KIND="${2:-apk}"
case "$FLAVOR" in dev|staging|prod) ;; *) echo "flavor must be dev|staging|prod" >&2; exit 1;; esac
case "$KIND" in apk|aab) ;; *) echo "kind must be apk|aab" >&2; exit 1;; esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/../android"
cd "$ANDROID_DIR"

CAP="$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}"

if [[ -f keystore.properties ]]; then
  echo ">> keystore.properties found -> release will be SIGNED"
else
  echo ">> keystore.properties NOT found -> release will be UNSIGNED (output *-release-unsigned.*)"
fi

if [[ "$KIND" == "aab" ]]; then
  TASK="bundle${CAP}Release"
  OUT_DIR="app/build/outputs/bundle/${FLAVOR}Release"
  PATTERN='*.aab'
else
  TASK="assemble${CAP}Release"
  OUT_DIR="app/build/outputs/apk/$FLAVOR/release"
  PATTERN='*.apk'
fi

echo ">> ./gradlew $TASK"
./gradlew "$TASK" --console=plain

echo ">> Artifact:"
find "$OUT_DIR" -name "$PATTERN"
echo ">> Verify signature with: \$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs <apk>"
