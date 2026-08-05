#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

npm ci --prefix "$ROOT_DIR/frontend" --no-audit --no-fund
VITE_INSTITUTION_NAME="${VITE_INSTITUTION_NAME:-示例大学}" \
VITE_CAMPUS_NAME="${VITE_CAMPUS_NAME:-示例校区}" \
  npm run --prefix "$ROOT_DIR/frontend" build
node "$ROOT_DIR/scripts/ci/test_student_home_render.mjs"
