#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

npm ci --prefix "$ROOT_DIR/frontend" --no-audit --no-fund
npm run --prefix "$ROOT_DIR/frontend" generate:api
VITE_INSTITUTION_NAME="${VITE_INSTITUTION_NAME:-示例大学}" \
VITE_CAMPUS_NAME="${VITE_CAMPUS_NAME:-主校区}" \
  npm run --prefix "$ROOT_DIR/frontend" typecheck
VITE_INSTITUTION_NAME="${VITE_INSTITUTION_NAME:-示例大学}" \
VITE_CAMPUS_NAME="${VITE_CAMPUS_NAME:-主校区}" \
  npm run --prefix "$ROOT_DIR/frontend" build
node "$ROOT_DIR/scripts/ci/test_student_home_render.mjs"
node "$ROOT_DIR/scripts/ci/test_admin_data_render.mjs"
python "$ROOT_DIR/scripts/ci/test_welcome_brand_home_ui.py"
