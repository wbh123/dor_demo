from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V16__add_residency_student_category_and_transfer_support.sql"


class ResidencyTransferSchemaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION.read_text(encoding="utf-8") if MIGRATION.exists() else ""

    def test_student_room_and_batch_category_fields_exist(self):
        for token in (
            "student_category",
            "enrollment_source",
            "resident_scope",
            "separate_student_categories",
            "source_type",
        ):
            self.assertIn(token, self.sql)
        for value in (
            "DOMESTIC",
            "INTERNATIONAL",
            "DOMESTIC_ONLY",
            "INTERNATIONAL_ONLY",
            "MIXED",
            "TRANSFER_MANUAL",
        ):
            self.assertIn(value, self.sql)

    def test_room_assignment_is_cross_batch_residency_truth(self):
        for token in (
            "MODIFY COLUMN batch_id BIGINT NULL",
            "ADD COLUMN bed_id BIGINT NULL",
            "source_selection_mode",
            "bed_confirmed_at",
            "ended_at",
            "end_reason",
            "active_student_marker",
            "active_bed_marker",
            "uk_active_residency_student",
            "uk_active_residency_bed",
            "room_assignment_history",
        ):
            self.assertIn(token, self.sql)
        self.assertIn("INSERT INTO room_assignment", self.sql)
        self.assertIn("FROM bed_assignment", self.sql)

    def test_existing_migrations_are_not_modified_by_contract(self):
        self.assertTrue(MIGRATION.name.startswith("V16__"))
        self.assertNotIn("DROP TABLE room_assignment", self.sql)


if __name__ == "__main__":
    unittest.main()
