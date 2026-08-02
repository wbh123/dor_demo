from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class SelectionModeContractTest(unittest.TestCase):
    def test_v15_adds_room_assignment_without_random_bed(self):
        sql = read("backend-java/server/src/main/resources/db/migration/V15__add_batch_selection_modes.sql")
        self.assertIn("selection_mode", sql)
        self.assertIn("active_batch_room_lock", sql)
        self.assertIn("room_assignment", sql)
        self.assertIn("P2_BED_SELECTION_MODE", sql)
        self.assertIn("ROOM_SELECT", sql)
        self.assertIn("TEAM_ROOM_SELECT", sql)
        self.assertNotIn("ROOM_SELECT_RANDOM", sql)
        self.assertNotIn("TEAM_ROOM_SELECT_RANDOM", sql)

    def test_openapi_exposes_mode_and_room_selection(self):
        admin = read("backend-java/model/src/main/resources/admin/openapi-admin.yaml")
        student = read("backend-java/model/src/main/resources/student/openapi-student.yaml")
        root = read("backend-java/model/src/main/resources/openapi-interface.yaml")
        self.assertIn("selectionMode", admin)
        self.assertIn("enum: [ROOM, BED]", admin)
        self.assertIn("selectRoom", student)
        self.assertIn("selectTeamRoom", student)
        self.assertIn("rooms/{roomId}/select", root)
        self.assertIn("teams/{teamId}/rooms/{roomId}/select", root)

    def test_backend_uses_room_assignment_and_mode_guards(self):
        room_selection = read("backend-java/server/src/main/java/com/wust/dormitory/selection/RoomSelectionService.java")
        room_lock = read("backend-java/server/src/main/java/com/wust/dormitory/admin/ActiveBatchRoomLockService.java")
        mode_guard = read("backend-java/server/src/main/java/com/wust/dormitory/selection/BatchSelectionModeGuard.java")
        self.assertIn("INSERT INTO room_assignment", room_selection)
        self.assertIn("ROOM_CAPACITY_FULL", room_selection)
        self.assertNotIn("INSERT INTO bed_assignment", room_selection)
        self.assertIn("active_batch_room_lock", room_lock)
        self.assertIn("BATCH_ROOM_ACTIVE_CONFLICT", room_lock)
        self.assertIn("requireBedMode", mode_guard)
        self.assertIn("BATCH_SELECTION_MODE_MISMATCH", mode_guard)

    def test_frontend_uses_graphical_mode_cards_and_compact_permissions(self):
        batch = read("frontend/src/views/admin/AdminBatchView.vue")
        rooms = read("frontend/src/views/student/RoomListView.vue")
        features = read("frontend/src/views/platform/PlatformFeaturesView.vue")
        self.assertIn("mode-card", batch)
        self.assertIn("selectionMode", batch)
        self.assertIn("选择此寝室", rooms)
        self.assertIn("selectRoom", rooms)
        self.assertIn("grid-template-columns: repeat(3", features)
        self.assertNotIn("JSON.stringify", batch)


if __name__ == "__main__":
    unittest.main()
