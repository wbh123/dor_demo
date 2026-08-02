from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class SelectionResidencyTransferImplementationTest(unittest.TestCase):
    def test_batch_modes_are_persisted_and_authorized(self):
        creation = read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCreationService.java"
        )
        lifecycle = read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java"
        )
        copy = read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java"
        )
        self.assertIn("selection_mode", creation)
        self.assertIn("separate_student_categories", creation)
        self.assertIn("P2_BED_SELECTION_MODE", creation)
        self.assertIn("roomLockService.requirePublishable", lifecycle)
        self.assertIn("roomLockService.acquire", lifecycle)
        self.assertIn("roomLockService.release", lifecycle)
        self.assertIn("selectionMode", copy)
        self.assertIn("separateStudentCategories", copy)

    def test_room_mode_never_allocates_a_bed(self):
        service = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/RoomSelectionService.java"
        )
        self.assertIn('"ROOM"', service)
        self.assertIn('"ROOM_SELECT"', service)
        self.assertIn("具体床位由寝室成员", service)
        self.assertNotIn("random", service.lower())

    def test_bed_mode_uses_cross_batch_residency_truth(self):
        policy = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyPolicyService.java"
        )
        recommendation = read(
            "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java"
        )
        synchronizer = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/BedResidencySynchronizationService.java"
        )
        self.assertIn("availableBedCount(long batchId, long roomId)", policy)
        self.assertIn("room_assignment", policy)
        self.assertIn("batch_bed_scope", policy)
        self.assertIn("unknownBedResidents == 0", recommendation)
        self.assertIn("room_assignment", synchronizer)

    def test_student_category_and_room_scope_are_centralized(self):
        policy = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyPolicyService.java"
        )
        team = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/TeamCategoryGuard.java"
        )
        self.assertIn("DOMESTIC_ONLY", policy)
        self.assertIn("INTERNATIONAL_ONLY", policy)
        self.assertIn("MIXED", policy)
        self.assertIn("separate_student_categories", policy)
        self.assertIn("TEAM_STUDENT_CATEGORY_MISMATCH", team)

    def test_transfer_student_supports_three_atomic_paths(self):
        transfer = read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/TransferStudentService.java"
        )
        capacity = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/BatchCapacityService.java"
        )
        self.assertIn("@Transactional", transfer)
        for action in ("PROFILE_ONLY", "DIRECT_ASSIGNMENT", "ADD_TO_BATCH"):
            self.assertIn(action, transfer)
        self.assertIn("batchCapacityService.enroll", transfer)
        self.assertIn("availableBedCount(batchId, roomId)", capacity)
        self.assertIn("NO_ELIGIBLE_ROOM_CAPACITY", capacity)

    def test_frontend_is_graphical_and_not_json_driven(self):
        transfer = read("frontend/src/components/admin/TransferStudentWizard.vue")
        batch = read("frontend/src/views/admin/AdminBatchView.vue")
        dormitory = read("frontend/src/views/admin/AdminDormitoryView.vue")
        residency = read("frontend/src/views/admin/AdminResidencyView.vue")
        compact = read("frontend/src/platform/compact-feature-cards.css")
        self.assertIn("action-card", transfer)
        self.assertIn("mode-card", batch)
        self.assertIn("separation-switch", batch)
        self.assertIn("scope-segments", dormitory)
        self.assertIn("bed-card-grid", residency)
        self.assertIn("repeat(3", compact)
        combined = transfer + batch + dormitory + residency
        self.assertNotIn("JSON.stringify", combined)

    def test_latest_schema_and_two_1000_student_scenarios_exist(self):
        schema = read("backend-java/docs/sql/schema.sql")
        clean = read("backend-java/docs/sql/test-data/1000_students_clean.sql")
        realistic = read(
            "backend-java/docs/sql/test-data/1000_students_realistic_mixed_state.sql"
        )
        self.assertIn("Schema version: V1-V16", schema)
        self.assertIn("CLEAN_1000_READY", clean)
        self.assertIn("REALISTIC_1000_READY", realistic)
        self.assertIn("<>840", realistic)
        self.assertIn("<>160", realistic)


if __name__ == "__main__":
    unittest.main()
