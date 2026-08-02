#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V5__add_room_bed_layout.sql"
SCHEMA = ROOT / "backend-java/docs/sql/schema.sql"
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
ROOM_MANAGEMENT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
STUDENT_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomLayoutService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
SCENE = ROOT / "frontend/src/components/student/RoomBedScene3D.vue"
STYLES = ROOT / "frontend/src/phase2-room-layout.css"
SCENE_SIZE_STYLES = ROOT / "frontend/src/room-scene-geometry-fix.css"
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
        main = MAIN.read_text(encoding="utf-8")
        self.assertIn("./phase2-room-layout.css", main)
        self.assertIn("./room-scene-geometry-fix.css", main)

    def test_admin_room_editor_uses_physical_bed_count_and_readonly_capacity(self) -> None:
        self.assertTrue(ROOM_MANAGEMENT.is_file())
        service = ROOM_MANAGEMENT.read_text(encoding="utf-8")
        self.assertIn("class RoomManagementService", service)
        self.assertIn("SELECT COUNT(*) FROM bed WHERE room_id=:id", service)
        self.assertNotIn("room_id=:id AND operational_status='ENABLED'", service)
        self.assertIn("房间容量必须等于当前床位总数", service)
        self.assertIn("COALESCE(SUM(bed.operational_status='ENABLED'), 0) AS enabled_bed_count", service)
        self.assertIn("ROOM_TYPE_CAPACITY_MISMATCH", service)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("roomManagementService.rooms", controller)
        self.assertIn("roomManagementService.updateRoom", controller)

        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        for expected in (
            "physicalBedCount",
            "roomTypeOptions",
            "room-editor-dialog",
            "room-editor-summary",
            "room-capacity-field",
            "readonly",
            "容量由物理床位总数决定",
        ):
            self.assertIn(expected, admin)
        self.assertIn("capacity: physicalBedCount(selectedRoom.value)", admin)

    def test_layout_editor_uses_larger_bed_cards_and_wider_dialog(self) -> None:
        styles = STYLES.read_text(encoding="utf-8")
        for expected in (
            "width: min(1240px, calc(100vw - 32px))",
            "min-height: 500px",
            "width: 154px",
            "height: 84px",
            ".room-editor-dialog",
            ".room-editor-summary",
        ):
            self.assertIn(expected, styles)

    def test_drag_coordinate_snapping_never_escapes_backend_bounds(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("const snapped = Math.round(value / SNAP) * SNAP", editor)
        self.assertIn("return Math.min(maximum, Math.max(minimum, snapped))", editor)
        self.assertNotIn("const clamped = Math.min(maximum, Math.max(minimum, value))", editor)

    def test_save_button_reports_missing_reason_instead_of_silently_disabling(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("请填写布局修改原因后再保存", editor)
        self.assertIn("reasonInput.value?.focus()", editor)
        self.assertIn(':disabled="saving"', editor)
        self.assertNotIn(':disabled="saving || !reason.trim()"', editor)

    def test_default_layout_is_long_rectangle_with_short_edge_openings_and_two_by_two_units(self) -> None:
        scene = SCENE.read_text(encoding="utf-8")
        editor = EDITOR.read_text(encoding="utf-8")
        service = SERVICE.read_text(encoding="utf-8")
        styles = STYLES.read_text(encoding="utf-8")
        for expected in (
            "const ROOM_WIDTH = 11.5",
            "const ROOM_DEPTH = 8",
            "const DOOR_SIDE_X = -2.35",
            "const WINDOW_SIDE_X = 2.35",
            "const UPPER_ROW_Z = -1.65",
            "const LOWER_ROW_Z = 1.65",
            "const LEFT_SHORT_WALL_X",
            "const RIGHT_SHORT_WALL_X",
            "门窗位于房间短边",
            "纵向2×2布局",
        ):
            self.assertIn(expected, scene)
        self.assertIn("return new THREE.Vector3(DOOR_SIDE_X, 0, UPPER_ROW_Z)", scene)
        self.assertIn("return new THREE.Vector3(WINDOW_SIDE_X, 0, UPPER_ROW_Z)", scene)
        self.assertIn("return new THREE.Vector3(DOOR_SIDE_X, 0, LOWER_ROW_Z)", scene)
        self.assertIn("WINDOW_SIDE_X, BUNK_UPPER_Y, LOWER_ROW_Z", scene)
        self.assertIn("WINDOW_SIDE_X, BUNK_LOWER_Y, LOWER_ROW_Z", scene)
        self.assertIn("return 0", scene)

        for expected in (
            "return { x: -2.35, z: -1.65, rotation: 0 }",
            "return { x: 2.35, z: -1.65, rotation: 0 }",
            "return { x: -2.35, z: 1.65, rotation: 0 }",
            "return { x: 2.35, z: 1.65, rotation: 0 }",
        ):
            self.assertIn(expected, editor)
        self.assertIn("new DefaultPlacement(-2.35, -1.65, 0)", service)
        self.assertIn("new DefaultPlacement(2.35, -1.65, 0)", service)
        self.assertIn("new DefaultPlacement(-2.35, 1.65, 0)", service)
        self.assertIn("new DefaultPlacement(2.35, 1.65, 0)", service)
        self.assertIn("left: 8px", styles)
        self.assertIn("right: 8px", styles)

    def test_three_scene_expands_to_fill_desktop_control_area(self) -> None:
        styles = SCENE_SIZE_STYLES.read_text(encoding="utf-8")
        self.assertIn("height: clamp(680px, calc(100vh - 220px), 940px)", styles)
        self.assertIn("min-height: 680px", styles)
        scene = SCENE.read_text(encoding="utf-8")
        self.assertIn("camera.fov = mobile ? 45 : compact ? 36 : 31", scene)
        self.assertIn("mobile ? -11.6 : -9.4", scene)
        self.assertIn("camera.lookAt(0, 1.0, 0)", scene)

    def test_three_scene_keeps_long_sides_open(self) -> None:
        scene = SCENE.read_text(encoding="utf-8")
        for forbidden in (
            "BACK_LONG_WALL_Z",
            "FRONT_LONG_WALL_Z",
            "backLongWall",
            "frontLongWall",
        ):
            self.assertNotIn(forbidden, scene)
        self.assertIn("const floor = new THREE.Mesh", scene)
        self.assertIn("addWindow()", scene)
        self.assertIn("addDoorFrame()", scene)
        self.assertIn("开放视角", scene)

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
        self.assertIn("room_bed_layout", workflow)


if __name__ == "__main__":
    unittest.main()
