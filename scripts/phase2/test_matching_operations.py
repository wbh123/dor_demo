#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V6__version_matching_weight_schemes.sql"
SCHEMA = ROOT / "backend-java/docs/sql/schema.sql"
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-matching.yaml"
ROOT_OPENAPI = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
SCHEME_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingSchemeService.java"
MATCHING_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingService.java"
RECOMMENDATION_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java"
ADMIN_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminMatchingView.vue"
ROUTER = ROOT / "frontend/src/router/index.ts"
SHELL = ROOT / "frontend/src/layouts/AppShell.vue"
STYLES = ROOT / "frontend/src/matching-operations.css"
MAIN = ROOT / "frontend/src/main.ts"
SMOKE = ROOT / "scripts/e2e/phase2_matching_operations_smoke.py"
WORKFLOW = ROOT / ".github/workflows/phase1-ci.yml"


class MatchingOperationsPhaseTwoTest(unittest.TestCase):
    def test_v6_versions_matching_schemes_without_mutating_batch_references(self) -> None:
        self.assertTrue(MIGRATION.is_file())
        migration = MIGRATION.read_text(encoding="utf-8")
        for expected in (
            "ALTER TABLE matching_weight_scheme",
            "revision INT NOT NULL DEFAULT 1",
            "created_by BIGINT NULL",
            "change_reason VARCHAR(500)",
            "published_at DATETIME(3)",
            "DROP INDEX uk_weight_scheme_code",
            "UNIQUE KEY uk_weight_scheme_revision (scheme_code, revision)",
            "FOREIGN KEY (created_by) REFERENCES app_user(id)",
        ):
            self.assertIn(expected, migration)
        schema = SCHEMA.read_text(encoding="utf-8")
        self.assertIn("revision INT NOT NULL DEFAULT 1", schema)
        self.assertIn("uk_weight_scheme_revision", schema)
        self.assertIn("matching_weight_scheme_id BIGINT NOT NULL", schema)

    def test_openapi_exposes_scheme_list_create_and_revision_endpoints(self) -> None:
        self.assertTrue(OPENAPI.is_file())
        content = OPENAPI.read_text(encoding="utf-8")
        for expected in (
            "/api/v1/admin/matching-weight-schemes:",
            "/api/v1/admin/matching-weight-schemes/{schemeId}/revisions:",
            "operationId: listMatchingWeightSchemes",
            "operationId: createMatchingWeightScheme",
            "operationId: createMatchingWeightSchemeRevision",
            "MatchingWeightSchemeCreateRequest:",
            "MatchingWeightSchemeRevisionRequest:",
            "expectedVersion",
            "weights",
            "conflictRules",
            "activate",
            "reason",
        ):
            self.assertIn(expected, content)
        root = ROOT_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("admin/openapi-matching.yaml", root)

    def test_scheme_service_validates_and_creates_immutable_revisions(self) -> None:
        self.assertTrue(SCHEME_SERVICE.is_file())
        service = SCHEME_SERVICE.read_text(encoding="utf-8")
        for expected in (
            "class MatchingSchemeService",
            "SUPPORTED_WEIGHT_KEYS",
            "MATCHING_WEIGHT_KEY_INVALID",
            "MATCHING_WEIGHT_INVALID",
            "MATCHING_RULE_INVALID",
            "MATCHING_SCHEME_VERSION_CONFLICT",
            "ORDER BY revision DESC",
            "LIMIT 1",
            "FOR UPDATE",
            "int revision = (latestRevision == null ? 0 : latestRevision) + 1;",
            "UPDATE matching_weight_scheme SET enabled=0",
            "MATCHING_SCHEME_CREATE",
            "MATCHING_SCHEME_REVISION_CREATE",
            "@Transactional",
        ):
            self.assertIn(expected, service)
        self.assertNotIn("UPDATE matching_weight_scheme SET weights_json", service)
        controller = ADMIN_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("MatchingSchemeService", controller)
        self.assertIn("listMatchingWeightSchemes", controller)
        self.assertIn("createMatchingWeightSchemeRevision", controller)
        self.assertNotIn("@PostMapping", controller)

    def test_matching_algorithm_uses_batch_revision_and_public_explanations(self) -> None:
        matching = MATCHING_SERVICE.read_text(encoding="utf-8")
        scheme = SCHEME_SERVICE.read_text(encoding="utf-8")
        self.assertNotIn("private static final Map<String, Double> WEIGHTS", matching)
        for expected in (
            "roomScore(\n            long batchId",
            "schemeService.policyForBatch(batchId)",
            "recommendationReasons",
            "conflictReasons",
            "dimensionCount",
            "Math.max(0, Math.min(100",
        ):
            self.assertIn(expected, matching)
        for expected in (
            "matching_weight_scheme_id",
            "weights_json",
            "conflict_rules_json",
            "policyForBatch",
        ):
            self.assertIn(expected, scheme)
        self.assertTrue(RECOMMENDATION_SERVICE.is_file())
        recommendation = RECOMMENDATION_SERVICE.read_text(encoding="utf-8")
        self.assertIn("class StudentRoomRecommendationService", recommendation)
        self.assertIn("matchingService.roomScore(", recommendation)
        self.assertIn("batchId,", recommendation)
        self.assertIn("recommendationReasons", recommendation)
        self.assertIn("conflictReasons", recommendation)
        self.assertNotIn("questionnaire_answer", recommendation)
        student_controller = STUDENT_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("StudentRoomRecommendationService", student_controller)
        self.assertIn("recommendationService.rooms", student_controller)

    def test_admin_matching_page_uses_human_readable_inputs_and_revision_notice(self) -> None:
        self.assertTrue(ADMIN_VIEW.is_file())
        view = ADMIN_VIEW.read_text(encoding="utf-8")
        for expected in (
            "匹配规则",
            "权重方案修订",
            "入睡时间",
            "起床时间",
            "吸烟偏好冲突扣分",
            "修改原因",
            "创建新修订",
            "已有批次不会受影响",
            "/api/v1/admin/matching-weight-schemes",
        ):
            self.assertIn(expected, view)
        self.assertNotIn("JSON.stringify", view)
        router = ROUTER.read_text(encoding="utf-8")
        shell = SHELL.read_text(encoding="utf-8")
        self.assertIn("admin/matching", router)
        self.assertIn("匹配规则", shell)
        self.assertTrue(STYLES.is_file())
        styles = STYLES.read_text(encoding="utf-8")
        self.assertIn("@media (max-width: 640px)", styles)
        self.assertIn("./matching-operations.css", MAIN.read_text(encoding="utf-8"))

    def test_runtime_smoke_and_static_tests_are_connected_to_ci(self) -> None:
        self.assertTrue(SMOKE.is_file())
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m unittest scripts/phase2/test_matching_operations.py -v", workflow)
        self.assertIn("python scripts/e2e/phase2_matching_operations_smoke.py", workflow)
        smoke = SMOKE.read_text(encoding="utf-8")
        for expected in (
            "matching-weight-schemes",
            "MATCHING_SCHEME_VERSION_CONFLICT",
            "recommendationReasons",
            "conflictReasons",
            "MATCHING_SCHEME_REVISION_CREATE",
        ):
            self.assertIn(expected, smoke)


if __name__ == "__main__":
    unittest.main()
