#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V5__add_room_bed_layout.sql"
SCHEMA = ROOT / "backend-java/docs/sql/schema.sql"
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-room-management.yaml"
MASTER_OPENAPI = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
ROOM_MANAGEMENT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
STUDENT_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomLayoutService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
SCENE = ROOT / "frontend/src/components/student/RoomBedScene3D.vue"
STYLES = ROOT / "frontend/src/phase2-room-layout.css"
CANVAS_STYLES = ROOT / "frontend/src/admin-layout-canvas-refinement.css"
SCENE_SIZE_STYLES = ROOT / "frontend/src/room-scene-geometry-fix.css"
MAIN = ROOT / "frontend/src/main.ts"
SMOKE = ROOT / "scripts/e2e/phase2_room_layout_smoke.py"
WORKFLOW = ROOT / ".github/workflows/phase1-ci.yml"


class RoomLayoutPhaseTwoTest(unittest.TestCase):
    def test_v5_creates_normalized_room_bed_layout(self) -> None:
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
        self.assertIn("CREATE TABLE room_bed_layout", SCHEMA.read_text(encoding="utf-8"))

    def test_openapi_exposes_layout_unit_types(self) -> None:
        content = OPENAPI.read_text(encoding="utf-8")
        master = MASTER_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("openapi-room-management.yaml", master)
        self.assertIn("/api/v1/admin/rooms/{roomId}/bed-layout:", content)
        self.assertIn("operationId: getRoomBedLayout", content)
        self.assertIn("operationId: updateRoomBedLayout", content)
        for field in ("expectedRoomVersion", "reason", "beds", "bedId", "bedType", "layoutX", "layoutZ", "rotationDegrees"):
            self.assertIn(field, content)
        self.assertIn("enum: [LOFT_BED_DESK, BUNK]", content)
        self.assertIn("maximum: 8", content)

    def test_backend_has_transactional_split_and_capacity_protection(self) -> None:
        service = SERVICE.read_text(encoding="utf-8")
        for expected in (
            "class RoomLayoutService",
            "public Map<String, Object> getLayout",
            "public Map<String, Object> updateLayout",
            "@Transactional",
            "ROOM_LAYOUT_BED_MISMATCH",
            "ROOM_LAYOUT_OUT_OF_BOUNDS",
            "ROOM_LAYOUT_ROTATION_INVALID",
            "ROOM_LAYOUT_VERSION_CONFLICT",
            "BED_TYPE_OCCUPIED",
            "BUNK_COLLAPSE_NOT_SUPPORTED",
            "ROOM_CAPACITY_LIMIT",
            "splitLoftIntoBunk",
            "INSERT INTO bed_frame",
            "BUNK_UPPER",
            "BUNK_LOWER",
            "INSERT IGNORE INTO batch_bed_scope",
            "ROOM_LAYOUT_UPDATE",
            "DEFAULT_LAYOUT",
            "roomTypeForBedCount",
        ):
            self.assertIn(expected, service)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("getRoomBedLayout", controller)
        self.assertIn("updateRoomBedLayout", controller)
        self.assertIn("item.getBedType().getValue()", controller)
        self.assertNotIn("@GetMapping", controller)
        self.assertNotIn("@PutMapping", controller)

    def test_student_room_snapshot_returns_layout_fields(self) -> None:
        content = STUDENT_LAYOUT.read_text(encoding="utf-8")
        self.assertIn("LEFT JOIN room_bed_layout", content)
        self.assertIn("layout.layout_x", content)
        self.assertIn("layout.layout_z", content)
        self.assertIn("layout.rotation_degrees", content)
        self.assertIn("roomLayoutService.enrich", STUDENT_CONTROLLER.read_text(encoding="utf-8"))

    def test_admin_visual_editor_keeps_all_controls_inside_canvas(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        for expected in (
            "room-layout-stage",
            "layoutUnits",
            "bed_frame_id",
            "startDrag",
            "pointermove",
            "snapCoordinate",
            "cycleRotation",
            "layout-bed-type-actions",
            "layout-bed-rotate-button",
            "setUnitType(unit, 'LOFT_BED_DESK')",
            "setUnitType(unit, 'BUNK')",
            "restoreDefaultLayout",
            "expectedRoomVersion",
            "修改原因",
            "bedType: unit.unitType",
            "新增一个独立下铺床位",
            "最多8人",
        ):
            self.assertIn(expected, editor)
        for forbidden in (
            "layout-number-grid",
            "layout-bed-type-grid",
            "横向位置X",
            "纵向位置Z",
            "<select",
            "上下铺上铺",
            "上下铺下铺",
        ):
            self.assertNotIn(forbidden, editor)

        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        self.assertIn("RoomLayoutEditor", admin)
        self.assertIn("openLayoutEditor", admin)
        self.assertIn(">布局</button>", admin)

        styles = CANVAS_STYLES.read_text(encoding="utf-8")
        self.assertIn(".room-layout-stage", styles)
        self.assertIn(".layout-bed-type-actions", styles)
        self.assertIn(".layout-bed-rotate-button", styles)
        self.assertIn("@media (max-width: 640px)", styles)
        main = MAIN.read_text(encoding="utf-8")
        self.assertIn("./phase2-room-layout.css", main)
        self.assertIn("./room-scene-geometry-fix.css", main)
        self.assertIn("./admin-layout-canvas-refinement.css", main)

    def test_admin_room_editor_uses_physical_bed_count_and_readonly_room_type(self) -> None:
        service = ROOM_MANAGEMENT.read_text(encoding="utf-8")
        self.assertIn("class RoomManagementService", service)
        self.assertIn("SELECT COUNT(*) FROM bed WHERE room_id=:id", service)
        self.assertNotIn("room_id=:id AND operational_status='ENABLED'", service)
        self.assertIn("房间容量必须等于当前床位总数", service)
        self.assertNotIn("SET room_type=:roomType", service)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("roomManagementService.rooms", controller)
        self.assertIn("roomManagementService.updateRoom", controller)
        self.assertNotIn("request.getRoomType()", controller)

        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        for expected in (
            "physicalBedCount",
            "room-editor-dialog",
            "room-editor-summary",
            "room-capacity-field",
            "room-readonly-field",
            "当前房型",
            "房型由床位布局编辑器自动同步",
            "readonly",
            "容量由物理床位总数决定",
        ):
            self.assertIn(expected, admin)
        self.assertNotIn("roomTypeOptions", admin)

    def test_layout_editor_uses_opaque_rounded_dialog_and_inline_cards(self) -> None:
        styles = CANVAS_STYLES.read_text(encoding="utf-8")
        for expected in (
            "padding: 30px",
            "border-radius: 26px",
            "min-height: 430px",
            "width: 310px",
            "min-height: 176px",
            "background: #ffffff",
            "background: rgba(9, 23, 48, 0.78)",
            ".layout-bed-type-actions",
            ".layout-bed-rotate-button",
        ):
            self.assertIn(expected, styles)
        base_styles = STYLES.read_text(encoding="utf-8")
        self.assertIn(".room-editor-dialog", base_styles)
        self.assertIn(".room-editor-summary", base_styles)

    def test_drag_coordinate_snapping_never_escapes_backend_bounds(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("const snapped = Math.round(value / SNAP) * SNAP", editor)
        self.assertIn("return Math.min(maximum, Math.max(minimum, snapped))", editor)

    def test_save_button_reports_missing_reason(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("请填写布局修改原因后再保存", editor)
        self.assertIn("reasonInput.value?.focus()", editor)
        self.assertIn(':disabled="saving"', editor)
        self.assertNotIn(':disabled="saving || !reason.trim()"', editor)

    def test_default_layout_coordinates_are_stable(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        service = SERVICE.read_text(encoding="utf-8")
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

    def test_three_scene_expands_to_fill_desktop_control_area(self) -> None:
        styles = SCENE_SIZE_STYLES.read_text(encoding="utf-8")
        self.assertIn("height: clamp(680px, calc(100vh - 220px), 940px)", styles)
        scene = SCENE.read_text(encoding="utf-8")
        self.assertIn("camera.fov = mobile ? 45 : compact ? 36 : 31", scene)
        self.assertIn("camera.lookAt(0, 1.0, 0)", scene)

    def test_runtime_smoke_is_connected_to_ci(self) -> None:
        self.assertTrue(SMOKE.is_file())
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m unittest scripts/phase2/test_room_layout.py -v", workflow)
        self.assertIn("python -m unittest scripts/phase2/test_room_bed_type_and_preference_ui.py -v", workflow)
        self.assertIn("python -m unittest scripts/phase2/test_admin_layout_allocation_student_reset.py -v", workflow)
        self.assertIn("python scripts/e2e/phase2_room_layout_smoke.py", workflow)
        self.assertIn("room_bed_layout", workflow)


if __name__ == "__main__":
    unittest.main()
