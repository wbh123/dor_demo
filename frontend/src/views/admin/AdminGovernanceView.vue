<script setup lang="ts">
import { computed, onMounted, reactive, ref, watchEffect } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import ExportTaskPanel from '../../components/admin/ExportTaskPanel.vue'
import ReportBuilder from '../../components/admin/ReportBuilder.vue'
import AppConfirmDialog from '../../components/modal/AppConfirmDialog.vue'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import AuditSearchPanel from '../../features/admin-governance/components/AuditSearchPanel.vue'
import HistoricalAnalyticsPanel from '../../features/admin-governance/components/HistoricalAnalyticsPanel.vue'
import NotificationCenterPanel from '../../features/admin-governance/components/NotificationCenterPanel.vue'
import { useAuditSearch } from '../../features/admin-governance/composables/useAuditSearch'
import { useGovernanceExports } from '../../features/admin-governance/composables/useGovernanceExports'
import { useHistoricalAnalytics } from '../../features/admin-governance/composables/useHistoricalAnalytics'
import { useNotificationCenter } from '../../features/admin-governance/composables/useNotificationCenter'

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

const busy = ref(false)
const error = ref('')
const message = ref('')
const reportMetadata = reactive({ fields: [] as string[], filters: [] as string[], sorts: [] as string[], metrics: [] as string[] })
const reportDefinition = reactive({ name: '批次历史分析报表', fields: [] as string[], filters: {} as Record<string, unknown>, sorts: [] as string[], metrics: [] as string[], locale: 'zh-CN' as 'zh-CN' | 'en-US' })
const reportReason = ref('')
const retentionPolicy = ref<DataObject | null>(null)
const retentionStatistics = ref<DataObject | null>(null)
const retentionSimulation = ref<DataObject | null>(null)
const retentionConfirm = ref(false)

watchEffect(() => {
  const visible = availableTabs.value
  if (visible.length && !visible.some((tab) => tab.key === activeTab.value)) {
    activeTab.value = visible[0].key
  }
})

onMounted(async () => {
  const requests: Promise<unknown>[] = []
  if (canAuditExport.value || hasFeature('P3_CUSTOM_REPORT_EXPORT')) requests.push(loadExports())
  if (canTemplateView.value) requests.push(loadNotificationTemplates())
  if (canNotificationStatus.value) requests.push(loadNotificationStatus())
  if (analyticsModes.value.length) requests.push(loadMetrics())
  if (hasFeature('P3_CUSTOM_REPORT_EXPORT')) requests.push(loadReportMetadata())
  if (hasFeature('P3_DATA_RETENTION_QUERY')) requests.push(loadRetention())
  if (canAuditQuery.value) requests.push(queryAudit())
  await Promise.allSettled(requests)
})

async function execute(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '操作失败'
  } finally {
    busy.value = false
  }
}

function updateReportDefinition(value: typeof reportDefinition) {
  Object.assign(reportDefinition, value)
}

async function loadReportMetadata() {
  const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/reports/metadata')
  const data = (response.data.data ?? {}) as DataObject
  reportMetadata.fields = (data.fields ?? []) as string[]
  reportMetadata.filters = (data.filters ?? []) as string[]
  reportMetadata.sorts = (data.sorts ?? []) as string[]
  reportMetadata.metrics = (data.metrics ?? []) as string[]
}

async function saveReport() {
  await execute(async () => {
    await api.post('/api/v1/admin/governance/reports/templates', {
      definition: reportDefinition,
      reason: reportReason.value,
    })
    message.value = '报表模板已保存。'
  })
}

async function exportReport() {
  await execute(async () => {
    await api.post('/api/v1/admin/governance/reports/export', {
      definition: reportDefinition,
      reason: reportReason.value,
    })
    message.value = '报表已进入异步生成队列。'
    await loadExports({ silent: true })
  })
}

async function loadRetention() {
  const [policy, stats] = await Promise.all([
    api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/policy'),
    api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/statistics'),
  ])
  retentionPolicy.value = (policy.data.data ?? {}) as DataObject
  retentionStatistics.value = (stats.data.data ?? {}) as DataObject
}

