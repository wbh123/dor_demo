from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class PlatformFeatureGuiContractTest(unittest.TestCase):
    def test_backend_exposes_entitlement_projection_and_final_state_commands(self):
        controller = read(
            "backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformManagementController.java"
        )
        service = read(
            "backend-java/server/src/main/java/com/wust/dormitory/subscription/EntitlementAdminService.java"
        )
        self.assertIn('@GetMapping("/features/entitlements")', controller)
        self.assertIn('@PutMapping("/features/{featureCode}/state")', controller)
        self.assertIn('@PostMapping("/features/batch-state")', controller)
        self.assertIn("FeatureTargetState", controller)
        self.assertIn("featureEntitlements", service)
        self.assertIn("setFeatureState", service)
        self.assertIn("setFeatureStates", service)
        self.assertIn("@Transactional", service)
        self.assertIn("enabled_in_program", service)
        self.assertIn("FEATURE_NOT_IMPLEMENTED", service)
        self.assertIn("effective_until", service)
        self.assertIn("FEATURE_BATCH_STATE_SET", service)

    def test_openapi_declares_gui_entitlement_endpoints(self):
        source = read("backend-java/model/src/main/resources/platform/openapi-platform.yaml")
        self.assertIn("/api/v1/platform/features/entitlements:", source)
        self.assertIn("/api/v1/platform/features/{featureCode}/state:", source)
        self.assertIn("/api/v1/platform/features/batch-state:", source)
        self.assertIn("FeatureTargetState", source)
        self.assertIn("INHERIT", source)

    def test_frontend_uses_switches_filters_and_batch_mode(self):
        page = read("frontend/src/views/platform/PlatformFeaturesView.vue")
        api = read("frontend/src/platform/api.ts")
        self.assertIn('role="switch"', page)
        self.assertIn("batchMode", page)
        self.assertIn("批量编辑", page)
        self.assertIn("恢复套餐默认", page)
        self.assertIn("searchText", page)
        self.assertIn("phaseFilter", page)
        self.assertIn("scopeFilter", page)
        self.assertIn("stateFilter", page)
        self.assertIn("featureEntitlements", api)
        self.assertIn("setFeatureState", api)
        self.assertIn("setFeatureStates", api)
        self.assertNotIn("JSON.stringify(catalog", page)
        self.assertNotIn("每行一个功能代码", page)


if __name__ == "__main__":
    unittest.main()
