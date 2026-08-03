#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
OPENAPI_ROOT = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
OPENAPI_DIR = OPENAPI_ROOT.parent


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def validate_openapi(errors: list[str]) -> int:
    require(OPENAPI_ROOT.is_file(), "missing OpenAPI root contract", errors)
    if not OPENAPI_ROOT.is_file():
        return 0

    yaml_files = sorted(OPENAPI_DIR.rglob("*.yaml"))
    path_pattern = re.compile(r"^\s{2}(/api/v1/[^:]+):\s*$", re.MULTILINE)
    ref_pattern = re.compile(r"\$ref:\s*['\"]?([^'\"\s#]+)#")
    paths: set[str] = set()

    for yaml_file in yaml_files:
        text = yaml_file.read_text(encoding="utf-8")
        paths.update(path_pattern.findall(text))
        for target in ref_pattern.findall(text):
            resolved = (yaml_file.parent / target).resolve()
            require(
                resolved.is_file(),
                f"broken OpenAPI reference: {yaml_file.relative_to(ROOT)} -> {target}",
                errors,
            )

    critical_paths = {
        "/api/v1/auth/login",
        "/api/v1/auth/activate",
        "/api/v1/admin/batches",
        "/api/v1/admin/batches/{batchId}/scope",
        "/api/v1/admin/batches/{batchId}/room-preflight",
        "/api/v1/student/profile",
        "/api/v1/student/batches/{batchId}/questionnaire",
        "/api/v1/student/batches/{batchId}/beds/{bedId}/hold",
        "/api/v1/student/batches/{batchId}/teams/{teamId}/confirm",
        "/api/v1/realtime/batches/{batchId}/rooms/{roomId}",
        "/api/v1/platform/subscription",
    }
    for path in sorted(critical_paths):
        require(path in paths, f"missing critical OpenAPI path: {path}", errors)
    require(len(paths) >= 82, f"OpenAPI path count unexpectedly low: {len(paths)}", errors)
    return len(paths)


def validate_fixed_questionnaire(errors: list[str]) -> None:
    service = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCreationService.java"
    )
    require(
        "version_code='SYSTEM-PREFERENCE-V1'" in service,
        "batch creation does not bind the fixed questionnaire code",
        errors,
    )
    require(
        '"BUILTIN_QUESTIONNAIRE_MISSING"' in service,
        "missing stable fixed-questionnaire error code",
        errors,
    )
    require(
        'result.put("questionnaireType", "BUILTIN_FIXED")' in service,
        "batch response does not identify the fixed questionnaire type",
        errors,
    )
    require(
        "version_status='PUBLISHED'" not in service,
        "batch creation still selects the latest published questionnaire",
        errors,
    )
    require(
        "数据库完整性修复" not in service,
        "public business error leaks private database recovery instructions",
        errors,
    )


def validate_batch_scope(errors: list[str]) -> None:
    service = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeService.java"
    )
    lifecycle = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java"
    )
    controller = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeController.java"
    )
    view = read("frontend/src/views/admin/AdminBatchView.vue")

    require(
        "batch_student_eligibility" in service and "batch_room_scope" in service,
        "batch scope service does not persist exact student and room scope",
        errors,
    )
    require(
        "DELETE FROM batch_building_scope" in service
        and "DELETE FROM batch_bed_scope" in service,
        "batch scope update does not replace legacy broad scope",
        errors,
    )
    require(
        "BATCH_STUDENT_SCOPE_REQUIRED" in service
        and "BATCH_ROOM_SCOPE_REQUIRED" in service,
        "batch scope readiness errors are missing",
        errors,
    )
    require(
        "batchScopeService.requireReady(batchId)" in lifecycle,
        "batch publication does not validate selected students and rooms before preflight",
        errors,
    )
    require(
        "implements BatchScopeApi" in controller,
        "batch scope OpenAPI controller is missing",
        errors,
    )
    require(
        "/scope`" in view or "/scope'" in view or "/scope\"" in view,
        "administrator batch page does not call the batch scope endpoint",
        errors,
    )
    require(
        "配置参与范围" in view and "保存并继续发布" in view,
        "administrator batch page does not provide the scope-first publication flow",
        errors,
    )
    require(
        "准备范围" not in view,
        "legacy all-student/all-building preparation action remains visible",
        errors,
    )


