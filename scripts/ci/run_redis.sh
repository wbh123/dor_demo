#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PYTHONPATH="$ROOT_DIR/scripts/ci" \
  python3 -m unittest \
    "$ROOT_DIR/scripts/ci/test_redis_database_reset.py" \
    "$ROOT_DIR/scripts/ci/test_reset_scenario.py" \
    -v
