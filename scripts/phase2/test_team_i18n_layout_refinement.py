#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEAM_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java"
STUDENT_PROFILE_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentProfileService.java"
STUDENT_ADMIN_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
STUDENT_OPENAPI = ROOT / "backend-java/model/src/main/resources/student/openapi-student.yaml"
ADMIN_STUDENT_OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-student-management.yaml"
ROOM_OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-room-management.yaml"
ROOM_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
WELCOME_SETTING = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java"
WELCOME_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java"
AUTH_OPENAPI = ROOT / "backend-java/model/src/main/resources/auth/openapi-auth.yaml"
MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V9__add_student_contact_and_notifications.sql"
RESET_SEED = ROOT / "backend-java/docs/sql/reset_and_seed_test_data.sql"
I18N = ROOT / "frontend/src/i18n/index.ts"
APP_SHELL = ROOT / "frontend/src/layouts/AppShell.vue"
STUDENT_HOME = ROOT / "frontend/src/views/student/StudentHomeView.vue"
TEAM_VIEW = ROOT / "frontend/src/views/student/TeamView.vue"
ROOM_LIST = ROOT / "frontend/src/views/student/RoomListView.vue"
LAYOUT_EDITOR = ROOT / "frontend/src/components/admin/RoomLayoutEditor.vue"
LAYOUT_STYLE = ROOT / "frontend/src/phase2-room-layout.css"
TEAM_STYLE = ROOT / "frontend/src/team-i18n-refinement.css"
ADMIN_DASHBOARD = ROOT / "frontend/src/views/admin/AdminDashboardView.vue"
ADMIN_DATA = ROOT / "frontend/src/views/admin/AdminDataView.vue"


