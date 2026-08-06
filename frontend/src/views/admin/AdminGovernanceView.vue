<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import AppConfirmDialog from '../../components/modal/AppConfirmDialog.vue'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import AuditSearchPanel from '../../features/admin-governance/components/AuditSearchPanel.vue'
import DataRetentionPanel from '../../features/admin-governance/components/DataRetentionPanel.vue'
import HistoricalAnalyticsPanel from '../../features/admin-governance/components/HistoricalAnalyticsPanel.vue'
import NotificationCenterPanel from '../../features/admin-governance/components/NotificationCenterPanel.vue'
import ReportExportPanel from '../../features/admin-governance/components/ReportExportPanel.vue'
import { useAuditSearch } from '../../features/admin-governance/composables/useAuditSearch'
import { useDataRetention } from '../../features/admin-governance/composables/useDataRetention'
import { useGovernanceExports } from '../../features/admin-governance/composables/useGovernanceExports'
import { useHistoricalAnalytics } from '../../features/admin-governance/composables/useHistoricalAnalytics'
import { useNotificationCenter } from '../../features/admin-governance/composables/useNotificationCenter'
import { useReportWorkspace } from '../../features/admin-governance/composables/useReportWorkspace'

const { hasFeature } = useFeatureAccess()
type TabKey = 'audit' | 'notification' | 'analytics' | 'report' | 'retention'
const tabs: Array<{ key: TabKey; zh: string; en: string; features: string[] }> = [
  { key: 'audit', zh: '高级审计', en: 'Advanced audit', features: ['P2_AUDIT_ADVANCED_QUERY', 'P2_AUDIT_EXPORT'] },
  { key: 'notification', zh: '通知中心', en: 'Notification center', features: ['P3_NOTIFICATION_TEMPLATE_VIEW', 'P3_NOTIFICATION_TEMPLATE_MANAGE', 'P3_NOTIFICATION_SEND', 'P3_NOTIFICATION_SCHEDULE', 'P3_NOTIFICATION_DELIVERY_STATUS'] },
  { key: 'analytics', zh: '历史分析', en: 'Historical analytics', features: ['P3_HISTORICAL_DASHBOARD', 'P3_CROSS_BATCH_COMPARISON', 'P3_TREND_ANALYSIS'] },
  { key: 'report', zh: '自定义报表', en: 'Custom reports', features: ['P3_CUSTOM_REPORT_EXPORT'] },
  { key: 'retention', zh: '数据保留', en: 'Data retention', features: ['P3_DATA_RETENTION_QUERY'] },
]
const availableTabs = computed(() => tabs.filter((tab) => tab.features.some(hasFeature)))
const activeTab = ref<TabKey>('audit')

const canAuditQuery = computed(() => hasFeature('P2_AUDIT_ADVANCED_QUERY'))
const canAuditExport = computed(() => hasFeature('P2_AUDIT_EXPORT'))
const canMaskedExport = computed(() => canAuditExport.value && hasFeature('P2_EXPORT_DESENSITIZATION'))
const canSensitiveExport = computed(() => canAuditExport.value && hasFeature('P2_SENSITIVE_DATA_EXPORT'))
const canTemplateView = computed(() => hasFeature('P3_NOTIFICATION_TEMPLATE_VIEW') || hasFeature('P3_NOTIFICATION_TEMPLATE_MANAGE') || hasFeature('P3_NOTIFICATION_SEND'))
const canTemplateManage = computed(() => hasFeature('P3_NOTIFICATION_TEMPLATE_MANAGE'))
const canNotificationSend = computed(() => hasFeature('P3_NOTIFICATION_SEND'))
const canNotificationSchedule = computed(() => hasFeature('P3_NOTIFICATION_SCHEDULE'))
const canNotificationStatus = computed(() => hasFeature('P3_NOTIFICATION_DELIVERY_STATUS'))
const canHistoricalDashboard = computed(() => hasFeature('P3_HISTORICAL_DASHBOARD'))
const canComparison = computed(() => hasFeature('P3_CROSS_BATCH_COMPARISON'))
const canTrend = computed(() => hasFeature('P3_TREND_ANALYSIS'))
const canReport = computed(() => hasFeature('P3_CUSTOM_REPORT_EXPORT'))
const canRetention = computed(() => hasFeature('P3_DATA_RETENTION_QUERY'))

const {
  tasks: exportTasks,
  busy: exportBusy,
  error: exportError,
  message: exportMessage,
  load: loadExports,
  cancel: cancelExport,
} = useGovernanceExports()