def validate_business_feature_projection(errors: list[str]) -> None:
    auth_contract = read("backend-java/model/src/main/resources/auth/openapi-auth.yaml")
    auth_controller = read(
        "backend-java/server/src/main/java/com/wust/dormitory/auth/AuthController.java"
    )
    auth_store = read("frontend/src/stores/auth.ts")
    batch_view = read("frontend/src/views/admin/AdminBatchView.vue")
    platform_features = read("frontend/src/views/platform/PlatformFeaturesView.vue")

    require(
        "required: [userId, username, displayName, userType, features]" in auth_contract,
        "current user contract does not require effective feature projection",
        errors,
    )
    require(
        "data.setFeatures(featureAccessService.currentFeatures()" in auth_controller,
        "authentication responses do not include effective features",
        errors,
    )
    require(
        "applyBusinessEntitlements" in auth_store and "current?.features ?? []" in auth_store,
        "frontend session does not apply effective features after login or restore",
        errors,
    )
    require(
        "P2_BED_SELECTION_MODE" in batch_view and "bedModeAuthorized" in batch_view,
        "school administrator batch page is not guarded by the bed-selection entitlement",
        errors,
    )
    require(
        "bedSelectionFeature" in platform_features
        and "P2_BED_SELECTION_MODE" in platform_features
        and "核心模式开关" in platform_features,
        "platform administrator does not have a dedicated bed-selection mode switch",
        errors,
    )


def validate_overlay_style(errors: list[str]) -> None:
    main = read("frontend/src/main.ts")
    overlay = read("frontend/src/overlay-refinement.css")

    require(
        "./overlay-refinement.css" in main,
        "shared overlay style is not loaded after feature styles",
        errors,
    )
    for selector in (
        ".modal-overlay",
        ".welcome-overlay",
        ".dialog-backdrop",
        "[class$='-overlay']",
        "[class$='-backdrop']",
        ".scope-dialog",
        ".preflight-dialog",
    ):
        require(selector in overlay, f"shared overlay style misses selector: {selector}", errors)
    require(
        "position: fixed" in overlay
        and "inset: 0" in overlay
        and "backdrop-filter: blur(8px)" in overlay
        and "background: rgba(12, 24, 48, 0.68)" in overlay,
        "shared overlay background does not match the dormitory editor visual layer",
        errors,
    )


def validate_layout_and_selection_regressions(errors: list[str]) -> None:
    editor = read("frontend/src/components/admin/RoomLayoutEditor.vue")
    editor_css = read("frontend/src/admin-layout-canvas-refinement.css")
    room_list = read("frontend/src/views/student/RoomListView.vue")
    assignment_view = read("frontend/src/views/student/AssignmentView.vue")
    batch_view = read("frontend/src/views/admin/AdminBatchView.vue")
    residency_query = read(
        "backend-java/server/src/main/java/com/wust/dormitory/residency/CurrentResidencyQueryService.java"
    )
    team_guard = read(
        "backend-java/server/src/main/java/com/wust/dormitory/residency/TeamCategoryGuard.java"
    )

    require(
        "transform: `translate(-50%, -50%) rotate(" not in editor,
        "room layout still rotates text and controls with the bed card",
        errors,
    )
    require(
        "layout-bed-surface" in editor
        and "layout-bed-content" in editor
        and "isQuarterTurn(unit.rotation)" in editor,
        "room layout does not separate the rotating shape from upright controls",
        errors,
    )
    require(
        "恢复标准2×2布局" in editor
        and "new DefaultPlacement(-2.35, -1.65, 0)" in read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
        ),
        "standard horizontal 2x2 default layout is not preserved",
        errors,
    )
    require(
        ".layout-bed-surface" in editor_css
        and ".layout-bed-unit.vertical .layout-bed-type-actions" in editor_css,
        "rotated bed cards do not keep controls inside the new orientation",
        errors,
    )
    require(
        "window.confirm" not in room_list
        and "roomSelectionTarget" in room_list
        and "room-selection-overlay" in room_list,
        "room-mode confirmation still uses a browser alert instead of an overlay dialog",
        errors,
    )
    require(
        '<div v-if="scopeDialog" class="modal-overlay"' in batch_view
        and '<div v-if="preflightBatch && roomPreflight" class="modal-overlay"' in batch_view,
        "batch scope or preflight no longer uses the common modal overlay",
        errors,
    )
    require(
        "db.id AS building_id" in residency_query
        and "public Map<String, Object> assignment(long studentId)" in residency_query,
        "room-mode assignment query is not normalized or still uses an invalid building column",
        errors,
    )
    require(
        "具体床位待寝室成员协商" in assignment_view
        and "未固定床位" in assignment_view,
        "assignment page does not support room-only residency",
        errors,
    )
    require(
        "invitationContext" in team_guard
        and "active_batch_student_lock" in team_guard,
        "team invitation category guard no longer resolves the active batch context",
        errors,
    )