class TeamI18nLayoutRefinementTest(unittest.TestCase):
    def test_team_backend_invalidates_pending_invites_supports_removal_and_leaving(self) -> None:
        service = TEAM_SERVICE.read_text(encoding="utf-8")
        for expected in (
            "MAX_TEAM_SIZE = 5",
            "TEAM_SIZE_LIMIT",
            "pending_invitation_count",
            "confirmed_member_count",
            "cancelPendingInvitations",
            "invitation_status='CANCELLED'",
            "member_status='REMOVED'",
            "public Map<String, Object> removeMember",
            "public Map<String, Object> leaveTeam",
            "public Map<String, Object> preparePersonalSelection",
            "TEAM_DISSOLVED_BY_LEADER",
            "TEAM_MEMBER_REMOVED",
            "createNotification",
        ):
            self.assertIn(expected, service)

        controller = STUDENT_CONTROLLER.read_text(encoding="utf-8")
        for expected in ("removeTeamMember", "leaveTeam", "preparePersonalSelection", "listNotifications"):
            self.assertIn(expected, controller)

        openapi = STUDENT_OPENAPI.read_text(encoding="utf-8")
        for expected in (
            "/api/v1/student/teams/{teamId}/members/{studentId}:",
            "/api/v1/student/teams/{teamId}/leave:",
            "/api/v1/student/batches/{batchId}/personal-selection/prepare:",
            "/api/v1/student/notifications:",
            "operationId: removeTeamMember",
            "operationId: leaveTeam",
            "operationId: preparePersonalSelection",
            "operationId: listNotifications",
        ):
            self.assertIn(expected, openapi)

    def test_home_and_team_pages_cover_invitation_notifications_and_five_member_layout(self) -> None:
        home = STUDENT_HOME.read_text(encoding="utf-8")
        i18n = I18N.read_text(encoding="utf-8")
        for expected in (
            "team-invitations",
            "notifications",
            "homeInvitation",
            "dismissHomeInvitation",
            "respondHomeInvitation",
            "phone_number",
            "nationality_code",
            "savePhoneNumber",
        ):
            self.assertIn(expected, home)
        self.assertIn("暂不确认", i18n)

        team = TEAM_VIEW.read_text(encoding="utf-8")
        for expected in (
            "pending_invitation_count",
            "confirmed_member_count",
            "showStartSelectionConfirm",
            "removeMember",
            "leaveTeam",
            "team-member-slot-grid",
            "Array.from({ length: 5",
        ):
            self.assertIn(expected, team)
        self.assertIn("未确认邀请将失效", i18n)

        room_list = ROOM_LIST.read_text(encoding="utf-8")
        self.assertIn("personal-selection/prepare", room_list)
        self.assertIn("进入个人选寝将退出当前队伍", i18n)

        styles = TEAM_STYLE.read_text(encoding="utf-8")
        self.assertIn("grid-template-columns: repeat(5, minmax(0, 1fr))", styles)
        self.assertIn("aspect-ratio: 1 / 0.94", styles)

    def test_student_data_model_has_nationality_phone_notifications_and_reset_seed(self) -> None:
        migration = MIGRATION.read_text(encoding="utf-8")
        for expected in (
            "nationality_code",
            "phone_number",
            "CREATE TABLE student_notification",
            "TEAM_MEMBER_REMOVED",
            "DEFAULT 'CN'",
        ):
            self.assertIn(expected, migration)

        profile_service = STUDENT_PROFILE_SERVICE.read_text(encoding="utf-8")
        self.assertIn("nationality_code", profile_service)
        self.assertIn("phone_number", profile_service)
        self.assertIn("updatePhoneNumber", profile_service)

        admin_service = STUDENT_ADMIN_SERVICE.read_text(encoding="utf-8")
        self.assertIn("nationalityCode", admin_service)
        self.assertIn("phoneNumber", admin_service)

        openapi = ADMIN_STUDENT_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("nationalityCode", openapi)
        self.assertIn("phoneNumber", openapi)
        self.assertIn("pattern: '^\\d{12}$'", openapi)

        seed = RESET_SEED.read_text(encoding="utf-8")
        for expected in (
            "preserved_admin_account",
            "clear_all_business_data",
            "flyway_schema_history",
            "202600000001",
            "nationality_code",
            "international_student_count",
        ):
            self.assertIn(expected, seed)

    def test_frontend_has_country_aware_language_switch_vectors_and_reversed_subtitles(self) -> None:
        content = I18N.read_text(encoding="utf-8")
        for expected in (
            "zh-CN",
            "en-US",
            "useI18n",
            "setLocale",
            "applyNationalityLocale",
            "countryLanguageMap",
            "subtitle",
            "translateError",
            "localStorage",
        ):
            self.assertIn(expected, content)

        shell = APP_SHELL.read_text(encoding="utf-8")
        self.assertIn("language-switcher", shell)
        self.assertIn("nav-svg-icon", shell)
        self.assertIn("setLocale", shell)
        self.assertIn("welcome?.messages", shell)
        self.assertIn("subtitle(", shell)
        self.assertNotIn('class="avatar"', shell)

    def test_foreign_nationality_and_phone_are_visible_and_admin_editable(self) -> None:
        home = STUDENT_HOME.read_text(encoding="utf-8")
        self.assertIn("isForeignStudent", home)
        self.assertIn("nationality-chip", home)
        self.assertIn("countryName(profile.nationality_code)", home)
        self.assertIn("savePhoneNumber", home)

        admin = ADMIN_DATA.read_text(encoding="utf-8")
        self.assertIn("nationalityCode", admin)
        self.assertIn("phoneNumber", admin)
        self.assertIn("countryName(student.nationality_code)", admin)

    def test_welcome_setting_is_multilingual_and_respects_storage_limit(self) -> None:
        setting = WELCOME_SETTING.read_text(encoding="utf-8")
        welcome = WELCOME_SERVICE.read_text(encoding="utf-8")
        admin_openapi = (ROOT / "backend-java/model/src/main/resources/admin/openapi-system-setting.yaml").read_text(encoding="utf-8")
        auth_openapi = AUTH_OPENAPI.read_text(encoding="utf-8")
        dashboard = ADMIN_DASHBOARD.read_text(encoding="utf-8")

        for expected in (
            "Map<String, String> messages",
            "readMessages",
            "MAX_STORED_VALUE_LENGTH = 1000",
            "serializedMessages.length() > MAX_STORED_VALUE_LENGTH",
            ".addValue(\"messages\", serializedMessages)",
            'List.of("zh-CN", "en-US")',
        ):
            self.assertIn(expected, setting)
        self.assertIn("messages", welcome)
        self.assertIn("required: [messages, expectedVersion]", admin_openapi)
        self.assertIn("messages:", admin_openapi)
        self.assertIn("messages:", auth_openapi)
        self.assertIn("welcomeMessages", dashboard)
        self.assertIn("zh-CN", dashboard)
        self.assertIn("en-US", dashboard)

    def test_layout_backend_splits_loft_into_bunk_and_limits_capacity(self) -> None:
        service = ROOM_LAYOUT.read_text(encoding="utf-8")
        for expected in (
            '"BUNK"',
            "ROOM_CAPACITY_LIMIT",
            "房间最多只能配置8个床位",
            "splitLoftIntoBunk",
            "INSERT INTO bed_frame",
            "BUNK_UPPER",
            "BUNK_LOWER",
            "INSERT IGNORE INTO batch_bed_scope",
        ):
            self.assertIn(expected, service)

        openapi = ROOM_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("enum: [LOFT_BED_DESK, BUNK]", openapi)

    def test_layout_editor_uses_bed_units_large_cards_and_opaque_dialog(self) -> None:
        editor = LAYOUT_EDITOR.read_text(encoding="utf-8")
        for expected in (
            "LOFT_BED_DESK",
            "BUNK",
            "最多8人",
            "新增一个独立下铺床位",
            "layoutUnits",
        ):
            self.assertIn(expected, editor)
        self.assertNotIn("上下铺上铺", editor)
        self.assertNotIn("上下铺下铺", editor)

        styles = LAYOUT_STYLE.read_text(encoding="utf-8")
        self.assertIn("width: 304px", styles)
        self.assertIn("height: 144px", styles)
        self.assertIn("background: #ffffff", styles)
        self.assertIn("background: rgba(8, 22, 48, 0.72)", styles)


if __name__ == "__main__":
    unittest.main()
