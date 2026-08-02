#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-room-management.yaml"
MASTER_OPENAPI = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
ROOM_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
ROOM_MANAGEMENT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
LAYOUT_STYLES = ROOT / "frontend/src/phase2-room-layout.css"
QUESTIONNAIRE = ROOT / "frontend/src/views/student/QuestionnaireView.vue"
STUDENT_STYLES = ROOT / "frontend/src/student-experience.css"
ROOM_DETAIL = ROOT / "frontend/src/views/student/RoomDetailView.vue"
ROOM_SELECTION_STYLES = ROOT / "frontend/src/room-selection-refinement.css"


class RoomBedTypeAndPreferenceUiTest(unittest.TestCase):
    def test_layout_contract_accepts_bed_unit_type_and_room_edit_omits_room_type(self) -> None:
        openapi = OPENAPI.read_text(encoding="utf-8")
        master = MASTER_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("openapi-room-management.yaml", master)
        self.assertIn("required: [bedId, bedType, layoutX, layoutZ, rotationDegrees]", openapi)
        self.assertIn("required: [capacity, gender, operationalStatus, reason]", openapi)
        self.assertNotIn("required: [roomType, capacity", openapi)
        self.assertIn("enum: [LOFT_BED_DESK, BUNK]", openapi)
        self.assertNotIn("enum: [LOFT_BED_DESK, BUNK_UPPER, BUNK_LOWER]", openapi)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("item.getBedType().getValue()", controller)
        self.assertNotIn("request.getRoomType()", controller)

    def test_backend_rejects_occupied_changes_splits_bunks_and_syncs_room_type(self) -> None:
        service = ROOM_LAYOUT.read_text(encoding="utf-8")
        for expected in (
            "AS occupied",
            "BED_TYPE_OCCUPIED",
            "非空床位不能修改床位类型",
            "splitLoftIntoBunk",
            "INSERT INTO bed_frame",
            "BUNK_UPPER",
            "BUNK_LOWER",
            "INSERT IGNORE INTO batch_bed_scope",
            "ROOM_CAPACITY_LIMIT",
            "房间最多只能配置8个床位",
            "roomTypeForBedCount",
            "room_type=:roomType",
            "capacity=:capacity",
            "UNIT_TYPES",
        ):
            self.assertIn(expected, service)

        management = ROOM_MANAGEMENT.read_text(encoding="utf-8")
        self.assertNotIn("SET room_type=:roomType", management)
        self.assertNotIn("validateRoomType", management)
        self.assertNotIn("roomType,", management)

    def test_room_editor_no_longer_changes_room_type(self) -> None:
        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        self.assertNotIn('v-model="editForm.roomType"', admin)
        self.assertNotIn("roomTypeOptions", admin)
        self.assertIn("房型由床位布局编辑器自动同步", admin)
        self.assertIn("当前房型", admin)

    def test_visual_editor_uses_bed_units_and_large_rectangles(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        for expected in (
            "layout-bed-type-grid",
            "layoutUnits",
            "setUnitType",
            "unit.occupied",
            ':disabled="saving || unit.occupied"',
            "非空床位不可修改类型",
            "bedType: unit.unitType",
            "新增一个独立下铺床位",
            "同步房型",
            "最多8人",
        ):
            self.assertIn(expected, editor)
        self.assertNotIn("上下铺上铺", editor)
        self.assertNotIn("上下铺下铺", editor)

        styles = LAYOUT_STYLES.read_text(encoding="utf-8")
        self.assertIn("width: min(1040px, calc(100vw - 40px))", styles)
        self.assertIn("min-height: 360px", styles)
        self.assertIn("width: 304px", styles)
        self.assertIn("height: 144px", styles)
        self.assertIn("background: #ffffff", styles)
        self.assertIn("background: rgba(8, 22, 48, 0.72)", styles)

    def test_preference_card_columns_stretch_and_buttons_are_fixed(self) -> None:
        styles = STUDENT_STYLES.read_text(encoding="utf-8")
        for expected in (
            ".personal-preference-card > .section-head",
            "gap: 10px",
            ".personal-preference-table-column",
            "height: 100%",
            "grid-auto-rows: 1fr",
            "grid-template-rows: minmax(0, 1fr) auto",
            "grid-template-rows: repeat(2, minmax(56px, 56px))",
            "padding: 16px",
            "width: 100%",
        ):
            self.assertIn(expected, styles)

    def test_questionnaire_radio_choices_use_unique_tokens(self) -> None:
        questionnaire = QUESTIONNAIRE.read_text(encoding="utf-8")
        for expected in (
            "choiceToken",
            "choicePayload",
            "answerPayload",
            "normalizeSavedAnswer",
            ':key="choice.token"',
            ':value="choice.token"',
        ):
            self.assertIn(expected, questionnaire)
        self.assertNotIn(':key="String(choice.value)"', questionnaire)
        self.assertNotIn(':value="choice.value"', questionnaire)

    def test_temporary_hold_is_fixed_next_to_current_selection(self) -> None:
        detail = ROOM_DETAIL.read_text(encoding="utf-8")
        styles = ROOM_SELECTION_STYLES.read_text(encoding="utf-8")
        for expected in (
            "selection-overview-grid",
            "selection-hold-card",
            "尚未临时保留",
            "已临时保留",
            'v-if="holdToken" class="countdown compact-countdown"',
            ':disabled="!holdToken || submitting || remainingSeconds <= 0"',
        ):
            self.assertIn(expected, detail)
        self.assertNotIn("床位已临时保留；点击其他空床位可以直接切换床位。", detail)
        self.assertNotIn('v-if="holdToken" class="panel hold-panel bed-selection-action-bar"', detail)
        self.assertIn(".selection-overview-grid", styles)
        self.assertIn(".selection-hold-card", styles)


if __name__ == "__main__":
    unittest.main()
