#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND = REPO_ROOT / "frontend/src"
JAVA_ROOT = REPO_ROOT / "backend-java/server/src/main/java/com/wust/dormitory"
MIGRATION_ROOT = REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
MODEL_ROOT = REPO_ROOT / "backend-java/model/src/main/resources"


class StudentExperienceTest(unittest.TestCase):
    def test_welcome_database_model_is_persistent_and_configurable(self) -> None:
        migration = (MIGRATION_ROOT / "V7__add_student_welcome_settings.sql").read_text(encoding="utf-8")
        self.assertIn("welcome_acknowledged_at", migration)
        self.assertIn("CREATE TABLE system_setting", migration)
        self.assertIn("STUDENT_WELCOME_MESSAGE", migration)
        self.assertIn("version INT NOT NULL DEFAULT 0", migration)

    def test_personal_preference_v8_adds_high_impact_roommate_dimensions(self) -> None:
        migration = (MIGRATION_ROOT / "V8__expand_personal_preferences.sql").read_text(encoding="utf-8")
        for code in (
            "SUMMER_AC_OVERNIGHT",
            "SUMMER_AC_TEMPERATURE",
            "WINTER_HEATING_ACCEPTANCE",
            "WINTER_HEATING_TEMPERATURE",
            "AFTER_LIGHTS_ACTIVITY",
            "ALARM_SNOOZE",
            "STRONG_FOOD_ODOR_ACCEPTANCE",
        ):
            self.assertIn(code, migration)
        self.assertIn("'INTEGER', 'winterHeatingTemperature', 0, 11", migration)
        self.assertIn("AC_TEMPERATURE", migration)
        self.assertIn("个人偏好", migration)

    def test_welcome_openapi_has_student_ack_and_admin_setting_endpoints(self) -> None:
        master = (MODEL_ROOT / "openapi-interface.yaml").read_text(encoding="utf-8")
        auth = (MODEL_ROOT / "auth/openapi-auth.yaml").read_text(encoding="utf-8")
        welcome = (MODEL_ROOT / "auth/openapi-welcome.yaml").read_text(encoding="utf-8")
        settings = (MODEL_ROOT / "admin/openapi-system-setting.yaml").read_text(encoding="utf-8")
        self.assertIn("/api/v1/auth/welcome/acknowledge", master)
        self.assertIn("/api/v1/admin/settings/student-welcome", master)
        self.assertIn("WelcomeData:", auth)
        self.assertIn("messages:", auth)
        self.assertIn("welcome:", auth)
        self.assertIn("operationId: acknowledgeStudentWelcome", welcome)
        self.assertIn("operationId: getStudentWelcomeSetting", settings)
        self.assertIn("operationId: updateStudentWelcomeSetting", settings)
        self.assertIn("required: [messages, expectedVersion]", settings)

    def test_backend_separates_welcome_and_setting_services(self) -> None:
        welcome_service = (JAVA_ROOT / "auth/StudentWelcomeService.java").read_text(encoding="utf-8")
        welcome_controller = (JAVA_ROOT / "auth/WelcomeController.java").read_text(encoding="utf-8")
        setting_service = (JAVA_ROOT / "admin/SystemSettingService.java").read_text(encoding="utf-8")
        setting_controller = (JAVA_ROOT / "admin/SystemSettingController.java").read_text(encoding="utf-8")
        auth_controller = (JAVA_ROOT / "auth/AuthController.java").read_text(encoding="utf-8")
        self.assertIn("welcome_acknowledged_at", welcome_service)
        self.assertIn("STUDENT_WELCOME_MESSAGE", welcome_service)
        self.assertIn("setMessages", welcome_service)
        self.assertIn("implements WelcomeApi", welcome_controller)
        self.assertIn("implements SystemSettingApi", setting_controller)
        self.assertIn("expectedVersion", setting_service)
        self.assertIn("readMessages", setting_service)
        self.assertIn("auditService.success", setting_service)
        self.assertIn("setWelcome", auth_controller)

    def test_matching_supports_new_air_conditioner_and_roommate_preferences(self) -> None:
        scheme = (JAVA_ROOT / "matching/MatchingSchemeService.java").read_text(encoding="utf-8")
        matching = (JAVA_ROOT / "matching/MatchingService.java").read_text(encoding="utf-8")
        for feature in (
            "summerAirConditionerTemperature",
            "winterHeatingTemperature",
            "summerOvernightAirConditioner",
            "winterHeatingAcceptance",
            "afterLightsActivity",
            "alarmSnooze",
            "strongFoodOdorAcceptance",
        ):
            self.assertIn(feature, scheme + matching)
        self.assertIn("空调使用偏好存在差异", matching)
        self.assertIn("熄灯后活动习惯存在差异", matching)

    def test_frontend_uses_personal_preference_wording_and_conditional_fields(self) -> None:
        questionnaire = (FRONTEND / "views/student/QuestionnaireView.vue").read_text(encoding="utf-8")
        home = (FRONTEND / "views/student/StudentHomeView.vue").read_text(encoding="utf-8")
        self.assertIn("个人偏好", questionnaire)
        self.assertIn("isQuestionVisible", questionnaire)
        self.assertIn("isRequired", questionnaire)
        self.assertIn("WINTER_HEATING_TEMPERATURE", questionnaire)
        self.assertIn("SUMMER_AC_TEMPERATURE", home)
        self.assertNotIn("生活习惯问卷", questionnaire + home)

    def test_personal_preference_card_uses_desktop_table_and_profile_columns(self) -> None:
        home = (FRONTEND / "views/student/StudentHomeView.vue").read_text(encoding="utf-8")
        styles = (FRONTEND / "student-experience.css").read_text(encoding="utf-8")
        for expected in (
            "personal-preference-content",
            "personal-preference-table-column",
            "personal-preference-side-column",
            "personal-preference-profile-panel",
            "personal-preference-action-panel",
        ):
            self.assertIn(expected, home + styles)
        self.assertIn("grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr)", styles)
        self.assertIn("grid-template-columns: minmax(82px, auto) minmax(0, 1fr)", styles)
        self.assertIn("gap: 8px", styles)
        self.assertIn("@media (max-width: 900px)", styles)

    def test_frontend_welcome_modal_and_admin_editor_exist(self) -> None:
        shell = (FRONTEND / "layouts/AppShell.vue").read_text(encoding="utf-8")
        auth = (FRONTEND / "stores/auth.ts").read_text(encoding="utf-8")
        dashboard = (FRONTEND / "views/admin/AdminDashboardView.vue").read_text(encoding="utf-8")
        i18n = (FRONTEND / "i18n/index.ts").read_text(encoding="utf-8")
        styles = (FRONTEND / "student-experience.css").read_text(encoding="utf-8")
        self.assertIn("新同学，欢迎你", i18n)
        self.assertIn("welcome?.messages", shell)
        self.assertIn("acknowledgeWelcome", auth)
        self.assertIn("welcome-overlay", styles)
        self.assertIn("prefers-reduced-motion", styles)
        self.assertIn("新生欢迎语", dashboard)
        self.assertIn("welcomeMessages", dashboard)
        self.assertIn("/api/v1/admin/settings/student-welcome", dashboard)


if __name__ == "__main__":
    unittest.main()
