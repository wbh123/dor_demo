#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")
    if target.suffix == ".vue":
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = target.with_name(f"{target.stem}{suffix}")
            if companion.exists():
                content += "\n" + companion.read_text(encoding="utf-8")
    return content


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


home_content = read("frontend/src/views/student/StudentHomeContent.vue")
home_view = read("frontend/src/views/student/StudentHomeView.vue")
shell = read("frontend/src/layouts/AppShell.vue")
router = read("frontend/src/router/index.ts")
i18n = read("frontend/src/i18n/index.ts")
env_example = read(".env.example")
welcome_editor = read("frontend/src/components/admin/WelcomeMessageEditor.vue")
dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
platform_layout = read("frontend/src/layouts/PlatformLayout.vue")
platform_dashboard = read("frontend/src/views/platform/PlatformDashboardView.vue")
platform_features = read("frontend/src/views/platform/PlatformFeaturesView.vue")
residency = read("frontend/src/views/admin/AdminResidencyView.vue")
bed_confirmation = read("frontend/src/views/admin/AdminBedConfirmationView.vue")
student_admin = read("frontend/src/views/admin/AdminDataView.vue")
student_service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java")
student_query_contract = "\n".join((
    student_service,
    read("backend-java/server/src/main/resources/mapper/admin/StudentAdminMapper.xml"),
    read("backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/StudentAdminDetailRow.java"),
))
batch = "\n".join((
    read("frontend/src/views/admin/AdminBatchView.vue"),
    read("frontend/src/features/admin-batch/components/BatchScopeDialog.vue"),
    read("frontend/src/features/admin-batch/components/BatchPublishConfirmationDialog.vue"),
    read("frontend/src/features/admin-batch/components/BatchAllocationPreviewDialog.vue"),
))
openapi_root = read("backend-java/model/src/main/resources/openapi-interface.yaml")
auth_contract = read("backend-java/model/src/main/resources/auth/openapi-auth.yaml")
welcome_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java")
welcome_service_test = read("backend-java/server/src/test/java/com/wust/dormitory/auth/StudentWelcomeServiceTest.java")
admin_password = read("frontend/src/views/admin/AdminPasswordView.vue")

require("profileAnswerEntries" in home_content, "student home does not normalize the canonical preference object")
require("questionnaire.value.answers ?? []" not in home_content, "student home still treats preference answers as a legacy array")
require(
    "to=\"/student/preferences\"" in home_content
    and "cross-batch-preference-note" not in home_view,
    "student preference entry must remain in the card without the detached cross-batch bar",
)
require("VITE_ADMIN_CONTACT_PHONE" in home_view and "有疑问请致电" in home_view, "student contact phone is not environment-driven")
require("phone-edit-fab" not in home_view, "student phone editor still uses a detached floating button")

require("VITE_APP_TITLE" in shell and "VITE_APP_SUBTITLE" in shell, "school application title and subtitle are not environment-driven")
require("VITE_APP_TITLE" in env_example and "VITE_APP_SUBTITLE" in env_example and "VITE_ADMIN_CONTACT_PHONE" in env_example, "public environment template misses brand or contact settings")
require("to=\"/admin/profile/password\"" in shell, "school administrator sidebar has no password change entry")
require("subtitle(chinese: string, english: string)" in i18n and "locale.value === 'zh-CN' ? chinese : english" in i18n, "bilingual subtitle selection remains reversed")

require("welcome-token-toolbar" in dashboard and "插入学生信息" in dashboard and "token-toolbar" not in welcome_editor, "welcome token toolbar must be centralized around the active editor")
require("<strong>汉语</strong>" in dashboard and "<strong>英语</strong>" in dashboard, "welcome language cards are not named 汉语 and英语")
require("<strong>美国</strong>" not in dashboard, "welcome editor still presents English as United States")
require("美国卡片" not in dashboard, "welcome copy still ties English fallback to the United States")
require("message:" not in auth_contract.split("WelcomeData:", 1)[1].split("CurrentUserData:", 1)[0], "welcome OpenAPI still exposes the legacy message field")
require("setMessage(" not in welcome_service, "welcome service still writes the legacy message field")
require("getMessage()" not in welcome_service_test, "welcome service tests still use the removed legacy message field")

require("<img" not in platform_layout, "system administrator platform still renders a school logo")
require("exact-active-class" in platform_layout and "custom-active" in platform_layout, "platform navigation does not distinguish service overview from child routes")
for platform_route in ("/platform/plans", "/platform/subscription", "/platform/features", "/platform/quotas", "/platform/audit", "/platform/profile/password"):
    require(platform_route in platform_layout, f"system administrator does not receive the default platform capability: {platform_route}")
for quota_code in ("MAX_CAMPUSES", "MAX_BUILDINGS", "MAX_BATCHES_PER_YEAR", "MAX_CONCURRENT_ACTIVE_BATCHES"):
    require(quota_code in platform_dashboard, f"platform dashboard misses quota title mapping: {quota_code}")
require("batchSelection" in platform_features and "批量开启" in platform_features and "批量关闭" in platform_features, "feature authorization lacks batch operations")
require("permission-heading-line" in platform_features, "permission badge and enabled count are not aligned on the title line")

require("AdminBedConfirmationView" in residency and "residencyTab" in residency, "residency and actual-bed review are not merged into one business page")
require("admin/bed-confirmations" not in router, "legacy standalone bed-confirmation route still exists")
require("实际床位核查" not in shell, "legacy standalone bed-confirmation menu still exists")
require("reviewFilter" in bed_confirmation and "全部核查状态" in bed_confirmation, "actual-bed review has no dropdown filter")

require(
    "DormitoryBedSelector" in student_admin
    and "placementBeds" in student_admin
    and "placementBedId" in student_admin,
    "student accommodation adjustment does not use the shared dormitory bed selector",
)
for field in ("current_building_name", "current_room_number", "current_bed_code", "selection_review_status", "declared_bed_code"):
    require(field in student_query_contract, f"student list query misses accommodation field: {field}")
require("TRANSFER_MANUAL" not in student_service, "legacy transfer-student enrollment source remains accepted")
require("住宿状态" in student_admin and "宿舍与床位" in student_admin, "student table does not show residence and selection locations")

require(
    "scope-footer-status" in batch
    and "仅保存范围" in batch
    and "保存并发布" in batch,
    "scope save and publish actions are not anchored in the shared modal footer",
)
require(
    "<AppConfirmDialog" in batch
    and "publishConfirmation" in batch
    and "confirm-text=\"确认发布\"" in batch,
    "batch publication does not use the shared confirmation dialog after completed preparation",
)
require(
    "<AppModal" in batch
    and "统一分配预演" in batch
    and "allocation-stats" in batch,
    "allocation preview does not use the shared modal visual layer",
)

require("/api/v1/auth/password" in openapi_root, "school administrator password endpoint is missing from the root OpenAPI contract")
require("admin/profile/password" in router, "school administrator password route is missing")
require((ROOT / "frontend/src/views/admin/AdminPasswordView.vue").is_file(), "school administrator password page is missing")
require("调试阶段仅要求密码非空" in admin_password, "school administrator password page misses the debugging non-empty-only policy hint")
for obsolete in ("12至72位", "包含大写字母", "包含小写字母", "包含数字", "包含特殊字符"):
    require(obsolete not in admin_password, f"school administrator password page still exposes obsolete strong-password rule: {obsolete}")

if errors:
    print("UI and business closure contract failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("UI and business closure contract passed")
