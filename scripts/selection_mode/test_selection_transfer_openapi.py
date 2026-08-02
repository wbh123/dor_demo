from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class SelectionTransferOpenApiTest(unittest.TestCase):
    def test_root_contract_references_new_fragments(self):
        root = read("backend-java/model/src/main/resources/openapi-interface.yaml")
        for token in (
            "admin/openapi-batch-selection.yaml",
            "admin/openapi-residency-transfer.yaml",
            "student/openapi-room-selection.yaml",
            "/api/v1/admin/transfer-students",
            "/api/v1/student/residency/bed-confirmation",
        ):
            self.assertIn(token, root)

    def test_batch_modes_and_student_categories_are_typed(self):
        batch = read("backend-java/model/src/main/resources/admin/openapi-batch-selection.yaml")
        students = read("backend-java/model/src/main/resources/admin/openapi-student-management.yaml")
        rooms = read("backend-java/model/src/main/resources/admin/openapi-room-management.yaml")
        self.assertIn("enum: [ROOM, BED]", batch)
        self.assertIn("separateStudentCategories", batch)
        self.assertIn("enum: [DOMESTIC, INTERNATIONAL]", students)
        self.assertIn("TRANSFER_MANUAL", students)
        self.assertIn("enum: [DOMESTIC_ONLY, INTERNATIONAL_ONLY, MIXED]", rooms)

    def test_no_raw_json_configuration_for_new_workflows(self):
        combined = read("backend-java/model/src/main/resources/admin/openapi-residency-transfer.yaml") + read(
            "backend-java/model/src/main/resources/student/openapi-room-selection.yaml"
        )
        self.assertNotIn("additionalProperties: true", combined)
        for operation in (
            "onboardTransferStudent",
            "previewStudentBatchCapacity",
            "enrollStudentIntoBatch",
            "selectRoom",
            "selectTeamRoom",
            "confirmMyActualBed",
        ):
            self.assertIn(f"operationId: {operation}", combined)


if __name__ == "__main__":
    unittest.main()
