#!/usr/bin/env bash
# Capture a logcat dump for bug reports. Filters out lines that look like they
# carry token/password/secrets so the report can be shared safely.
# Usage: ./scripts/collect-logcat.sh [output-file] [adb-serial]
set -euo pipefail

OUT="${1:-logcat-$(date +%Y%m%d-%H%M%S).txt}"
SERIAL="${2:-}"
ADB_ARGS=()
[[ -n "$SERIAL" ]] && ADB_ARGS=(-s "$SERIAL")

echo ">> dumping current logcat buffer to $OUT (secrets scrubbed)"
# -d dumps and exits. Drop lines mentioning sensitive keywords (case-insensitive).
adb "${ADB_ARGS[@]}" logcat -d \
  | grep -viE 'token|password|passwd|secret|anon[_-]?key|authorization|bearer' \
  > "$OUT"

echo ">> saved $OUT ($(wc -l < "$OUT" | tr -d ' ') lines)"
echo ">> REVIEW the file before sharing. Do NOT attach token/password/PII."
