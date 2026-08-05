#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

: "${ADMIN_TOKEN:?ADMIN_TOKEN is required}"
: "${LOAD_STUDENT_PASSWORD:?LOAD_STUDENT_PASSWORD is required}"
: "${TARGET_BATCH_ID:?TARGET_BATCH_ID is required}"
: "${TARGET_ROOM_ID:?TARGET_ROOM_ID is required}"
: "${TARGET_BED_ID:?TARGET_BED_ID is required}"

command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 2; }
command -v k6 >/dev/null || { echo "k6 is required" >&2; exit 2; }

export BASE_URL="${BASE_URL:-http://localhost:8080}"
export LOAD_FIXTURES="${LOAD_FIXTURES:-scripts/load/generated/selection-fixtures.json}"
export K6_SUMMARY="${K6_SUMMARY:-scripts/load/generated/k6-summary.json}"
export SSE_RESULT="${SSE_RESULT:-scripts/load/generated/sse-result.json}"
mkdir -p \
  "$(dirname "$LOAD_FIXTURES")" \
  "$(dirname "$K6_SUMMARY")" \
  "$(dirname "$SSE_RESULT")"

python3 scripts/load/prepare-load-fixtures.py

python3 scripts/load/sse-connections.py >scripts/load/generated/sse-connections.log 2>&1 &
SSE_PID=$!
cleanup() {
  if kill -0 "$SSE_PID" 2>/dev/null; then
    kill "$SSE_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

k6 run \
  --summary-export "$K6_SUMMARY" \
  scripts/load/k6-selection-flow.js

wait "$SSE_PID"
trap - EXIT
cat scripts/load/generated/sse-connections.log

python3 scripts/load/assert-selection-result.py
