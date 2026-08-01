#!/usr/bin/env python3
from __future__ import annotations

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

    def test_room_detail_uses_visual_scene_without_state_version(self) -> None:
        content = (FRONTEND / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        self.assertIn("room-scene", content)
        self.assertIn("room-window", content)
        self.assertIn("bedPlacement", content)
        self.assertIn("bunk-window-upper", content)
        self.assertIn("bunk-window-lower", content)
        self.assertNotIn("room.state_version", content)

    def test_responsive_scene_styles_exist(self) -> None:
        content = (FRONTEND / "ux-refinement.css").read_text(encoding="utf-8")
        self.assertIn(".room-scene", content)
        self.assertIn(".scene-bed.bunk-window-upper", content)
        self.assertIn("@media (max-width: 640px)", content)
        self.assertIn(".modal-overlay", content)


if __name__ == "__main__":
    unittest.main()
