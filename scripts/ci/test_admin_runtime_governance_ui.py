#!/usr/bin/env python3
# 管理运行时修复的跨层回归契约；通过标准五项门禁持续验证。
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        raise AssertionError(message)


def forbid(source: str, token: str, message: str) -> None:
    if token in source:
        raise AssertionError(message)


dormitory = read("frontend/src/views/admin/AdminDormitoryView.vue")
forbid(dormitory, 'class="building-summary-list"', "楼栋概况不得继续使用独立小卡片列表")
require(dormitory, 'class="table-wrap building-compact-table"', "楼栋概况应使用集中式高密度表格")
require(dormitory, '<table', "楼栋概况应集中展示楼栋详细信息")

residency = read("frontend/src/views/admin/AdminResidencyView.vue")
require(residency, ':open="Boolean(ending)"', "办理退宿必须使用公共 AppModal")
forbid(residency, '<div v-if="ending" class="modal-overlay"', "办理退宿不得保留私有遮罩")

confirmation = read("frontend/src/views/admin/AdminBedConfirmationView.vue")
require(confirmation, "buildingFilter", "学生申报核查必须提供楼栋下拉筛选")
require(confirmation, "全部楼栋", "学生申报核查必须提供全部楼栋选项")

operations_view = read("frontend/src/views/admin/AdminOperationsView.vue")
operations_page = read("frontend/src/features/admin-operations/composables/useAdminOperationsPage.ts")
operations_preview = read("frontend/src/features/admin-operations/components/FairnessPreviewPanel.vue")
require(operations_page, "const batches", "公平性预演必须加载可选批次")
require(operations_preview, "<select", "公平性预演批次必须使用下拉框")
require(operations_view, 'v-if="loading"', "运营数据加载期间必须只显示加载状态")
require(operations_view, "正在加载运营与健康数据", "运营页必须提供明确加载文案")

governance_view = read("frontend/src/views/admin/AdminGovernanceView.vue")
analytics_page = read("frontend/src/features/admin-governance/composables/useHistoricalAnalytics.ts")
analytics_panel = read("frontend/src/features/admin-governance/components/HistoricalAnalyticsPanel.vue")
forbid(governance_view, 'v-model="analyticsFilters"', "analyticsFilters 不得通过组件级 v-model 修改 const reactive")
forbid(analytics_panel, 'v-model="filters"', "历史分析面板不得通过组件级 v-model 修改响应式筛选对象")
require(analytics_panel, ':model-value="filters"', "历史分析筛选应使用显式 model-value")
require(analytics_panel, "@update:model-value=\"emit('update:filters', $event)\"", "历史分析筛选应显式向父级发送新值")
require(analytics_page, "Object.assign(filters, value)", "历史分析组合函数应显式合并筛选更新")

processor = read("backend-java/server/src/main/java/com/wust/dormitory/export/ExportTaskProcessor.java")
require(processor, "@Scheduled", "异步导出必须存在后台任务处理器")
require(processor, "claimNext", "后台处理器必须原子领取排队任务")
require(processor, "AUDIT_EXPORT", "后台处理器必须处理审计导出")
require(processor, "CUSTOM_REPORT", "后台处理器必须处理自定义报表导出")

export_service = read("backend-java/server/src/main/java/com/wust/dormitory/export/ExportTaskService.java")
require(export_service, "claimNext", "导出任务服务必须支持领取下一个排队任务")
require(export_service, "file_reference", "成功任务必须保留安全文件引用")

controller = read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminGovernanceAuditController.java")
require(controller, '/exports/{taskId}/download', "治理接口必须提供已完成导出下载入口")

export_panel = read("frontend/src/components/admin/ExportTaskPanel.vue")
require(export_panel, "download", "导出任务面板必须提供完成后的下载动作")
require(export_panel, "SUCCEEDED", "下载动作必须只对已完成任务开放")

dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
for token in (
    "BUILDING_CREATE", "ROOM_UPDATE", "ROOM_LAYOUT_UPDATE", "BATCH_PUBLISH",
    "RESIDENCY_END", "ROOM_CHANGE_APPROVE", "WAITLIST_ASSIGN",
    "AUDIT_EXPORT", "REPORT_EXPORT", "NOTIFICATION_SEND",
):
    require(dashboard, token, f"业务操作记录缺少中文动作映射：{token}")
require(dashboard, "auditResourceText", "业务操作目标类型应统一中文映射")

normalizer = read("backend-java/server/src/main/java/com/wust/dormitory/common/json/JdbcJsonNormalizer.java")
require(normalizer, "TemporalAccessor", "JDBC/Java 时间值必须在写入历史和审计前规范化")
require(read("backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyHistoryWriter.java"), "JdbcJsonNormalizer.normalize", "在住历史必须规范化数据库时间值")
require(read("backend-java/server/src/main/java/com/wust/dormitory/audit/AuditService.java"), "JdbcJsonNormalizer.normalize", "通用审计必须规范化数据库时间值")

print("admin runtime and governance UI contract: OK")