def validate_frontend(errors: list[str]) -> None:
    shell = read("frontend/src/layouts/AppShell.vue")
    login = read("frontend/src/views/LoginView.vue")
    router = read("frontend/src/router/index.ts")
    vite_config = read("frontend/vite.config.ts")
    env_example = read(".env.example")
    package_json = read("frontend/package.json")

    require(
        "VITE_INSTITUTION_NAME" in shell,
        "application shell does not use configurable institution display name",
        errors,
    )
    require(
        "VITE_INSTITUTION_NAME" in login,
        "login page does not use configurable institution display name",
        errors,
    )
    for forbidden in (
        "showBuiltinQuestionnaireNotice",
        "BUILT-IN PREFERENCE QUESTIONNAIRE",
        "学生个人偏好问卷：系统内置固定问卷",
    ):
        require(forbidden not in shell, f"removed questionnaire notice returned: {forbidden}", errors)
    require(
        "loadEnv" in vite_config and re.search(r"envDir:\s*['\"]\.\.['\"]", vite_config),
        "Vite does not load the repository-root environment file",
        errors,
    )
    require(
        "VITE_DEV_SERVER_PORT" in vite_config
        and "VITE_BACKEND_PROXY_TARGET" in vite_config
        and "VITE_ALLOWED_HOSTS" in vite_config,
        "Vite development settings are not fully environment-driven",
        errors,
    )
    for variable in (
        "VITE_INSTITUTION_NAME",
        "VITE_DEV_SERVER_PORT",
        "VITE_BACKEND_PROXY_TARGET",
        "VITE_ALLOWED_HOSTS",
        "WUST_DORMITORY_CORS_ALLOWED_ORIGIN_PATTERNS",
    ):
        require(variable in env_example, f"missing public environment variable: {variable}", errors)

    has_admin_batches_path = re.search(r"path:\s*['\"]admin/batches['\"]", router)
    has_admin_batches_name = re.search(r"name:\s*['\"]admin-batches['\"]", router)
    require(
        bool(has_admin_batches_path and has_admin_batches_name),
        "administrator batch route is missing or renamed",
        errors,
    )
    require(
        '"generate:api"' in package_json and "openapi-typescript" in package_json,
        "frontend API types are not generated from the OpenAPI contract",
        errors,
    )


def validate_security_configuration(errors: list[str]) -> None:
    security = read(
        "backend-java/server/src/main/java/com/wust/dormitory/security/SecurityConfig.java"
    )
    require(
        "WUST_DORMITORY_CORS_ALLOWED_ORIGIN_PATTERNS" in security,
        "backend CORS origin patterns are not environment-driven",
        errors,
    )
    require(
        "parseOriginPatterns" in security,
        "backend CORS origin parsing is missing",
        errors,
    )
    require(
        re.search(r"vicp[.]fun", security) is None,
        "backend security configuration contains a hard-coded tunnel domain",
        errors,
    )



