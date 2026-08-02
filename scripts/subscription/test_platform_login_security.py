from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SECURITY_CONFIG = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/security/SecurityConfig.java"
PASSWORD_MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V14__fix_system_admin_password_encoding.sql"


class PlatformLoginSecurityTest(unittest.TestCase):
    def test_platform_login_is_public_but_platform_apis_require_system_admin(self):
        source = SECURITY_CONFIG.read_text(encoding="utf-8")
        self.assertIn('"/api/v1/platform/login"', source)
        public_matcher = source.index('"/api/v1/platform/login"')
        permit_all = source.index('.permitAll()', public_matcher)
        platform_matcher = source.index('"/api/v1/platform/**"')
        system_admin_role = source.index('.hasRole("SYSTEM_ADMIN")', platform_matcher)
        self.assertLess(public_matcher, permit_all)
        self.assertLess(permit_all, platform_matcher)
        self.assertLess(platform_matcher, system_admin_role)

    def test_existing_system_admin_hash_is_upgraded_for_delegating_encoder(self):
        source = PASSWORD_MIGRATION.read_text(encoding="utf-8")
        self.assertIn("{bcrypt}", source)
        self.assertIn("user_type = 'SYSTEM_ADMIN'", source)
        self.assertIn("password_hash LIKE '$2%'", source)


if __name__ == "__main__":
    unittest.main()
