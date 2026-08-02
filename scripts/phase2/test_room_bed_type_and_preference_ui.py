#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
ROOM_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
ROOM_MANAGEMENT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
ADMIN_VIEW = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
LAYOUT_STYLES = ROOT / "frontend/src/phase2-room-layout.css"
QUESTIONNAIRE = ROOT / "frontend/src/views/student/QuestionnaireView.vue"
STUDENT_STYLES = ROOT / "frontend/src/student-experience.css"


class RoomBedTypeAndPreferenceUiTest(unittest.TestCase):
    def test_layout_contract_accepts_bed_type(self) -> None:
        openapi = OPENAPI.read_text(encoding="utf-8")
        self.assertIn("required: [bedId, bedType, layoutX, layoutZ, rotationDegrees]", openapi)
        self.assertIn("bedType:", openapi)
        self.assertIn("LOFT_BED_DESK", openapi)
        self.assertIn("BUNK_UPPER", openapi)
        self.assertIn("BUNK_LOWER", openapi)

        controller = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("item.getBedType()", controller)

    def test_backend_rejects_occupied_bed_type_changes_and_syncs_room_type(self) -> None:
        service = ROOM_LAYOUT.read_text(encoding="utf-8")
        for expected in (
            "AS occupied",
            "BED_TYPE_OCCUPIED",
            "非空床位不能修改床位类型",
            "UPDATE bed SET bed_type=:bedType",
            "roomTypeForBedCount",
            "room_type=:roomType",
            "capacity=:capacity",
            "Set.of(\"LOFT_BED_DESK\", \"BUNK_UPPER\", \"BUNK_LOWER\")",
        ):
            self.assertIn(expected, service)

        management = ROOM_MANAGEMENT.read_text(encoding="utf-8")
        self.assertNotIn("SET room_type=:roomType", management)
        self.assertNotIn("validateRoomType(command.roomType()", management)

    def test_room_editor_no_longer_changes_room_type(self) -> None:
        admin = ADMIN_VIEW.read_text(encoding="utf-8")
        self.assertNotIn('v-model="editForm.roomType"', admin)
        self.assertIn("房型由床位布局编辑器自动同步", admin)
        self.assertIn("当前房型", admin)

    def test_visual_editor_changes_only_empty_beds_and_uses_large_rectangles(self) -> None:
        editor = EDITOR.read_text(encoding="utf-8")
        for expected in (
            "layout-bed-type-grid",
            "setBedType",
            "bed.occupied",
            ':disabled="saving || bed.occupied"',
            "非空床位不可修改类型",
            "bedType: bed.bed_type",
            "同步房型",
        ):
            self.assertIn(expected, editor)

        styles = LAYOUT_STYLES.read_text(encoding="utf-8")
        self.assertIn("width: min(1080px, calc(100vw - 32px))", styles)
        self.assertIn("min-height: 400px", styles)
        self.assertIn("width: 190px", styles)
        self.assertIn("height: 90px", styles)
        self.assertIn("width: 114px", styles)
        self.assertIn("height: 54px", styles)

    def test_preference_card_columns_stretch_and_header_gap_is_compact(self) -> None:
        styles = STUDENT_STYLES.read_text(encoding="utf-8")
        for expected in (
            ".personal-preference-card > .section-head",
            "gap: 10px",
            ".personal-preference-table-column",
            "height: 100%",
            "grid-auto-rows: 1fr",
            "grid-template-rows: minmax(0, 1fr) auto",
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


if __name__ == "__main__":
    unittest.main()