def validate_comprehensive_enhancements(errors: list[str]) -> None:
    tsconfig = read("frontend/tsconfig.app.json")
    browser_config = read("frontend/tsconfig.browser.json")
    data_view = read("frontend/src/views/admin/AdminDataView.vue")
    dormitory_view = read("frontend/src/views/admin/AdminDormitoryView.vue")
    dashboard_view = read("frontend/src/views/admin/AdminDashboardView.vue")
    batch_view = read("frontend/src/views/admin/AdminBatchView.vue")
    matching_view = read("frontend/src/views/admin/AdminMatchingView.vue")
    room_list = read("frontend/src/views/student/RoomListView.vue")
    room_detail = read("frontend/src/views/student/RoomDetailView.vue")
    home_view = read("frontend/src/views/student/StudentHomeView.vue")
    questionnaire_view = read("frontend/src/views/student/QuestionnaireView.vue")
    team_view = read("frontend/src/views/student/TeamView.vue")
    assignment_view = read("frontend/src/views/student/AssignmentView.vue")
    shell = read("frontend/src/layouts/AppShell.vue")
    student_service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java")
    spreadsheet_controller = read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminSpreadsheetController.java")
    welcome_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java")
    preference_service = read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentPreferenceService.java")
    policy_service = read("backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java")
    team_service = read("backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java")
    matching_service = read("backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingService.java")
    feature_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java")

    require(
        '"extends": "./tsconfig.browser.json"' in tsconfig
        and '"moduleResolution": "Bundler"' in browser_config,
        "frontend tsconfig still depends on an editor-unresolvable package preset",
        errors,
    )
    require(
        "匹配度" in room_list and "score-ring-with-label" in room_list,
        "room cards do not explain the matching score",
        errors,
    )
    require(
        "countryOptions" in data_view and "countryLabel" in data_view
        and "nationality_code" in student_service,
        "student nationality is not selected and displayed by country or region name",
        errors,
    )
    require(
        "/api/v1/admin/import/students" in data_view
        and "/api/v1/admin/import/rooms" in dormitory_view
        and "学生导入模板" in spreadsheet_controller
        and "宿舍导入模板" in spreadsheet_controller,
        "student or dormitory spreadsheet import and templates are incomplete",
        errors,
    )
    require(
        "countryMessages" in dashboard_view
        and "configuration.countryMessages().get(countryCode)" in welcome_service
        and 'configuration.messages().get("en-US")' in welcome_service,
        "country-specific welcome messages or English fallback are missing",
        errors,
    )
    for token in (
        "studentGenderFilter", "studentCategoryFilter", "studentDegreeFilter",
        "studentMajorFilter", "studentGradeFilter", "roomBuildingFilter", "roomFloorFilter",
        "scope-sticky-header",
    ):
        require(token in batch_view, f"batch scope filter or sticky save is missing: {token}", errors)
    require(
        "degree_level" in student_service and "grade_year" in student_service,
        "nullable degree level and grade year are not persisted for students",
        errors,
    )
    require(
        "/api/v1/student/preferences" in questionnaire_view
        and "即使当前没有开放批次" in home_view
        and "student_preference_profile" in preference_service,
        "students cannot maintain preferences outside an active batch",
        errors,
    )
    require(
        "24小时制" in questionnaire_view and "24小时制" in batch_view,
        "time inputs do not explain the 24-hour format",
        errors,
    )
    require(
        ":min=\"questionMin(question)\"" in questionnaire_view
        and ":max=\"questionMax(question)\"" in questionnaire_view
        and "temperature < 16 || temperature > 30" in matching_service,
        "air-conditioner temperature is not constrained to 16 through 30 degrees",
        errors,
    )
    require(
        "可以直接点击三维图像中的床位" in room_detail
        and "已切换到" not in room_detail
        and "enlarged-countdown" in room_detail,
        "bed selection interaction hints or countdown presentation regressed",
        errors,
    )
    require(
        "bedTypeLabel" in assignment_view
        and "bedTypeLabel" in read("frontend/src/views/admin/AdminResidencyView.vue")
        and "bedTypeLabel" in read("frontend/src/views/admin/AdminAssignmentView.vue"),
        "user-facing bed type names are not consistently applied",
        errors,
    )
    require(
        "compact-home-top-card" in home_view,
        "student home top cards were not compacted",
        errors,
    )
    require(
        "TEAM_ASSIGNED_FORBIDDEN" in team_service
        and "请先创建处于组队中的队伍" in team_service
        and "请先创建处于组队中的队伍" in team_view,
        "assigned-student team restrictions or invitation guidance are missing",
        errors,
    )
    require(
        "ALLOW_SELECTION_WITHOUT_QUESTIONNAIRE" in policy_service
        and "ALLOW_STUDENT_RESELECT" in policy_service
        and "allowWithoutQuestionnaire" in matching_view
        and "allowStudentReselect" in matching_view,
        "administrator questionnaire-bypass or reselect policy is incomplete",
        errors,
    )
    require(
        "missingPreferenceCount" in room_list
        and "conflictReasons" in room_list
        and "仅剩上下铺" in read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java")
        and "仅剩上床下桌" in read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java"),
        "roommate preference and remaining-bed warnings are incomplete",
        errors,
    )
    for code in (
        "P2_STUDENT_PROFILE_INSIGHT", "P2_ROOM_RECOMMENDATION",
        "P2_QUESTIONNAIRE_BYPASS_CONTROL", "P2_STUDENT_RESELECT_CONTROL",
    ):
        require(code in feature_codes, f"missing system-admin feature code: {code}", errors)
    require(
        "weight-manual" in matching_view and "权重控制说明" in matching_view,
        "matching weight guidance is missing",
        errors,
    )
    require(
        "account-card-without-avatar" in shell and "padding" in shell,
        "sidebar account card padding was not increased",
        errors,
    )
    require(
        not (ROOT / ".github/workflows/export-source-snapshot.yml").exists(),
        "temporary source export workflow must not enter the public baseline",
        errors,
    )