const {
  filters: auditFilters,
  rows: auditRows,
  selectedAudit,
  total: auditTotal,
  busy: auditBusy,
  error: auditError,
  message: auditMessage,
  exportConfirm: auditExportConfirm,
  includeSensitive: auditIncludeSensitive,
  query: queryAudit,
  updateFilters: updateAuditFilters,
  reset: resetAudit,
  openExport: openAuditExport,
  requestExport: requestAuditExport,
  auditJson,
} = useAuditSearch(() => loadExports({ silent: true }))

const {
  templateDraft,
  templates,
  recipientCriteria,
  selectedTemplateRevisionId,
  recipientCount,
  preview: notificationPreview,
  tasks: notificationTasks,
  scheduledAt,
  busy: notificationBusy,
  error: notificationError,
  message: notificationMessage,
  loadTemplates: loadNotificationTemplates,
  loadStatus: loadNotificationStatus,
  saveTemplate,
  preflightRecipients,
  sendNotification,
  cancelTask: cancelNotification,
  updateTemplateDraft,
  updateRecipientCriteria,
} = useNotificationCenter({
  canTemplateManage: () => canTemplateManage.value,
  canNotificationSend: () => canNotificationSend.value,
  canNotificationStatus: () => canNotificationStatus.value,
})
const notificationConfirm = ref(false)

const {
  filters: analyticsFilters,
  definitions: metricDefinitions,
  items: analyticsItems,
  mode: analyticsMode,
  privacy: analyticsPrivacy,
  modes: analyticsModes,
  busy: analyticsBusy,
  error: analyticsError,
  message: analyticsMessage,
  loadDefinitions: loadMetrics,
  run: runAnalytics,
  updateFilters: updateAnalyticsFilters,
  reset: resetAnalytics,
} = useHistoricalAnalytics({
  canHistoricalDashboard: () => canHistoricalDashboard.value,
  canComparison: () => canComparison.value,
  canTrend: () => canTrend.value,
})

const {
  metadata: reportMetadata,
  definition: reportDefinition,
  reason: reportReason,
  busy: reportBusy,
  error: reportError,
  message: reportMessage,
  loadMetadata: loadReportMetadata,
  save: saveReport,
  exportReport,
  updateDefinition: updateReportDefinition,
} = useReportWorkspace(() => loadExports({ silent: true }))

const {
  policy: retentionPolicy,
  statistics: retentionStatistics,
  simulation: retentionSimulation,
  busy: retentionBusy,
  error: retentionError,
  message: retentionMessage,
  load: loadRetention,
  simulate: simulateRetention,
  preflight: retentionPreflight,
} = useDataRetention()
const retentionConfirm = ref(false)

watchEffect(() => {
  const visible = availableTabs.value
  if (visible.length && !visible.some((tab) => tab.key === activeTab.value)) {
    activeTab.value = visible[0].key
  }
})

onMounted(async () => {
  const requests: Promise<unknown>[] = []
  if (canAuditExport.value || canReport.value) requests.push(loadExports())
  if (canTemplateView.value) requests.push(loadNotificationTemplates())
  if (canNotificationStatus.value) requests.push(loadNotificationStatus())
  if (analyticsModes.value.length) requests.push(loadMetrics())
  if (canReport.value) requests.push(loadReportMetadata())
  if (canRetention.value) requests.push(loadRetention())
  if (canAuditQuery.value) requests.push(queryAudit())
  await Promise.allSettled(requests)
})
</script>

