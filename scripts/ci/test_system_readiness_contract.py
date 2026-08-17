#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required readiness file: {path}")
    return target.read_text(encoding="utf-8")


def main() -> int:
    spec = read("backend-java/model/src/main/resources/admin/openapi-system-readiness.yaml")
    assert "/api/v1/admin/system-readiness:" in spec
    assert "operationId: getSystemReadiness" in spec
    for token in (
        "READY_WITH_WARNINGS",
        "BLOCKED",
        "ReadinessCheckResult",
        "suggestedAction",
        "actionRoute",
        "checkedAt",
    ):
        assert token in spec, f"OpenAPI readiness contract missing {token}"

    model_pom = read("backend-java/model/pom.xml")
    assert "openapi-system-readiness.yaml" in model_pom, "readiness contract must participate in OpenAPI generation"
    assert "generated-sources/openapi-readiness" in model_pom, "readiness generated sources must be compiled"

    controller = read("backend-java/server/src/main/java/com/wust/dormitory/readiness/SystemReadinessController.java")
    assert "SystemReadinessService" in controller
    assert "SecurityUsers.requireAdmin()" in controller
    assert "implements SystemReadinessApi" in controller, "controller must implement the generated OpenAPI interface"

    service = read("backend-java/server/src/main/java/com/wust/dormitory/readiness/SystemReadinessService.java")
    assert "ReadinessChecker" in service
    assert "CHECK_FAILED" in service
    assert "READY_WITH_WARNINGS" in service

    checker_names = (
        "InfrastructureReadinessChecker",
        "ResourceReadinessChecker",
        "StudentReadinessChecker",
        "BatchReadinessChecker",
        "AuthorizationReadinessChecker",
        "MobileReadinessChecker",
        "LabelReadinessChecker",
    )
    for checker in checker_names:
        read(f"backend-java/server/src/main/java/com/wust/dormitory/readiness/{checker}.java")

    mapper_xml = read("backend-java/server/src/main/resources/mapper/readiness/SystemReadinessMapper.xml")
    assert "SELECT *" not in mapper_xml.upper()
    assert " LIMIT " in mapper_xml.upper(), "diagnostic sample queries must be bounded"
    assert "rule_template_id AS ruleTemplateId" in mapper_xml

    batch = read("backend-java/server/src/main/java/com/wust/dormitory/readiness/BatchReadinessChecker.java")
    for token in (
        "BatchScopeService",
        "requireReady(batchId)",
        "BatchRuleTemplateService",
        "resolveForBatch(ruleTemplateId)",
        "RULE_TEMPLATE_NOT_BOUND",
        "BatchRoomLockService",
        "preview(batchId)",
        "MatchingSchemeService",
        "policyForBatch(batchId)",
    ):
        assert token in batch, f"batch readiness missing existing validator reuse: {token}"
    for forbidden in ("acquire(batchId)", "changeStatus(", "rebuild(batchId)"):
        assert forbidden not in batch, f"batch readiness must remain read-only: {forbidden}"

    frontend = read("frontend/src/views/admin/AdminSystemReadinessView.vue")
    for token in ("上线体检", "重新检查", "去处理", "actionRoute", "BLOCKED", "READY_WITH_WARNINGS"):
        assert token in frontend, f"frontend readiness view missing {token}"
    assert "systemReadinessSchema" in frontend

    package_json = read("frontend/package.json")
    assert "openapi-system-readiness.yaml" in package_json
    assert "systemReadinessSchema.d.ts" in package_json

    router = read("frontend/src/router/index.ts")
    assert "admin/system-readiness" in router
    assert "AdminSystemReadinessView.vue" in router

    print("system readiness cross-layer contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
