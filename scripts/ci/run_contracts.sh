#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 "$ROOT_DIR/scripts/ci/test_openapi_relative_refs.py"
python3 "$ROOT_DIR/scripts/ci/validate_system_contracts_v25.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_residency_building_alias.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_phase2_phase3_operations.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_import_recovery_anomaly_workflows.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_welcome_brand_room_exchange.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_waitlist_core.py" "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/ci/test_v25_integrated_workflow.py" "$ROOT_DIR"
