#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V5__add_room_bed_layout.sql"
SCHEMA = ROOT / "backend-java/docs/sql/schema.sql"
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
STUDENT_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomLayoutService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
SCENE = ROOT / "frontend/src/components/student/RoomBedScene3D.vue"
STYLES = ROOT / "frontend/src/phase2-room-layout.css"
MAIN = ROOT / "frontend/src/main.ts"
SMOKE = ROOT / "scripts/e2e/phase2_room_layout_smoke.py"
WORKFLOW = ROOT / ".github/workflows/phase1-ci.yml"


class RoomLayoutPhaseTwoTest(unittest.TestCase):
    def test_v5_creates_normalized_room_bed_layout(self) -> None:
        self.assertTrue(MIGRATION.is_file())
        content = MIGRATION.read_text(encoding="utf-8")
        for expected in (
            "CREATE TABLE room_bed_layout",
            "bed_id BIGINT NOT NULL",
            "layout_x DECIMAL(6,3) NOT NULL",
            "layout_z DECIMAL(6,3) NOT NULL",
            "rotation_degrees SMALLINT NOT NULL",
            "updated_by BIGINT NOT NULL",
            "PRIMARY KEY (bed_id)",
            "FOREIGN KEY (bed_id) REFERENCES bed(id)",
            "FOREIGN KEY (updated_by) REFERENCES app_user(id)",
            "ck_room_bed_layout_x",
            "ck_room_bed_layout_z",
            "ck_room_bed_layout_rotation",
        ):
            self.assertIn(expected, content)
        schema = SCHEMA.read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE room_bed_layout", schema)

    def test_openapi_exposes_read_and_update_layout(self) -> None:
        content = OPENAPI.read_text(encoding="utf-8")
        self.assertIn("/api/v1/admin/rooms/{roomId}/bed-layout:", content)
        self.assertIn("operationId: getRoomBedLayout", content)
        self.assertIn("operationId: updateRoomBedLayout", content)
        self.assertIn("RoomBedLayoutRequest:", content)
        self.assertIn("RoomBedLayoutItem:", content)
        for field in ("expectedRoomVersion", "reason", "beds", "bedId", "layoutX", "layoutZ", "rotationDegrees"):
            self.assertIn(field, content)

    def test_backend_has_transactional_layout_service_and_generated_controller_methods(self) -> None:
        self.assertTrue(SERVICE.is_file())
        service = SERVICE.read_text(encoding="utf-8")
        for expected in (
            "class RoomLayoutService",
            "public Map<String, Object> getLayout",
            "public Map<String, Object> updateLayout",
            "@Transactional",
            "ROOM_LAYOUT_BED_MISMATCH",
            "ROOM_LAYOUT_OUT_OF_BOUNDS",
            "ROOM_LAYOUT_ROTATION_INVALID",
            "ROOM_LAYOUT_BUNK_MISMATCH",
            "ROOM_LAYOUT_VERSION_CONFLICT",
            "ROOM_LAYOUT_UPDATE",
            "DEFAULT_LAYOUT",
        ):
            self.assertIn(expected, service)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("getRoomBedLayout", controller)
        self.assertIn("updateRoomBedLayout", controller)
        self.assertIn("RoomLayoutService", controller)
        self.assertNotIn("@GetMapping", controller)
        self.assertNotIn("@PutMapping", controller)

    def test_student_room_snapshot_returns_layout_fields(self) -> None:
        self.assertTrue(STUDENT_LAYOUT.is_file())
        content = STUDENT_LAYOUT.read_text(encoding="utf-8")
        self.assertIn("LEFT JOIN room_bed_layout", content)
        self.assertIn("layout.layout_x", content)
        self.assertIn("layout.layout_z", content)
        self.assertIn("layout.rotation_degrees", content)
        controller = STUDENT_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("roomLayoutService.enrich", controller)

    def test_admin_has_visual_drag_editor_with_bunk_grouping_and_mobile_inputs(self) -> None:
        self.assertTrue(EDITOR.is_file())
        editor = EDITOR.read_text(encoding="utf-8")
        for expected in (
            "room-layout-stage",
            "layoutUnits",
            "bed_frame_id",
            "startDrag",
            "pointermove",
            "snapCoordinate",
            "cycleRotation",
            "restoreDefaultLayout",
            "expectedRoomVersion",
            "修改原因",
            "layout-number-grid",
        ):
            self.assertIn(expected, editor)

        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        self.assertIn("RoomLayoutEditor", admin)
        self.assertIn("openLayoutEditor", admin)
        self.assertIn(">布局</button>", admin)

        self.assertTrue(STYLES.is_file())
        styles = STYLES.read_text(encoding="utf-8")
        self.assertIn(".room-layout-stage", styles)
        self.assertIn("@media (max-width: 640px)", styles)
        self.assertIn("./phase2-room-layout.css", MAIN.read_text(encoding="utf-8"))

    def test_three_scene_prefers_database_layout_and_keeps_default_fallback(self) -> None:
        scene = SCENE.read_text(encoding="utf-8")
        for expected in (
            "customBedPlacement",
            "bed.layout_x",
            "bed.layout_z",
            "bed.rotation_degrees",
            "defaultBedPlacement",
            "defaultBedRotation",
            "bunkAnchor",
        ):
            self.assertIn(expected, scene)

    def test_runtime_smoke_is_connected_to_ci(self) -> None:
        self.assertTrue(SMOKE.is_file())
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m unittest scripts/phase2/test_room_layout.py -v", workflow)
        self.assertIn("python scripts/e2e/phase2_room_layout_smoke.py", workflow)
        self.assertIn("Assert Flyway V5 and room layout rules", workflow)


if __name__ == "__main__":
    unittest.main()