async function simulateRetention() {
  await execute(async () => {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/simulate')
    retentionSimulation.value = (response.data.data ?? {}) as DataObject
  })
}

async function retentionPreflight(payload: { reason: string }) {
  await api.post('/api/v1/admin/governance/retention/preflight', { reason: payload.reason })
  message.value = '数据保留清理预检已记录，本轮不会执行删除。'
  await simulateRetention()
}
</script>

<template>
  <div class="content-column governance-page">
    <div class="page-title">
      <span class="eyebrow">GOVERNANCE CENTER</span>
      <h2>审计、通知与数据分析</h2>
      <p>学校业务审计、站内通知、历史快照、自定义报表和数据保留查询统一管理。外部短信、邮件和移动推送尚未接入。</p>
    </div>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="availableTabs.length===0" class="alert warning">当前服务未开通治理中心相关功能。</p>
    <nav v-else class="governance-tabs">
      <button
        v-for="tab in availableTabs"
        :key="tab.key"
        type="button"
        :class="{active:activeTab===tab.key}"
        @click="activeTab=tab.key"
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

    <section v-if="activeTab==='report'&&hasFeature('P3_CUSTOM_REPORT_EXPORT')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">REPORT</span><h3>自定义报表</h3><p>只能使用字段、筛选、排序和指标白名单，禁止输入任意结构化查询语言。</p></div></header>
      <p v-if="exportError" class="alert error">{{ exportError }}</p>
      <p v-if="exportMessage" class="alert success">{{ exportMessage }}</p>
      <label><span>保存或生成原因</span><textarea v-model="reportReason" class="input" rows="3" /></label>
      <ReportBuilder
        :model-value="reportDefinition"
        v-bind="reportMetadata"
        :busy="busy"
        @update:model-value="updateReportDefinition"
        @save="saveReport"
        @export="exportReport"
      />
      <ExportTaskPanel :tasks="exportTasks" :busy="exportBusy" @refresh="loadExports" @cancel="cancelExport" />
    </section>

    <section v-if="activeTab==='retention'&&hasFeature('P3_DATA_RETENTION_QUERY')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">RETENTION</span><h3>数据保留查询</h3><p>Data retention · 本轮仅展示策略、到期统计、模拟清理和清理预检，不执行生产删除、备份或恢复。</p></div></header>
      <div v-if="retentionPolicy" class="retention-summary">
        <article><span>业务数据保留</span><strong>{{ retentionPolicy.dataRetentionDays }}天</strong></article>
        <article><span>审计保留</span><strong>{{ retentionPolicy.auditRetentionDays }}天</strong></article>
        <article><span>执行清理</span><strong>未开放</strong></article>
      </div>
      <div class="button-row">
        <button class="button secondary" type="button" @click="simulateRetention">模拟清理</button>
        <button class="button danger" type="button" @click="retentionConfirm=true">记录清理预检</button>
      </div>
      <div v-if="retentionStatistics" class="json-card"><strong>到期数据统计</strong><pre>{{ JSON.stringify(retentionStatistics,null,2) }}</pre></div>
      <div v-if="retentionSimulation" class="json-card"><strong>受保护数据与模拟结果</strong><pre>{{ JSON.stringify(retentionSimulation,null,2) }}</pre></div>
    </section>

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
.governance-page{gap:18px}.governance-tabs{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}.governance-tabs button{display:grid;gap:4px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface);text-align:left;color:inherit}.governance-tabs button.active{border-color:var(--primary);background:#eef4ff}.governance-tabs span{font-size:11px;color:var(--text-muted)}.governance-section{display:grid;gap:18px}.governance-section>label{display:grid;gap:7px}.json-card{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.json-card pre{max-height:360px;overflow:auto;margin:0;white-space:pre-wrap;font-size:11px}.retention-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.retention-summary article{padding:16px;border-radius:13px;background:var(--surface-soft)}.retention-summary span,.retention-summary strong{display:block}.retention-summary span{color:var(--text-muted);font-size:12px}.retention-summary strong{margin-top:5px;font-size:22px}@media(max-width:620px){.retention-summary{grid-template-columns:1fr}}
</style>
