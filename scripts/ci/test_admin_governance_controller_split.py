#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN_ROOT = ROOT / "backend-java" / "server" / "src" / "main" / "java" / "com" / "wust" / "dormitory" / "admin"
OLD = ADMIN_ROOT / "AdminGovernanceController.java"

EXPECTED = {
    "AdminGovernanceAuditController.java": {
        "routes": [
            '@PostMapping("/audit/query")',
            '@PostMapping("/audit/export")',
            '@GetMapping("/exports")',
            '@PostMapping("/exports/{taskId}/cancel")',
            '@GetMapping("/exports/{taskId}/download")',
        ],
        "services": ["AuditQueryService", "AuditExportService", "ExportTaskService", "FeatureAccessService"],
    },
    "AdminGovernanceNotificationController.java": {
        "routes": [
            '@GetMapping("/notifications/templates")',
            '@PostMapping("/notifications/templates/revisions")',
            '@PostMapping("/notifications/preflight")',
            '@PostMapping("/notifications/schedule")',
            '@GetMapping("/notifications/status")',
            '@PostMapping("/notifications/{taskId}/cancel")',
        ],
        "services": ["NotificationTemplateService", "NotificationDispatchService"],
    },
    "AdminGovernanceAnalyticsController.java": {
        "routes": [
            '@GetMapping("/analytics/definitions")',
            '@PostMapping("/analytics/batches/{batchId}/snapshot")',
            '@PostMapping("/analytics/dashboard")',
            '@PostMapping("/analytics/comparison")',
            '@PostMapping("/analytics/trend")',
        ],
        "services": ["BatchAnalyticsSnapshotService", "HistoricalAnalyticsService", "FeatureAccessService"],
    },
    "AdminGovernanceReportController.java": {
        "routes": [
            '@GetMapping("/reports/metadata")',
            '@GetMapping("/reports/templates")',
            '@PostMapping("/reports/templates")',
            '@PostMapping("/reports/export")',
        ],
        "services": ["ReportBuilderService"],
    },
    "AdminGovernanceRetentionController.java": {
        "routes": [
            '@GetMapping("/retention/policy")',
            '@GetMapping("/retention/statistics")',
            '@GetMapping("/retention/simulate")',
            '@PostMapping("/retention/preflight")',
        ],
        "services": ["DataRetentionQueryService"],
    },
}

ALL_DOMAIN_SERVICES = {
    "AuditQueryService",
    "AuditExportService",
    "ExportTaskService",
    "NotificationTemplateService",
    "NotificationDispatchService",
    "BatchAnalyticsSnapshotService",
    "HistoricalAnalyticsService",
    "ReportBuilderService",
    "DataRetentionQueryService",
}

errors: list[str] = []

if OLD.exists():
    errors.append("旧 AdminGovernanceController 仍存在")

sources: dict[str, str] = {}
for filename, spec in EXPECTED.items():
    path = ADMIN_ROOT / filename
    if not path.exists():
        errors.append(f"缺少拆分后的控制器：{filename}")
        continue
    source = path.read_text(encoding="utf-8")
    sources[filename] = source
    if '@RequestMapping("/api/v1/admin/governance")' not in source:
        errors.append(f"{filename} 缺少统一治理根路径")
    for service in spec["services"]:
        if service not in source:
            errors.append(f"{filename} 缺少领域依赖：{service}")
    allowed = set(spec["services"])
    for service in ALL_DOMAIN_SERVICES - allowed:
        if service in source:
            errors.append(f"{filename} 不应跨域依赖：{service}")

combined = "\n".join(sources.values())
for filename, spec in EXPECTED.items():
    source = sources.get(filename, "")
    for route in spec["routes"]:
        count = combined.count(route)
        if count != 1:
            errors.append(f"治理路由必须且只能实现一次：{route}（当前 {count} 次）")
        if source and route not in source:
            errors.append(f"治理路由归属错误：{route} 不在 {filename}")

expected_operations = sum(len(spec["routes"]) for spec in EXPECTED.values())
actual_operations = sum(
    source.count("@GetMapping(") + source.count("@PostMapping(")
    for source in sources.values()
)
if sources and actual_operations != expected_operations:
    errors.append(
        f"拆分后治理接口总数不匹配：当前 {actual_operations}，预期 {expected_operations}"
    )

if errors:
    print("管理员治理 Controller 拆分契约失败：")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print(
    "admin governance controller split contract: OK "
    "(5 audit/export + 6 notification + 5 analytics + 4 report + 4 retention operations)"
)
