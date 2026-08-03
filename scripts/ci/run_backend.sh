#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mvn -f "$ROOT_DIR/backend-java/pom.xml" \
  --batch-mode \
  --no-transfer-progress \
  clean verify
