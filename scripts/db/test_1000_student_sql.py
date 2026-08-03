from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SQL_DIR = ROOT / "backend-java/docs/sql"
DATA_DIR = SQL_DIR / "test-data"
NAVICAT_DIR = SQL_DIR / "navicat"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


class ThousandStudentSqlTest(unittest.TestCase):
    def test_latest_schema_installer_contains_v16(self):
        schema = read(SQL_DIR / "schema.sql")
        self.assertIn("Schema version: V1-V16", schema)
        self.assertIn("V15__add_batch_selection_modes.sql", schema)
        self.assertIn("V16__add_residency_student_category_and_transfer_support.sql", schema)
        self.assertLess(
            schema.index("V15__add_batch_selection_modes.sql"),
            schema.index("V16__add_residency_student_category_and_transfer_support.sql"),
        )

    def test_common_base_generates_enough_resources_and_students(self):
        base = read(DATA_DIR / "1000_students_base.sql")
        for token in (
            "WHILE i<=1000",
            "WHILE building_index<=6",
            "room_id_value=room_id_value+1",
            "bed_base+5",
            "DOMESTIC_ONLY",
            "INTERNATIONAL_ONLY",
            "MIXED",
            "TRANSFER_MANUAL",
        ):
            self.assertIn(token, base)
        self.assertIn("COUNT(*) FROM room", base)
        self.assertIn("COUNT(*) FROM bed", base)

    def test_clean_entry_contains_no_business_state(self):
        clean = read(DATA_DIR / "1000_students_clean.sql")
        self.assertIn("SOURCE 1000_students_base.sql", clean)
        self.assertIn("COUNT(*) FROM selection_batch) <> 0", clean)
        self.assertIn("COUNT(*) FROM room_assignment) <> 0", clean)
        self.assertIn("CLEAN_1000_READY", clean)

    def test_realistic_entry_models_room_and_bed_modes(self):
        realistic = read(DATA_DIR / "1000_students_realistic_mixed_state.sql")
        for token in (
            "'OPEN','ROOM',0",
            "'OPEN','BED',1",
            "active_batch_room_lock",
            "source_selection_mode",
            "bed_id IS NULL",
            "student_feature",
            "student_notification",
            "REALISTIC_1000_READY",
        ):
            self.assertIn(token, realistic)
        self.assertIn("<>840", realistic)
        self.assertIn("<>160", realistic)

    def test_scripts_do_not_drop_platform_catalogs(self):
        base = read(DATA_DIR / "1000_students_base.sql")
        for table in (
            "feature_catalog",
            "quota_catalog",
            "subscription_plan",
            "service_subscription",
        ):
            self.assertIn(f"'{table}'", base)
        self.assertNotIn("DROP DATABASE", base)

    def test_navicat_schema_recreates_unified_database(self):
        reset = read(NAVICAT_DIR / "01_数据库架构/00_删除并创建数据库.sql")
        self.assertIn("DROP DATABASE IF EXISTS `wust_dormitory`", reset)
        self.assertIn("CREATE DATABASE `wust_dormitory`", reset)
        self.assertIn("USE `wust_dormitory`", reset)
        migration_files = sorted((NAVICAT_DIR / "01_数据库架构").glob("[0-9][0-9]_V*.sql"))
        self.assertEqual(16, len(migration_files))
        self.assertTrue((NAVICAT_DIR / "01_数据库架构/99_写入Flyway基线.sql").exists())

    def test_navicat_data_packages_select_database_and_clear_business_data(self):
        for folder, filename, ready_token in (
            ("02_1000人干净测试数据", "01_清空并导入1000人干净数据.sql", "CLEAN_1000_READY"),
            ("03_1000人真实业务数据", "01_清空并导入1000人真实业务数据.sql", "REALISTIC_1000_READY"),
        ):
            selector = read(NAVICAT_DIR / folder / "00_使用统一数据库.sql")
            data = read(NAVICAT_DIR / folder / filename)
            self.assertIn("USE `wust_dormitory`", selector)
            self.assertIn("CALL clear_1000_test_data();", data)
            self.assertIn(ready_token, data)
            self.assertNotIn("DROP DATABASE", data)

    def test_navicat_generators_use_unified_database(self):
        schema_generator = read(ROOT / "scripts/db/build_frozen_baseline.py")
        data_generator = read(ROOT / "scripts/db/generate_1000_student_sql.py")
        orchestrator = read(ROOT / "scripts/db/generate_navicat_sql.py")
        for source in (schema_generator, data_generator, orchestrator):
            self.assertIn("wust_dormitory", source)
        self.assertIn('choices=("source", "inline", "navicat")', schema_generator)
        self.assertIn("--check", schema_generator)
        self.assertIn("--check", data_generator)


if __name__ == "__main__":
    unittest.main()