def validate_test_inventory(errors: list[str]) -> int:
    tests = sorted((ROOT / "backend-java/server/src/test/java").rglob("*Test.java"))
    expected = {
        "AuthTokenSerializationTest.java",
        "AuthControllerFeatureProjectionTest.java",
        "BedScopeGuardTest.java",
        "BatchCreationServiceTest.java",
        "BatchSelectionModeGuardTest.java",
        "TeamCategoryGuardTest.java",
        "BedSelectionEligibilityGuardTest.java",
        "SecurityConfigTest.java",
        "BatchScopeServiceTest.java",
        "BatchLifecycleServiceTest.java",
        "RoomLayoutServiceTest.java",
        "ResidencyServiceTest.java",
        "SpreadsheetSupportTest.java",
        "CountryRegionCatalogTest.java",
        "MatchingServiceTest.java",
    }
    names = {path.name for path in tests}
    for name in sorted(expected):
        require(name in names, f"missing core regression test: {name}", errors)
    return len(tests)


def main() -> int:
    errors: list[str] = []
    path_count = validate_openapi(errors)
    validate_fixed_questionnaire(errors)
    validate_batch_scope(errors)
    validate_business_feature_projection(errors)
    validate_overlay_style(errors)
    validate_layout_and_selection_regressions(errors)
    validate_frontend(errors)
    validate_security_configuration(errors)
    validate_comprehensive_enhancements(errors)
    test_count = validate_test_inventory(errors)

    if errors:
        print("System contract validation failed:", file=sys.stderr)
        for error in sorted(set(errors)):
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "System contract validation passed: "
        f"{path_count} OpenAPI paths, {test_count} backend test classes"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
