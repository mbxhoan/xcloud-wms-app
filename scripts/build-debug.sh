#!/usr/bin/env bash
# Build a debug APK for the given flavor.
# Usage: ./scripts/build-debug.sh [dev|staging|prod]   (default: dev)
set -euo pipefail

FLAVOR="${1:-dev}"
case "$FLAVOR" in dev|staging|prod) ;; *) echo "flavor must be dev|staging|prod" >&2; exit 1;; esac

# Resolve android module dir (this script lives in app/scripts).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/../android"
cd "$ANDROID_DIR"

# Capitalize first letter -> Dev/Staging/Prod for the Gradle task name.
CAP="$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}"
TASK="assemble${CAP}Debug"

echo ">> ./gradlew $TASK"
./gradlew "$TASK" --console=plain

echo ">> APK:"
find "app/build/outputs/apk/$FLAVOR/debug" -name '*.apk'
