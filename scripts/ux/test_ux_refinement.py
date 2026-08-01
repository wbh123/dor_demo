#!/usr/bin/env python3
from __future__ import annotations

import json
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND = REPO_ROOT / "frontend/src"
JAVA_ROOT = REPO_ROOT / "backend-java/server/src/main/java/com/wust/dormitory"
MIGRATION_ROOT = REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
DEV_MIGRATION_ROOT = REPO_ROOT / "backend-java/server/src/test/resources/db/dev-migration"


class UxRefinementTest(unittest.TestCase):
    def test_v4_defines_three_state_smoking_preference(self) -> None:
        migration = (
            MIGRATION_ROOT / "V4__refine_questionnaire_and_active_batch_rules.sql"
        ).read_text(encoding="utf-8")
        self.assertIn("question_type = 'SINGLE_CHOICE'", migration)
        for option in ("'ACCEPT', '接受'", "'REJECT', '不接受'", "'ANY', '均可'"):
            self.assertIn(option, migration)
        self.assertIn("JSON_QUOTE('ACCEPT')", migration)
        self.assertIn("JSON_QUOTE('REJECT')", migration)

    def test_v4_uses_low_privilege_unique_lock_table(self) -> None:
        migration = (
            MIGRATION_ROOT / "V4__refine_questionnaire_and_active_batch_rules.sql"
        ).read_text(encoding="utf-8")
        lifecycle = (JAVA_ROOT / "admin/BatchLifecycleService.java").read_text(encoding="utf-8")
        controller = (JAVA_ROOT / "admin/AdminController.java").read_text(encoding="utf-8")

        self.assertIn("CREATE TABLE active_batch_student_lock", migration)
        self.assertIn("PRIMARY KEY (student_id)", migration)
        self.assertIn("('PUBLISHED', 'OPEN', 'PAUSED')", migration)
        self.assertNotIn("CREATE TRIGGER", migration)

        self.assertIn("@Transactional", lifecycle)
        self.assertIn("INSERT INTO active_batch_student_lock", lifecycle)
        self.assertIn("DELETE FROM active_batch_student_lock", lifecycle)
        self.assertIn("DuplicateKeyException", lifecycle)
        self.assertIn("BATCH_STUDENT_ACTIVE_CONFLICT", lifecycle)
        self.assertIn("batchLifecycleService.changeStatus", controller)

    def test_development_data_finishes_with_three_state_preferences(self) -> None:
        refinement = (
            DEV_MIGRATION_ROOT / "R__zz_refine_development_questionnaire.sql"
        ).read_text(encoding="utf-8")
        self.assertIn("ELT(1 + MOD(student_id, 3), 'ACCEPT', 'REJECT', 'ANY')", refinement)
        self.assertIn("assert_development_smoking_preferences", refinement)

    def test_matching_treats_any_as_non_conflicting(self) -> None:
        matching = (JAVA_ROOT / "matching/MatchingService.java").read_text(encoding="utf-8")
        self.assertIn("private boolean smokingConflict", matching)
        self.assertIn('"ACCEPT".equals(leftValue) && "REJECT".equals(rightValue)', matching)
        self.assertIn('"REJECT".equals(leftValue) && "ACCEPT".equals(rightValue)', matching)
        self.assertIn('left == null ? "ANY"', matching)

    def test_admin_room_filters_refresh_and_editor_is_modal(self) -> None:
        content = (FRONTEND / "views/admin/AdminDormitoryView.vue").read_text(encoding="utf-8")
        self.assertIn("watch([buildingId, gender]", content)
        self.assertIn("modal-overlay", content)
        self.assertIn('role="dialog"', content)
        self.assertIn("openRoomEditor", content)
        self.assertNotIn(">筛选</button>", content)

    def test_student_shell_and_home_hide_technical_details(self) -> None:
        shell = (FRONTEND / "layouts/AppShell.vue").read_text(encoding="utf-8")
        home = (FRONTEND / "views/student/StudentHomeView.vue").read_text(encoding="utf-8")
        self.assertNotIn("系统服务正常", shell)
        for forbidden in ("batch_code", "hold_duration_seconds", "MySQL", "state_version"):
            self.assertNotIn(forbidden, home)
        self.assertIn("answerSummary", home)
        self.assertIn("我的住宿结果", home)
        self.assertIn("修改问卷", home)

    def test_questionnaire_uses_three_radio_choices(self) -> None:
        content = (FRONTEND / "views/student/QuestionnaireView.vue").read_text(encoding="utf-8")
        for value in ("ACCEPT", "REJECT", "ANY"):
            self.assertIn(value, content)
        self.assertIn("question.options", content)
        self.assertIn('type="radio"', content)
        self.assertNotIn("feature_key }}</p>", content)

    def test_room_cards_show_anonymous_roommate_preferences(self) -> None:
        content = (FRONTEND / "views/student/RoomListView.vue").read_text(encoding="utf-8")
        self.assertIn("compact-room-grid", content)
        self.assertIn("roommate-summary", content)
        self.assertIn("室友偏好", content)
        self.assertIn("assigned_count", content)
        self.assertNotIn("student_name", content)
        self.assertNotIn("student_number", content)

    def test_threejs_dependency_and_scene_component_exist(self) -> None:
        package_json = json.loads((REPO_ROOT / "frontend/package.json").read_text(encoding="utf-8"))
        self.assertIn("three", package_json["dependencies"])

        scene_path = FRONTEND / "components/student/RoomBedScene3D.vue"
        self.assertTrue(scene_path.exists())
        scene = scene_path.read_text(encoding="utf-8")
        self.assertIn("import * as THREE from 'three'", scene)
        self.assertIn("new THREE.Raycaster()", scene)
        self.assertIn("new ResizeObserver", scene)
        self.assertIn("BUNK_UPPER", scene)
        self.assertIn("BUNK_LOWER", scene)
        self.assertIn("emit('select'", scene)
        self.assertIn("prefers-reduced-motion", scene)

    def test_room_detail_has_dropdown_and_direct_switching(self) -> None:
        content = (FRONTEND / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        self.assertIn("RoomBedScene3D", content)
        self.assertIn("bed-select-control", content)
        self.assertIn("selectFromDropdown", content)
        self.assertIn("switchIndividualBed", content)
        self.assertIn("releaseIndividualHold", content)
        self.assertIn("切换床位", content)
        self.assertIn(":selected-bed-ids=\"selectedBedIds\"", content)
        self.assertNotIn("bedPlacement", content)
        self.assertNotIn("Boolean(holdToken)", content)
        self.assertNotIn("room.state_version", content)

    def test_scene_selection_is_high_contrast_and_mobile_friendly(self) -> None:
        content = (FRONTEND / "ux-refinement.css").read_text(encoding="utf-8")
        for selector in (
            ".bed-select-control",
            ".three-bed-scene",
            ".selected-bed-summary",
            ".bed-selection-action-bar",
            ".three-scene-selected",
        ):
            self.assertIn(selector, content)
        self.assertIn("@media (max-width: 640px)", content)
        self.assertIn("position: sticky", content)
        self.assertIn("prefers-reduced-motion", content)


if __name__ == "__main__":
    unittest.main()
