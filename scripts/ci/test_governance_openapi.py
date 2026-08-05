#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
root = (ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml").read_text(encoding="utf-8")
spec = (ROOT / "backend-java/model/src/main/resources/admin/openapi-governance.yaml").read_text(encoding="utf-8")

paths = (
    "/api/v1/admin/governance/audit/query",
    "/api/v1/admin/governance/audit/export",
    "/api/v1/admin/governance/exports",
    "/api/v1/admin/governance/notifications/templates",
    "/api/v1/admin/governance/notifications/preflight",
    "/api/v1/admin/governance/notifications/schedule",
    "/api/v1/admin/governance/notifications/status",
    "/api/v1/admin/governance/analytics/definitions",
    "/api/v1/admin/governance/analytics/dashboard",
    "/api/v1/admin/governance/analytics/comparison",
    "/api/v1/admin/governance/analytics/trend",
    "/api/v1/admin/governance/reports/metadata",
    "/api/v1/admin/governance/reports/templates",
    "/api/v1/admin/governance/reports/export",
    "/api/v1/admin/governance/retention/policy",
    "/api/v1/admin/governance/retention/statistics",
    "/api/v1/admin/governance/retention/simulate",
    "/api/v1/admin/governance/retention/preflight",
)
missing = [path for path in paths if path not in root or path not in spec]
if missing:
    raise AssertionError("governance OpenAPI paths missing: " + ", ".join(missing))
for token in (
    "additionalProperties: false",
    "AuditQueryRequest",
    "RecipientCriteria",
    "ReportDefinition",
    "minLength: 2",
    "maximum: 200",
):
    if token not in spec:
        raise AssertionError(f"governance OpenAPI validation missing: {token}")
if "SQL" in spec or "sql" in spec:
    raise AssertionError("governance OpenAPI must not expose arbitrary SQL input")
print("Governance root OpenAPI contract passed")
