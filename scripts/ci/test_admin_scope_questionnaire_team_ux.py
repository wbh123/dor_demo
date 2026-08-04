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

admin_data = read("frontend/src/views/admin/AdminDataView.vue")
require(admin_data, (
    "PhoneDialCodeSelect",
    "TransientNotice",
    "student-category-switch-top",
    "/api/v1/admin/students/${student.id}/residency-adjustment-context",
    "/api/v1/admin/students/${placementTarget.value.studentId}/residency-adjustment",
    "master-data-card-body",
), "administrator student page is missing required layout or placement behavior")

admin_batch = read("frontend/src/views/admin/AdminBatchView.vue")
require(admin_batch, (
    "TransientNotice",
    "room.conflict_batch_name",
    "room.conflict_selection_mode",
    "scope-filter-control",
    "全选当前可用",
    "studentMajorFilter",
    "studentGradeFilter",
    "roomBuildingFilter",
    "roomFloorFilter",
), "batch scope filtering, conflict labels or notices are incomplete")

batch_scope = read("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeService.java")
require(batch_scope, (
    "conflict_batch_name",
    "conflict_selection_mode",
    "ROOM_ACTIVE_BATCH_CONFLICT",
    "active_batch_room_lock",
), "batch scope service must expose and validate active room conflicts")

preference = read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentPreferenceService.java")
require(preference, (
    "requiredFlag(",
    "value instanceof Boolean",
    "value instanceof Number",
), "questionnaire required flags must accept JDBC boolean and numeric values")

student_openapi = read("backend-java/model/src/main/resources/student/openapi-student.yaml")
require(student_openapi, (
    "required: [studentNumber, studentName]",
    "/api/v1/student/teams/{teamId}/invitations/{studentId}",
    "operationId: cancelTeamInvitation",
), "team invite identity and cancellation OpenAPI are incomplete")

team_service = read("backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java")
require(team_service, (
    "inviteTeammate(String studentNumber, String studentName",
    "INVITEE_IDENTITY_MISMATCH",
    "return createInternalTeam(batchId, user)",
    "cancelInvitation(",
    "TEAM_INVITATION_CANCELLED",
), "team invitation service is incomplete")

team_view = read("frontend/src/views/student/TeamView.vue")
require(team_view, (
    "inviteStudentName",
    "studentName: inviteStudentName.value.trim()",
    "cancelInvitation",
    "TransientNotice",
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

print("admin scope, questionnaire and team UX contract: OK")
