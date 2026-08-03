from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION_DIR = ROOT / "backend-java/server/src/main/resources/db/migration"
NAVICAT_DIR = ROOT / "backend-java/docs/sql/navicat"
DATA_DIR = ROOT / "backend-java/docs/sql/test-data"
DATA_BASE = DATA_DIR / "1000_students_base.sql"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


class NavicatDatabaseIntegrityTest(unittest.TestCase):
    def test_v17_restores_required_system_configuration(self):
        migration = read(MIGRATION_DIR / "V17__restore_required_system_configuration.sql")
        self.assertIn("STUDENT_WELCOME_MESSAGE", migration)
        self.assertIn("INSERT INTO system_setting", migration)
        self.assertIn("WHERE NOT EXISTS", migration)
        self.assertIn("matching_weight_scheme", migration)
        self.assertIn("batch_rule_template", migration)
        self.assertIn("LEFT JOIN app_user", migration)

    def test_thousand_student_cleanup_preserves_system_settings(self):
        base = read(DATA_BASE)
        self.assertIn("'system_setting'", base)
        self.assertIn("STUDENT_WELCOME_MESSAGE", base)
        self.assertIn("UPDATE matching_weight_scheme", base)
        self.assertIn("UPDATE batch_rule_template", base)
        self.assertIn("UPDATE system_setting", base)

    def test_test_data_entries_clear_derived_quota_alerts(self):
        clean = read(DATA_DIR / "1000_students_clean.sql")
        self.assertIn("DELETE FROM service_quota_alert", clean)
        self.assertIn("STUDENT_WELCOME_MESSAGE", clean)
        generator = read(ROOT / "scripts/db/generate_1000_student_sql.py")
        self.assertIn("DELETE FROM service_quota_alert", generator)
        self.assertIn("STUDENT_WELCOME_MESSAGE", generator)

    def test_navicat_schema_contains_v17_and_integrity_check(self):
        migrations = sorted((NAVICAT_DIR / "01_数据库架构").glob("[0-9][0-9]_V*.sql"))
        self.assertEqual(17, len(migrations))
        self.assertTrue(
            (NAVICAT_DIR / "01_数据库架构/17_V17__restore_required_system_configuration.sql").exists()
        )
        baseline = read(NAVICAT_DIR / "01_数据库架构/99_写入Flyway基线.sql")
        self.assertIn("'17'", baseline)
        self.assertTrue((NAVICAT_DIR / "04_数据库完整性检查/00_修复并检查数据库完整性.sql").exists())

    def test_navicat_test_data_packages_restore_required_configuration(self):
        for folder in ("02_1000人干净测试数据", "03_1000人真实业务数据"):
            recovery = read(NAVICAT_DIR / folder / "02_恢复必需系统配置.sql")
            self.assertIn("STUDENT_WELCOME_MESSAGE", recovery)
            self.assertIn("DELETE FROM service_quota_alert", recovery)
            self.assertIn("UPDATE matching_weight_scheme", recovery)
            self.assertIn("UPDATE batch_rule_template", recovery)
            self.assertIn("SYSTEM_CONFIGURATION_READY", recovery)

    def test_integrity_sql_checks_structure_seed_and_orphans(self):
        sql = read(NAVICAT_DIR / "04_数据库完整性检查/00_修复并检查数据库完整性.sql")
        for token in (
            "STUDENT_WELCOME_MESSAGE",
            "SYSTEM_ADMIN",
            "feature_catalog",
            "quota_catalog",
            "FULL_CURRENT",
            "PRIMARY_SERVICE",
            "SYSTEM_DEFAULT",
            "flyway_schema_history",
            "version='17'",
            "information_schema.tables",
            "information_schema.columns",
            "information_schema.statistics",
            "information_schema.referential_constraints",
            "DB_INTEGRITY_OK",
        ):
            self.assertIn(token, sql)
        self.assertIn("SIGNAL SQLSTATE '45000'", sql)

    def test_navicat_generator_delivers_integrity_script(self):
        generator = read(ROOT / "scripts/db/generate_navicat_sql.py")
        self.assertIn("04_数据库完整性检查.sql", generator)
        self.assertIn("INTEGRITY_SOURCE", generator)
        self.assertIn("sync_integrity_file", generator)

    def test_navicat_readme_requires_integrity_check_after_import(self):
        readme = read(NAVICAT_DIR / "README.md")
        self.assertIn("04_数据库完整性检查", readme)
        self.assertIn("00_修复并检查数据库完整性.sql", readme)
        self.assertIn("02_恢复必需系统配置.sql", readme)
        self.assertIn("wust_dormitory", readme)
        self.assertIn("DB_INTEGRITY_OK", readme)


if __name__ == "__main__":
    unittest.main()
