from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SQL_DIR = ROOT / "backend-java/docs/sql"
DATA_DIR = SQL_DIR / "test-data"


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
            "ROOM_BED_MAPPING_REQUIRED",
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


if __name__ == "__main__":
    unittest.main()
