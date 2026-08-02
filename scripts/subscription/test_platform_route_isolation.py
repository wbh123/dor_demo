from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ROUTER = ROOT / "frontend/src/router/index.ts"


class PlatformRouteIsolationTest(unittest.TestCase):
    def test_platform_routes_are_registered_before_catch_all(self):
        source = ROUTER.read_text(encoding="utf-8")
        self.assertIn("import { platformRoutes, installPlatformRouteGuard }", source)
        self.assertIn("...platformRoutes", source)
        self.assertLess(source.index("...platformRoutes"), source.index("/:pathMatch(.*)*"))

    def test_business_guard_never_redirects_platform_routes(self):
        source = ROUTER.read_text(encoding="utf-8")
        guard_start = source.index("router.beforeEach(async (to) =>")
        auth_start = source.index("const auth = useAuthStore", guard_start)
        platform_skip = source.index("to.path.startsWith('/platform')", guard_start)
        self.assertLess(platform_skip, auth_start)
        self.assertIn("installPlatformRouteGuard(router)", source)


if __name__ == "__main__":
    unittest.main()
