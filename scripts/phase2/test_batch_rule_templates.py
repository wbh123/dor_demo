from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V10__add_batch_rule_templates.sql"
SCHEMA = ROOT / "backend-java/docs/sql/schema.sql"
DICTIONARY = ROOT / "backend-java/docs/database-dictionary.md"
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-batch-rule-template.yaml"
MASTER = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
ADMIN_OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchRuleTemplateService.java"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchRuleTemplateController.java"
CREATION_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCreationService.java"
ADMIN_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
COPY_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java"
VIEW = ROOT / "frontend/src/views/admin/AdminRuleTemplateView.vue"
BATCH_VIEW = ROOT / "frontend/src/views/admin/AdminBatchView.vue"
ROUTER = ROOT / "frontend/src/router/index.ts"
SHELL = ROOT / "frontend/src/layouts/AppShell.vue"
MAIN = ROOT / "frontend/src/main.ts"
TYPES = ROOT / "frontend/src/api/types.ts"
SCHEMA_TS = ROOT / "frontend/src/api/schema.d.ts"
STYLE = ROOT / "frontend/src/batch-rule-template.css"
SMOKE = ROOT / "scripts/e2e/phase2_batch_rule_template_smoke.py"
WORKFLOW = ROOT / ".github/workflows/phase1-ci.yml"


class BatchRuleTemplateTest(unittest.TestCase):
    def test_v10_creates_immutable_templates_and_batch_reference(self) -> None:
        self.assertTrue(MIGRATION.is_file())
        sql = MIGRATION.read_text(encoding="utf-8")
        for expected in (
            "-- V10: add batch rule templates",
            "CREATE TABLE batch_rule_template",
            "rule_code",
            "revision",
            "hold_duration_seconds",
            "hold_renewal_limit",
            "allow_team",
            "team_min_size",
            "team_max_size",
            "team_max_size <= 5",
            "allow_student_random",
            "unselected_strategy",
            "rule_version",
            "is_default",
            "default_marker",
            "uk_batch_rule_template_revision",
            "uk_batch_rule_template_default",
            "ADD COLUMN rule_template_id",
            "fk_selection_batch_rule_template",
            "SYSTEM_DEFAULT",
            "UPDATE selection_batch",
        ):
            self.assertIn(expected, sql)
        frozen = SCHEMA.read_text(encoding="utf-8")
        self.assertIn("-- >>> BEGIN V10__add_batch_rule_templates.sql", frozen)
        self.assertIn("CREATE TABLE batch_rule_template", frozen)
        self.assertIn("ADD COLUMN rule_template_id BIGINT", frozen)
        dictionary = DICTIONARY.read_text(encoding="utf-8")
        self.assertIn("batch_rule_template", dictionary)
        self.assertIn("rule_template_id", dictionary)

    def test_openapi_exposes_template_management_and_batch_reference(self) -> None:
        self.assertTrue(OPENAPI.is_file())
        contract = OPENAPI.read_text(encoding="utf-8")
        master = MASTER.read_text(encoding="utf-8")
        admin = ADMIN_OPENAPI.read_text(encoding="utf-8")
        for expected in (
            "/api/v1/admin/batch-rule-templates:",
            "tags: [BatchRuleTemplate]",
            "operationId: listBatchRuleTemplates",
            "operationId: createBatchRuleTemplate",
            "operationId: createBatchRuleTemplateRevision",
            "BatchRuleTemplateCreateRequest:",
            "BatchRuleTemplateRevisionRequest:",
            "maximum: 5",
        ):
            self.assertIn(expected, contract)
        self.assertIn("batch-rule-templates", master)
        self.assertIn("ruleTemplateId", admin)

    def test_backend_uses_dedicated_services_and_rule_snapshot(self) -> None:
        self.assertTrue(SERVICE.is_file())
        source = SERVICE.read_text(encoding="utf-8")
        for expected in (
            "record RuleSnapshot",
            "resolveForBatch",
            "BATCH_RULE_TEMPLATE_NOT_FOUND",
            "BATCH_RULE_TEMPLATE_DISABLED",
            "BATCH_RULE_TEMPLATE_VERSION_CONFLICT",
            "BATCH_RULE_TEMPLATE_INVALID",
            "BATCH_RULE_TEMPLATE_DEFAULT_REQUIRED",
            '"BATCH_RULE_TEMPLATE_CREATE"',
            '"BATCH_RULE_TEMPLATE_REVISE"',
            "teamMaxSize() > 5",
            "FOR UPDATE",
        ):
            self.assertIn(expected, source)
        self.assertTrue(CONTROLLER.is_file())
        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("implements BatchRuleTemplateApi", controller)
        self.assertIn("listBatchRuleTemplates", controller)
        self.assertIn("createBatchRuleTemplate", controller)
        self.assertIn("createBatchRuleTemplateRevision", controller)

        self.assertTrue(CREATION_SERVICE.is_file())
        creation = CREATION_SERVICE.read_text(encoding="utf-8")
        self.assertIn("batchRuleTemplateService.resolveForBatch", creation)
        self.assertIn("rule_template_id", creation)
        self.assertIn("snapshot.holdDurationSeconds()", creation)
        self.assertIn('"BATCH_CREATE"', creation)
        admin_controller = ADMIN_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("request.getRuleTemplateId()", admin_controller)
        self.assertIn("batchCreationService.create", admin_controller)
        copy = COPY_SERVICE.read_text(encoding="utf-8")
        self.assertIn("rule_template_id", copy)

    def test_admin_ui_manages_templates_and_batch_selects_exact_revision(self) -> None:
        self.assertTrue(VIEW.is_file())
        source = VIEW.read_text(encoding="utf-8")
        for expected in (
            "批次规则模板",
            "创建规则模板",
            "创建新修订",
            "设为默认模板",
            "holdDurationSeconds",
            "teamMinSize",
            "teamMaxSize",
            "allowStudentRandom",
            "changeReason",
            'min="2" max="5"',
        ):
            self.assertIn(expected, source)
        batch = BATCH_VIEW.read_text(encoding="utf-8")
        self.assertIn("ruleTemplates", batch)
        self.assertIn("form.ruleTemplateId", batch)
        self.assertIn("ruleTemplateSummary", batch)
        self.assertIn("ruleTemplateId", SCHEMA_TS.read_text(encoding="utf-8"))
        self.assertIn("AdminRuleTemplateView", ROUTER.read_text(encoding="utf-8"))
        self.assertIn("admin/rule-templates", ROUTER.read_text(encoding="utf-8"))
        self.assertIn("批次规则", SHELL.read_text(encoding="utf-8"))
        self.assertIn("batch-rule-template.css", MAIN.read_text(encoding="utf-8"))
        self.assertTrue(STYLE.is_file())
        styles = STYLE.read_text(encoding="utf-8")
        self.assertIn(".rule-template-grid", styles)
        self.assertIn("@media (max-width: 720px)", styles)

    def test_runtime_smoke_is_registered_for_v11(self) -> None:
        self.assertTrue(SMOKE.is_file())
        smoke = SMOKE.read_text(encoding="utf-8")
        self.assertIn("occurred_at >= CURRENT_TIMESTAMP", smoke)
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m unittest scripts/phase2/test_batch_rule_templates.py -v", workflow)
        self.assertIn("python -m unittest scripts/phase2/test_post_merge_hardening.py -v", workflow)
        self.assertIn("python scripts/e2e/phase2_batch_rule_template_smoke.py", workflow)
        self.assertIn("Assert Flyway V11", workflow)


if __name__ == "__main__":
    unittest.main()
