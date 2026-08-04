#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(source: str, tokens: tuple[str, ...], context: str) -> None:
    for token in tokens:
        if token not in source:
            raise AssertionError(f"{context}: {token}")


phone_select = read("frontend/src/components/common/PhoneDialCodeSelect.vue")
require(phone_select, (
    "selectedDialCode",
    "option.countryName",
    "option.dialCode",
    "update:modelValue",
), "phone dial-code selector must separate collapsed and expanded labels")

notice = read("frontend/src/components/common/TransientNotice.vue")
require(notice, (
    "duration: 3000",
    "window.setTimeout",
    "emit('close')",
    "notice-close",
), "transient notice must auto-close and support manual close")

global_notice = read("frontend/src/utils/installTransientSuccessNotices.ts")
require(global_notice, (
    "window.setTimeout(() => close(element), 3000)",
    ".alert.success",
    "notice-close",
), "existing success alerts must be enhanced as three-second popup notices")

admin_data = read("frontend/src/views/admin/AdminDataView.vue")
require(admin_data, (
    "PhoneDialCodeSelect",
    "TransientNotice",
    "student-category-switch-top",
    "master-data-card-body",
), "administrator student page is missing required layout behavior")

admin_openapi = read(
    "backend-java/model/src/main/resources/admin/openapi-residency-transfer.yaml")
require(admin_openapi, (
    "operationId: getStudentResidencyAdjustmentContext",
    "operationId: adjustStudentResidency",
    "AdminResidencyAdjustmentRequest",
), "administrator residency adjustment is missing from OpenAPI")
placement_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentController.java")
require(placement_controller, (
    "implements AdminResidencyAdjustmentApi",
    "getStudentResidencyAdjustmentContext",
    "adjustStudentResidency",
), "administrator residency adjustment must use generated API")
placement_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java")
require(placement_service, (
    "residencyService.end(",
    "residencyService.assign(",
    "active_batch_room_lock",
    "bed_confirmation_request",
    "room_change_request",
    "room_exchange_participant_lock",
    "RESIDENCY_ADJUSTMENT_PENDING_WORKFLOW",
    "position_index",
    "rotation_degrees",
), "administrator residency adjustment transaction is incomplete")

admin_batch = read("frontend/src/views/admin/AdminBatchView.vue")
require(admin_batch, (
    "全选当前可用",
    "studentMajorFilter",
    "studentGradeFilter",
    "roomBuildingFilter",
    "roomFloorFilter",
), "batch scope filters and select-all controls are incomplete")

batch_scope = read("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeService.java")
require(batch_scope, (
    "conflict_batch_name",
    "conflict_selection_mode",
    "disabled_reason",
    "room.put(\"operational_status\", disabledReason)",
    "ROOM_ACTIVE_BATCH_CONFLICT",
    "active_batch_room_lock",
), "batch scope service must expose, display and validate active room conflicts")

scope_styles = read("frontend/src/admin-density-refinement.css")
require(scope_styles, (
    ".scope-filter-grid .input",
    "height: 42px",
    ".scope-option.disabled::after",
    "活动互斥或房间不可用",
), "scope filter height and conflict label styles are incomplete")

preference = read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentPreferenceService.java")
require(preference, (
    "requiredFlag(",
    "value instanceof Boolean",
    "value instanceof Number",
), "questionnaire required flags must accept JDBC boolean and numeric values")
required_flag_test = read(
    "backend-java/server/src/test/java/com/wust/dormitory/student/StudentPreferenceRequiredFlagTest.java")
require(required_flag_test, ("Boolean.TRUE", '"true"', "assertFalse"),
        "questionnaire flag regression test is incomplete")

student_openapi = read("backend-java/model/src/main/resources/student/openapi-student.yaml")
require(student_openapi, (
    "required: [studentNumber, studentName]",
    "取消待确认邀请或移除已接受队友",
), "student invite identity and cancellation contract are incomplete")
student_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java")
require(student_controller, (
    "request.getStudentName()",
    "verifiedTeamInvitationService.removeOrCancel",
), "StudentApi implementation does not route verified invitations")
team_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/student/VerifiedTeamInvitationService.java")
require(team_service, (
    "INVITEE_IDENTITY_MISMATCH",
    "teamService.createFormingTeam(user)",
    "teamService.inviteTeammate(normalizedNumber, user)",
    "TEAM_INVITATION_CANCELLED",
    "member_status='INVITED'",
    "removeOrCancel",
    "notification.invitationWithdrawn.title",
), "verified invitation identity, creation or cancellation is incomplete")

spreadsheet = read("backend-java/server/src/main/java/com/wust/dormitory/admin/SpreadsheetSupport.java")
require(spreadsheet, (
    'createSheet("字段枚举")',
    "字段名称",
    "允许填写内容",
    "系统规范值",
), "Excel templates must include field-enumeration instructions")

controller = read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminSpreadsheetController.java")
require(controller, (
    "必填字段",
    "允许中文名称、英文名称或两位代码",
    "预检不会写入正式数据",
    "字段枚举",
), "template instructions must explain required fields and tolerance")

mapper = read("backend-java/server/src/main/java/com/wust/dormitory/admin/StudentImportRowMapper.java")
require(mapper, (
    "normalizeGradeYear",
    "normalizeStudentCategory",
    "normalizeDegreeLevel",
    "normalizeCountryCode",
), "student import values must be normalized without loosening headers")

room_import = read("backend-java/server/src/main/java/com/wust/dormitory/admin/RoomImportService.java")
require(room_import, (
    "normalizeRoomType",
    "normalizeGender",
    "normalizeResidentScope",
    "normalizeOperationalStatus",
    'integer(value(values, "楼层", "floornumber"), "楼层", "层")',
), "room import values must accept documented human-friendly forms")
room_import_test = read(
    "backend-java/server/src/test/java/com/wust/dormitory/admin/RoomImportValueNormalizationTest.java")
require(room_import_test, (
    'normalizeRoomType("四人间")',
    'normalizeResidentScope("国际生")',
    'normalizeOperationalStatus("维修")',
), "room import normalization regression test is incomplete")

print("admin scope, questionnaire, OpenAPI and team UX contract: OK")