<template>
  <div class="content-column governance-page">
    <div class="page-title">
      <span class="eyebrow">GOVERNANCE CENTER</span>
      <h2>审计、通知与数据分析</h2>
      <p>学校业务审计、站内通知、历史快照、自定义报表和数据保留查询统一管理。外部短信、邮件和移动推送尚未接入。</p>
    </div>
    <p v-if="availableTabs.length===0" class="alert warning">当前服务未开通治理中心相关功能。</p>
    <nav v-else class="governance-tabs">
      <button
        v-for="tab in availableTabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      ><strong>{{ tab.zh }}</strong><span>{{ tab.en }}</span></button>
    </nav>

    <AuditSearchPanel
      v-if="activeTab==='audit'&&availableTabs.some(t=>t.key==='audit')"
      :filters="auditFilters"
      :rows="auditRows"
      :selected-audit="selectedAudit"
      :total="auditTotal"
      :tasks="exportTasks"
      :can-query="canAuditQuery"
      :can-export="canAuditExport"
      :can-masked-export="canMaskedExport"
      :can-sensitive-export="canSensitiveExport"
      :busy="auditBusy"
      :export-busy="exportBusy"
      :error="auditError"
      :message="auditMessage"
      :export-error="exportError"
      :export-message="exportMessage"
      :format-json="auditJson"
      @update:filters="updateAuditFilters"
      @search="queryAudit"
      @reset="resetAudit"
      @select-audit="selectedAudit=$event"
      @request-export="openAuditExport"
      @refresh-exports="loadExports"
      @cancel-export="cancelExport"
    />

    <NotificationCenterPanel
      v-if="activeTab==='notification'&&availableTabs.some(t=>t.key==='notification')"
      :template-draft="templateDraft"
      :templates="templates"
      :recipient-criteria="recipientCriteria"
      :selected-template-revision-id="selectedTemplateRevisionId"
      :recipient-count="recipientCount"
      :preview="notificationPreview"
      :tasks="notificationTasks"
      :scheduled-at="scheduledAt"
      :can-template-manage="canTemplateManage"
      :can-notification-send="canNotificationSend"
      :can-notification-schedule="canNotificationSchedule"
      :can-notification-status="canNotificationStatus"
      :busy="notificationBusy"
      :error="notificationError"
      :message="notificationMessage"
      @update:template-draft="updateTemplateDraft"
      @update:recipient-criteria="updateRecipientCriteria"
      @update:selected-template-revision-id="selectedTemplateRevisionId=$event"
      @update:scheduled-at="scheduledAt=$event"
      @save-template="saveTemplate"
      @preflight="preflightRecipients"
      @confirm-send="notificationConfirm=true"
      @cancel-task="cancelNotification"
    />

    <HistoricalAnalyticsPanel
      v-if="activeTab==='analytics'&&availableTabs.some(t=>t.key==='analytics')"
      :filters="analyticsFilters"
      :definitions="metricDefinitions"
      :items="analyticsItems"
      :mode="analyticsMode"
      :modes="analyticsModes"
      :privacy="analyticsPrivacy"
      :busy="analyticsBusy"
      :error="analyticsError"
      :message="analyticsMessage"
      @update:filters="updateAnalyticsFilters"
      @update:mode="analyticsMode=$event"
      @run="runAnalytics"
      @reset="resetAnalytics"
    />

    <ReportExportPanel
      v-if="activeTab==='report'&&canReport"
      :metadata="reportMetadata"
      :definition="reportDefinition"
      :reason="reportReason"
      :tasks="exportTasks"
      :busy="reportBusy"
      :export-busy="exportBusy"
      :error="reportError"
      :message="reportMessage"
      :export-error="exportError"
      :export-message="exportMessage"
      @update:definition="updateReportDefinition"
      @update:reason="reportReason=$event"
      @save="saveReport"
      @export="exportReport"
      @refresh-exports="loadExports"
      @cancel-export="cancelExport"
    />

    <DataRetentionPanel
      v-if="activeTab==='retention'&&canRetention"
      :policy="retentionPolicy"
      :statistics="retentionStatistics"
      :simulation="retentionSimulation"
      :busy="retentionBusy"
      :error="retentionError"
      :message="retentionMessage"
      @simulate="simulateRetention"
      @confirm-preflight="retentionConfirm=true"
    />

    <AppConfirmDialog
      :open="auditExportConfirm"
      :title="auditIncludeSensitive?'确认完整敏感审计导出':'确认脱敏审计导出'"
      :variant="auditIncludeSensitive?'danger':'warning'"
      :confirmation-word="auditIncludeSensitive?'完整导出':''"
      require-reason
      reason-label="导出原因"
      confirm-text="创建异步导出任务"
      :action="requestAuditExport"
      @close="auditExportConfirm=false"
    />
    <AppConfirmDialog
      :open="notificationConfirm"
      title="确认发送站内通知"
      description="请再次核对接收人数和内容预览。服务重启不会重复发送，同一任务使用唯一执行标识。"
      variant="warning"
      require-reason
      reason-label="发送原因"
      confirm-text="确认创建发送任务"
      :action="sendNotification"
      @close="notificationConfirm=false"
    />
    <AppConfirmDialog
      :open="retentionConfirm"
      title="确认记录数据清理预检"
      description="该操作只记录策略快照和模拟结果，不会删除数据。"
      variant="danger"
      confirmation-word="仅预检"
      require-reason
      reason-label="预检原因"
      confirm-text="记录预检"
      :action="retentionPreflight"
      @close="retentionConfirm=false"
    />
  </div>
</template>

<style scoped>
.governance-page{gap:18px}.governance-tabs{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}.governance-tabs button{display:grid;gap:4px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface);text-align:left;color:inherit}.governance-tabs button.active{border-color:var(--primary);background:#eef4ff}.governance-tabs span{font-size:11px;color:var(--text-muted)}
</style>
