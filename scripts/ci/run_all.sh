#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT_DIR/scripts/ci/run_policy.sh"
bash "$ROOT_DIR/scripts/ci/run_contracts.sh"
bash "$ROOT_DIR/scripts/ci/run_backend.sh"
bash "$ROOT_DIR/scripts/ci/run_frontend.sh"
