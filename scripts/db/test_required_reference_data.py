from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V18__seed_required_business_reference_data.sql"
NAVICAT = ROOT / "backend-java/docs/sql/navicat/01_数据库架构/18_V18__seed_required_business_reference_data.sql"
INTEGRITY = ROOT / "backend-java/docs/sql/navicat/04_数据库完整性检查/00_修复并检查数据库完整性.sql"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


class RequiredReferenceDataTest(unittest.TestCase):
    def test_v18_seeds_published_questionnaire_and_enabled_scheme_idempotently(self):
        source = read(MIGRATION)
        for token in (
            "SYSTEM-PREFERENCE-V1",
            "version_status='PUBLISHED'",
            "questionnaire_question",
            "questionnaire_option",
            "SUMMER_AC_OVERNIGHT",
            "WINTER_HEATING_ACCEPTANCE",
            "SMOKING_ACCEPTANCE",
            "BED_PREFERENCE",
            "SYSTEM_DEFAULT",
            "matching_weight_scheme",
            "WHERE NOT EXISTS",
        ):
            self.assertIn(token, source)
        self.assertIn("SELECT COUNT(*) FROM questionnaire_version WHERE version_status='PUBLISHED'", source)
        self.assertIn("SELECT COUNT(*) FROM matching_weight_scheme WHERE enabled=1", source)

    def test_navicat_contains_same_v18_reference_migration(self):
        self.assertTrue(NAVICAT.exists())
        source = read(NAVICAT)
        self.assertIn("SYSTEM-PREFERENCE-V1", source)
        self.assertIn("SYSTEM_DEFAULT", source)

    def test_integrity_checker_requires_runtime_reference_data(self):
        source = read(INTEGRITY)
        self.assertIn("缺少已发布的个人偏好问卷", source)
        self.assertIn("缺少已启用的匹配权重方案", source)
        self.assertIn("questionnaire_option", source)
        self.assertIn("version='18'", source)


if __name__ == "__main__":
    unittest.main()
