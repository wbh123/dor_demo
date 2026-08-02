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
OPENAPI_STUDENT = REPO_ROOT / "backend-java/model/src/main/resources/student/openapi-student.yaml"


class UxRefinementTest(unittest.TestCase):
    def test_v4_defines_three_state_smoking_preference(self) -> None:
        migration = (MIGRATION_ROOT / "V4__refine_questionnaire_and_active_batch_rules.sql").read_text(encoding="utf-8")
        self.assertIn("question_type = 'SINGLE_CHOICE'", migration)
        for option in ("'ACCEPT', '接受'", "'REJECT', '不接受'", "'ANY', '均可'"):
            self.assertIn(option, migration)
        self.assertIn("JSON_QUOTE('ACCEPT')", migration)
        self.assertIn("JSON_QUOTE('REJECT')", migration)

    def test_v4_uses_low_privilege_unique_lock_table(self) -> None:
        migration = (MIGRATION_ROOT / "V4__refine_questionnaire_and_active_batch_rules.sql").read_text(encoding="utf-8")
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
        refinement = (DEV_MIGRATION_ROOT / "R__zz_refine_development_questionnaire.sql").read_text(encoding="utf-8")
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
        self.assertIn("修改个人偏好", home)
        self.assertNotIn("slice(0, 8)", home)

    def test_personal_preferences_are_complete_and_share_one_profile_card(self) -> None:
        home = (FRONTEND / "views/student/StudentHomeView.vue").read_text(encoding="utf-8")
        styles = (FRONTEND / "student-experience.css").read_text(encoding="utf-8")
        for expected in (
            "preferenceProfileSummary",
            "preferenceProfileTags",
            "personal-preference-card",
            "personal-preference-list",
            "student-primary-actions",
            "选择宿舍和床位",
            "组队选寝",
        ):
            self.assertIn(expected, home + styles)
        self.assertNotIn("preference-summary-item", home)
        self.assertIn("min-height: 56px", styles)

    def test_questionnaire_page_uses_personal_preference_wording(self) -> None:
        content = (FRONTEND / "views/student/QuestionnaireView.vue").read_text(encoding="utf-8")
        for value in ("ACCEPT", "REJECT", "ANY"):
            self.assertIn(value, content)
        self.assertIn("question.options", content)
        self.assertIn('type="radio"', content)
        self.assertNotIn("feature_key }}</p>", content)
        self.assertIn("个人偏好", content)
        self.assertNotIn("生活习惯问卷", content)
        self.assertIn("isQuestionVisible", content)
        self.assertIn("isRequired", content)

    def test_room_cards_show_anonymous_roommate_preferences(self) -> None:
        content = (FRONTEND / "views/student/RoomListView.vue").read_text(encoding="utf-8")
        self.assertIn("compact-room-grid", content)
        self.assertIn("roommate-summary", content)
        self.assertIn("室友偏好", content)
        self.assertIn("assigned_count", content)
        self.assertNotIn("student_name", content)
        self.assertNotIn("student_number", content)

    def test_room_list_filters_floor_and_minimum_available_beds(self) -> None:
        content = (FRONTEND / "views/student/RoomListView.vue").read_text(encoding="utf-8")
        for expected in (
            "floorFilter",
            "minimumAvailableBeds",
            "floorOptions",
            "Number(room.floor_number) !== Number(floorFilter.value)",
            "Number(room.availableCount) < minimumAvailableBeds.value",
            "筛选楼层",
            "最少剩余铺位",
        ):
            self.assertIn(expected, content)

    def test_threejs_dependency_and_scene_component_exist(self) -> None:
        package_json = json.loads((REPO_ROOT / "frontend/package.json").read_text(encoding="utf-8"))
        self.assertIn("three", package_json["dependencies"])
        scene_path = FRONTEND / "components/student/RoomBedScene3D.vue"
        self.assertTrue(scene_path.exists())
        scene = scene_path.read_text(encoding="utf-8")
        for expected in (
            "import * as THREE from 'three'",
            "new THREE.Raycaster()",
            "new ResizeObserver",
            "BUNK_UPPER",
            "BUNK_LOWER",
            "emit('select'",
            "prefers-reduced-motion",
        ):
            self.assertIn(expected, scene)

    def test_room_detail_has_dropdown_and_direct_switching(self) -> None:
        content = (FRONTEND / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        for expected in (
            "RoomBedScene3D",
            "bed-select-control",
            "selectFromDropdown",
            "switchIndividualBed",
            "releaseIndividualHold",
            "切换床位",
            ':selected-bed-ids="selectedBedIds"',
        ):
            self.assertIn(expected, content)
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

    def test_scene_uses_open_oblique_camera_and_hides_orientation_prompts(self) -> None:
        scene = (FRONTEND / "components/student/RoomBedScene3D.vue").read_text(encoding="utf-8")
        styles = (FRONTEND / "student-experience.css").read_text(encoding="utf-8")
        for expected in (
            "const ROOM_WIDTH = 11.5",
            "const ROOM_DEPTH = 8",
            "const DOOR_SIDE_X = -2.35",
            "const WINDOW_SIDE_X = 2.35",
            "const UPPER_ROW_Z = -1.65",
            "const LOWER_ROW_Z = 1.65",
            "const LEFT_SHORT_WALL_X",
            "const RIGHT_SHORT_WALL_X",
            "defaultBedRotation",
            "customBedPlacement",
            "window.position.set(RIGHT_SHORT_WALL_X",
            "doorFrame.position.set(LEFT_SHORT_WALL_X",
            "mobile ? -11.6 : -9.4",
            "camera.lookAt(0, 1.0, 0)",
        ):
            self.assertIn(expected, scene)
        for forbidden in (
            "BACK_LONG_WALL_Z",
            "FRONT_LONG_WALL_Z",
            "backLongWall",
            "frontLongWall",
        ):
            self.assertNotIn(forbidden, scene)
        self.assertIn(".three-scene-orientation", styles)
        self.assertIn("display: none !important", styles)
        self.assertNotIn("const BED_LONGITUDINAL_ROTATION = Math.PI / 2", scene)

    def test_room_detail_keeps_only_four_bed_status_bubbles(self) -> None:
        detail = (FRONTEND / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        for expected in ("可选择", "已选中", "暂时保留", "已有同学选择"):
            self.assertIn(expected, detail)
        for forbidden in (
            "窗户正对入口",
            "床位变化会自动更新",
            "C床与上下铺位于同一前排",
            "下拉框与三维图形同步",
            "可点击三维床位，或使用左侧下拉框",
            "room.remark",
        ):
            self.assertNotIn(forbidden, detail)
        legend = detail.split('class="scene-legend compact-scene-legend"', 1)[1].split("</div>", 1)[0]
        self.assertEqual(legend.count("<span"), 4)

    def test_scene_canvas_zoom_makes_beds_fill_more_of_the_viewport(self) -> None:
        styles = (FRONTEND / "room-scene-geometry-fix.css").read_text(encoding="utf-8")
        self.assertIn(".three-bed-scene-canvas", styles)
        self.assertIn("transform: scale(1.22)", styles)
        self.assertIn("transform-origin: 50% 48%", styles)
        self.assertIn("transform: scale(1.12)", styles)

    def test_student_welcome_modal_is_persistent_and_animated(self) -> None:
        shell = (FRONTEND / "layouts/AppShell.vue").read_text(encoding="utf-8")
        store = (FRONTEND / "stores/auth.ts").read_text(encoding="utf-8")
        styles = (FRONTEND / "student-experience.css").read_text(encoding="utf-8")
        for expected in (
            "新同学，欢迎你",
            "welcome-overlay",
            "welcome-dialog",
            "acknowledgeWelcome",
            "/api/v1/auth/welcome/acknowledge",
            "prefers-reduced-motion",
        ):
            self.assertIn(expected, shell + store + styles)
        self.assertIn('v-if="auth.isAdmin" class="topbar"', shell)
        self.assertNotIn("学生选寝中心", shell)

    def test_admin_dashboard_can_edit_student_welcome_message(self) -> None:
        dashboard = (FRONTEND / "views/admin/AdminDashboardView.vue").read_text(encoding="utf-8")
        for expected in (
            "/api/v1/admin/settings/student-welcome",
            "welcomeMessage",
            "welcomeVersion",
            "新生欢迎语",
            "保存欢迎语",
        ):
            self.assertIn(expected, dashboard)

    def test_release_message_is_three_second_toast_and_layout_is_compact(self) -> None:
        detail = (FRONTEND / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        styles = (FRONTEND / "room-selection-refinement.css").read_text(encoding="utf-8")
        for expected in ("toastMessage", "showToast", "window.setTimeout", "3000", "selection-toast"):
            self.assertIn(expected, detail)
        self.assertNotIn("message.value = '已释放当前选择，可以重新选择床位。'", detail)
        self.assertIn(".room-detail-page", styles)
        self.assertIn("calc(100vh -", styles)
        self.assertIn(".three-bed-scene-mount", styles)

    def test_team_flow_is_invitation_first_and_hides_internal_identity(self) -> None:
        team_view = (FRONTEND / "views/student/TeamView.vue").read_text(encoding="utf-8")
        openapi = OPENAPI_STUDENT.read_text(encoding="utf-8")
        controller = (JAVA_ROOT / "student/StudentController.java").read_text(encoding="utf-8")
        team_service = (JAVA_ROOT / "student/TeamService.java").read_text(encoding="utf-8")

        for forbidden in ("teamName", "team_name", "team_code", "创建新队伍", "创建队伍"):
            self.assertNotIn(forbidden, team_view)
        self.assertIn("/api/v1/student/team-invitations", team_view)
        self.assertIn("小组成员", team_view)
        self.assertIn("team.members", team_view)
        self.assertIn("operationId: inviteTeammate", openapi)
        self.assertNotIn("operationId: createTeam", openapi)
        self.assertNotIn("TeamCreateRequest:", openapi)
        self.assertIn("teamService.inviteTeammate", controller)
        self.assertIn("ensureFormingLeaderTeam", team_service)
        self.assertIn('team.put("members"', team_service)

        teams_method = team_service.split("public List<Map<String, Object>> teams", 1)[1]
        teams_method = teams_method.split("public List<Map<String, Object>> invitations", 1)[0]
        self.assertNotIn("team_code", teams_method)
        self.assertNotIn("team_name", teams_method)


if __name__ == "__main__":
    unittest.main()
