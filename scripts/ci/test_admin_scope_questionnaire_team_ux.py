#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    content = target.read_text(encoding="utf-8")
    if target.suffix == ".vue":
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = target.with_name(f"{target.stem}{suffix}")
            if companion.exists():
                content += "\n" + companion.read_text(encoding="utf-8")
        if path == "frontend/src/views/admin/AdminBatchView.vue":
            feature_root = ROOT / "frontend/src/features/admin-batch"
            if feature_root.exists():
                for component in sorted(feature_root.rglob("*.vue")):
                    content += "\n" + component.read_text(encoding="utf-8")
                for composable in sorted(feature_root.rglob("*.ts")):
                    content += "\n" + composable.read_text(encoding="utf-8")
    return content


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
    "/api/v1/admin/students/${student.id}/residency-adjustment-context",
    "/api/v1/admin/students/${placementTarget.value.studentId}/residency-adjustment",
    "master-data-card-body",
), "administrator student page is missing required layout or placement behavior")

placement_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentController.java")
require(placement_controller, (
    '"/residency-adjustment-context"',
    '"/residency-adjustment"',
), "administrator residency adjustment endpoints are missing")
placement_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java")
placement_mapper = read(
    "backend-java/server/src/main/resources/mapper/admin/AdminResidencyAdjustmentMapper.xml")
require(placement_service, (
    "residencyService.end(",
    "residencyService.assign(",
    "adjustmentMapper.findCompatibleBeds(",
    "RESIDENCY_ADJUSTMENT_TARGET_UNAVAILABLE",
), "administrator residency adjustment transaction is incomplete")
require(placement_mapper, (
    "active_batch_room_lock",
    "occupancy_source",
    "selectable",
), "administrator residency adjustment mapper must enforce room locks and occupancy")

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

team_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/student/VerifiedTeamInvitationController.java")
require(team_controller, (
    '"/team-invitations/verified"',
    '"/teams/{teamId}/invitations/{studentId}"',
    "cancelTeamInvitation",
), "verified team invite and cancellation endpoints are incomplete")
team_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/student/VerifiedTeamInvitationService.java")
require(team_service, (
    "INVITEE_IDENTITY_MISMATCH",
    "TEAM_INVITATION_PENDING_DUPLICATE",
    "ON DUPLICATE KEY UPDATE",
    "member_status IN ('JOINED','LOCKED')",
    "TEAM_INVITATION_CANCELLED",
    "member_status='INVITED'",
), "verified invitation identity, multi-invite creation or cancellation is incomplete")
team_response_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/student/TeamInvitationResponseService.java")
require(team_response_service, (
    "other_invitation.id<>:invitationId",
    "other_member.team_id<>:teamId",
    "supersededInvitationCount",
    "other_invitation.invitation_status='PENDING'",
), "competing team invitations must close transactionally after one is accepted")

team_view = read("frontend/src/views/student/TeamView.vue")
require(team_view, (
    "inviteStudentName",
    "studentName }",
    "cancelInvitation",
    "TransientNotice",
    "/team-invitations/verified",
), "team page must validate identity, cancel invitations and use popup notices")

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

print("admin scope, questionnaire and team UX contract: OK")
