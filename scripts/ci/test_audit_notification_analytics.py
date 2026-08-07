#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
errors: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        errors.append(f"missing required file: {path}")
        return ""
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


feature_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java")
for code in (
    "P2_SENSITIVE_DATA_VIEW", "P2_SENSITIVE_DATA_EXPORT", "P2_EXPORT_DESENSITIZATION",
    "P2_AUDIT_ADVANCED_QUERY", "P2_AUDIT_EXPORT",
    "P3_NOTIFICATION_TEMPLATE_VIEW", "P3_NOTIFICATION_TEMPLATE_MANAGE",
    "P3_NOTIFICATION_SEND", "P3_NOTIFICATION_SCHEDULE",
    "P3_NOTIFICATION_DELIVERY_STATUS", "P3_NOTIFICATION_CHANNEL_CONFIGURE",
    "P3_HISTORICAL_DASHBOARD", "P3_CROSS_BATCH_COMPARISON", "P3_TREND_ANALYSIS",
    "P3_CUSTOM_REPORT_EXPORT", "P3_DATA_RETENTION_QUERY",
):
    require(code in feature_codes, f"feature code missing: {code}")
quota_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/QuotaCodes.java")
for code in ("MAX_NOTIFICATION_RECIPIENTS", "DATA_RETENTION_DAYS", "AUDIT_RETENTION_DAYS"):
    require(code in quota_codes, f"quota code missing: {code}")

sensitive = read("backend-java/server/src/main/java/com/wust/dormitory/security/SensitiveDataPolicyService.java")
for token in ("FULL", "MASKED", "HIDDEN", "P2_SENSITIVE_DATA_VIEW", "requireReason", "maskPhone", "maskPreference"):
    require(token in sensitive, f"sensitive data policy missing: {token}")
require("studentViewer" in sensitive and "HIDDEN" in sensitive,
        "student-to-student sensitive data policy is not hidden")

query = read("backend-java/server/src/main/java/com/wust/dormitory/audit/AuditQueryService.java")
for token in (
    "occurredFrom", "occurredTo", "operatorId", "operatorRole", "module", "actionType",
    "targetType", "targetId", "success", "errorCode", "requestId", "networkAddress",
    "keyword", "page", "size",
):
    require(token in query, f"advanced audit query missing filter: {token}")
require("platform_audit_log" not in query, "school audit query must not merge platform authorization audit")

