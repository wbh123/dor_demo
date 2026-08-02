from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class SingleClientSubscriptionContractTest(unittest.TestCase):
    def test_v12_and_v13_define_platform_schema_and_seed(self):
        v12 = read("backend-java/server/src/main/resources/db/migration/V12__add_single_client_subscription_entitlements.sql")
        v13 = read("backend-java/server/src/main/resources/db/migration/V13__seed_single_client_subscription_catalog.sql")
        for table in (
            "feature_catalog", "quota_catalog", "subscription_plan",
            "subscription_plan_revision", "plan_revision_feature",
            "plan_revision_quota", "service_subscription",
            "service_subscription_revision", "subscription_feature_override",
            "subscription_quota_override", "service_quota_alert",
            "batch_entitlement_snapshot", "platform_audit_log",
        ):
            self.assertIn(f"CREATE TABLE {table}", v12)
        self.assertIn("password_change_required", v12)
        self.assertIn("SYSTEM_ADMIN", v12)
        self.assertIn("system_admin", v13)
        self.assertIn("LONG_TERM", v13)
        self.assertIn("end_at", v12)
        self.assertIn("P1_DORMITORY_BASIC", v13)
        self.assertIn("P2_BATCH_COPY", v13)
        self.assertIn("P3_ROOM_CHANGE_REQUEST", v13)
        self.assertIn("MAX_STUDENTS", v13)
        self.assertNotIn("tenant_id", v12 + v13)

    def test_backend_has_centralized_entitlement_services(self):
        base = "backend-java/server/src/main/java/com/wust/dormitory/subscription/"
        feature = read(base + "FeatureAccessService.java")
        quota = read(base + "QuotaService.java")
        snapshot = read(base + "EntitlementSnapshotService.java")
        interceptor = read(base + "FeatureAccessInterceptor.java")
        self.assertIn("currentFeatures", feature)
        self.assertIn("AccessMode", feature)
        self.assertIn("FEATURE_NOT_ENABLED", feature)
        self.assertIn("requireAvailable", quota)
        self.assertIn("SERVICE_QUOTA_EXCEEDED", quota)
        self.assertIn("captureForBatch", snapshot)
        self.assertIn("allowsBatchContinuation", snapshot)
        self.assertIn("/api/v1/platform/", interceptor)
        self.assertIn("FeatureRouteCatalog", interceptor)

    def test_identity_is_strictly_separated(self):
        current_user = read("backend-java/server/src/main/java/com/wust/dormitory/security/CurrentUser.java")
        security_users = read("backend-java/server/src/main/java/com/wust/dormitory/security/SecurityUsers.java")
        platform_auth = read("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthService.java")
        self.assertIn("isSystemAdmin", current_user)
        self.assertIn("passwordChangeRequired", current_user)
        self.assertIn("requireSystemAdmin", security_users)
        self.assertIn("requireBusinessUser", security_users)
        self.assertIn("SYSTEM_ADMIN_PASSWORD_CHANGE_REQUIRED", security_users)
        self.assertIn("SYSTEM_ADMIN", platform_auth)
        self.assertIn("password_change_required", platform_auth)

    def test_platform_openapi_and_frontend_are_separate(self):
        interface = read("backend-java/model/src/main/resources/openapi-interface.yaml")
        platform_contract = read("backend-java/model/src/main/resources/platform/openapi-platform.yaml")
        router = read("frontend/src/router/index.ts")
        platform_session = read("frontend/src/platform/session.ts")
        self.assertIn("platform/openapi-platform.yaml", interface)
        self.assertIn("/api/v1/platform/login", platform_contract)
        self.assertIn("/api/v1/platform/subscription", platform_contract)
        self.assertIn("/api/v1/platform/plans", platform_contract)
        self.assertIn("/platform/login", router)
        self.assertIn("passwordChangeRequired", platform_session)
        self.assertNotIn("tenantId", platform_contract + platform_session)

    def test_business_feature_mapping_is_explicit(self):
        catalog = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureRouteCatalog.java")
        self.assertIn("P1_IDENTITY_BASIC", catalog)
        self.assertIn("P1_DORMITORY_BASIC", catalog)
        self.assertIn("P1_BATCH_BASIC", catalog)
        self.assertIn("P1_SELF_SELECTION", catalog)
        self.assertIn("P2_ROOM_LAYOUT_UPDATE", catalog)
        self.assertIn("P2_MATCHING_SCHEME_REVISE", catalog)
        self.assertIn("P2_BATCH_COPY", catalog)
        self.assertIn("P2_RULE_TEMPLATE_REVISE", catalog)

    def test_password_reset_script_does_not_embed_password(self):
        script = read("scripts/admin/reset_system_admin_password.py")
        self.assertIn("getpass", script)
        self.assertIn("password_change_required", script)
        self.assertIn("SYSTEM_ADMIN_PASSWORD_RESET", script)
        self.assertNotIn("Dormitory@2026", script)


if __name__ == "__main__":
    unittest.main()
