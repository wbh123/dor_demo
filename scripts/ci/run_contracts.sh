#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 "$ROOT_DIR/scripts/ci/validate_system_contracts.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_residency_building_alias.py" "$ROOT_DIR"