export_task = read("backend-java/server/src/main/java/com/wust/dormitory/export/ExportTaskService.java")
for token in ("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "expires_at", "progress", "downloadToken"):
    require(token in export_task, f"shared export task missing behavior: {token}")
audit_export = read("backend-java/server/src/main/java/com/wust/dormitory/audit/AuditExportService.java")
require("ExportTaskService" in audit_export and "P2_AUDIT_EXPORT" in audit_export,
        "audit export does not reuse shared async export tasks")

notification_template = read("backend-java/server/src/main/java/com/wust/dormitory/notification/NotificationTemplateService.java")
for token in ("zh-CN", "en-US", "revision", "VARIABLE_WHITELIST", "builtIn", "creationReason", "requireAllowedVariable"):
    require(token in notification_template, f"notification template behavior missing: {token}")
require("eval(" not in notification_template and "ScriptEngine" not in notification_template,
        "notification templates must not execute expressions or scripts")
notification_dispatch = read("backend-java/server/src/main/java/com/wust/dormitory/notification/NotificationDispatchService.java")
for token in ("executionKey", "MAX_NOTIFICATION_RECIPIENTS", "chunk", "scheduledAt", "cancel"):
    require(token in notification_dispatch, f"notification scheduling behavior missing: {token}")
require("NotificationChannel.IN_APP" in notification_dispatch,
        "this delivery implements only the in-app notification channel")

snapshot = (
    read("backend-java/server/src/main/java/com/wust/dormitory/analytics/BatchAnalyticsSnapshotService.java")
    + read("backend-java/server/src/main/resources/mapper/analytics/BatchAnalyticsSnapshotMapper.xml")
)
for token in ("metricVersion", "snapshot", "FINISHED", "immutable", "updatedAt"):
    require(token in snapshot, f"historical snapshot behavior missing: {token}")
metric = read("backend-java/server/src/main/java/com/wust/dormitory/analytics/MetricDefinition.java")
for token in ("timeRange", "filters", "sourceBasis", "dataUpdatedAt", "metricVersion"):
    require(token in metric, f"metric definition metadata missing: {token}")

report = read("backend-java/server/src/main/java/com/wust/dormitory/report/ReportBuilderService.java")
for token in ("FIELD_WHITELIST", "FILTER_WHITELIST", "SORT_WHITELIST", "PRESET_METRICS", "ExportTaskService"):
    require(token in report, f"report whitelist/task behavior missing: {token}")
require("sql" not in report.lower() or "arbitrary sql" in report.lower(),
        "report builder appears to accept arbitrary SQL")

retention = read("backend-java/server/src/main/java/com/wust/dormitory/retention/DataRetentionQueryService.java")
for token in (
    "CURRENT_STUDENT", "ACTIVE_RESIDENCY", "ACTIVE_BATCH", "PENDING_ROOM_CHANGE",
    "PENDING_EXCHANGE", "PENDING_WAITLIST", "ACTIVE_ENTITLEMENT", "PENDING_EXPORT",
    "LEGAL_AUDIT_HOLD", "simulate", "preflight", "dataCutoff.toString()", "auditCutoff.toString()",
):
    require(token in retention, f"retention protection/query missing: {token}")
require("DELETE FROM" not in retention, "retention query delivery must not execute deletion")

for component in (
    "AuditFilterBar.vue", "ExportTaskPanel.vue", "NotificationTemplateEditor.vue",
    "RecipientSelector.vue", "AnalyticsFilterBar.vue", "MetricDefinitionPopover.vue",
    "ReportBuilder.vue",
):
    require((ROOT / "frontend/src/components/admin" / component).exists(), f"shared admin component missing: {component}")

admin_view = read("frontend/src/views/admin/AdminGovernanceView.vue")
for text in ("高级审计", "通知中心", "历史分析", "自定义报表", "数据保留", "Advanced audit", "Notification center"):
    require(text in admin_view, f"Chinese/English governance UI text missing: {text}")
require("AppConfirmDialog" in admin_view, "sensitive export and notification confirmation must use shared dialog")
require("availableTabs" in admin_view and "hasFeature" in admin_view,
        "governance tabs and endpoint loading are not based on effective feature access")

router = read("frontend/src/router/index.ts")
require("admin/governance" in router and "AdminGovernanceView.vue" in router,
        "governance center is not registered as an administrator route")
shell = read("frontend/src/layouts/AppShell.vue")
require("governanceEnabled" in shell and "'/admin/governance'" in shell,
        "authorized administrators do not receive the governance center navigation entry")
require("<AppModal" in shell and "welcome-overlay" not in shell and "welcome-dialog" not in shell,
        "student welcome still duplicates a complete custom modal overlay")

controller = (
    read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminGovernanceAuditController.java")
    + read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminGovernanceAnalyticsController.java")
)
require("FeatureAccessService" in controller and "requireAny" in controller,
        "direct governance controller endpoints do not enforce effective feature access")

for test_file in (
    "security/SensitiveDataPolicyServiceTest.java",
    "audit/AuditQueryServiceTest.java",
    "notification/NotificationTemplateServiceTest.java",
    "notification/NotificationDispatchServiceTest.java",
    "analytics/BatchAnalyticsSnapshotServiceTest.java",
    "analytics/HistoricalAnalyticsServiceTest.java",
    "report/ReportBuilderServiceTest.java",
    "retention/DataRetentionQueryServiceTest.java",
):
    require((ROOT / "backend-java/server/src/test/java/com/wust/dormitory" / test_file).exists(),
            f"governance behavior test missing: {test_file}")

if errors:
    print("Audit, notification, analytics and retention contract failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("Audit, notification, analytics and retention contract passed")
