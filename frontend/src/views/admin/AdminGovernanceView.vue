<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import AnalyticsFilterBar from '../../components/admin/AnalyticsFilterBar.vue'
import AuditFilterBar from '../../components/admin/AuditFilterBar.vue'
import ExportTaskPanel from '../../components/admin/ExportTaskPanel.vue'
import MetricDefinitionPopover from '../../components/admin/MetricDefinitionPopover.vue'
import NotificationTemplateEditor from '../../components/admin/NotificationTemplateEditor.vue'
import RecipientSelector from '../../components/admin/RecipientSelector.vue'
import ReportBuilder from '../../components/admin/ReportBuilder.vue'
import AppConfirmDialog from '../../components/modal/AppConfirmDialog.vue'

const { hasFeature } = useFeatureAccess()
type TabKey = 'audit'|'notification'|'analytics'|'report'|'retention'
const tabs: Array<{key:TabKey;zh:string;en:string;features:string[]}> = [
  { key:'audit', zh:'高级审计', en:'Advanced audit', features:['P2_AUDIT_ADVANCED_QUERY','P2_AUDIT_EXPORT'] },
  { key:'notification', zh:'通知中心', en:'Notification center', features:['P3_NOTIFICATION_TEMPLATE_VIEW','P3_NOTIFICATION_TEMPLATE_MANAGE','P3_NOTIFICATION_SEND','P3_NOTIFICATION_SCHEDULE','P3_NOTIFICATION_DELIVERY_STATUS'] },
  { key:'analytics', zh:'历史分析', en:'Historical analytics', features:['P3_HISTORICAL_DASHBOARD','P3_CROSS_BATCH_COMPARISON','P3_TREND_ANALYSIS'] },
  { key:'report', zh:'自定义报表', en:'Custom reports', features:['P3_CUSTOM_REPORT_EXPORT'] },
  { key:'retention', zh:'数据保留', en:'Data retention', features:['P3_DATA_RETENTION_QUERY'] },
]
const availableTabs = computed(() => tabs.filter((tab) => tab.features.some(hasFeature)))
const activeTab = ref<TabKey>('audit')
const busy = ref(false)
const error = ref('')
const message = ref('')

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
const analyticsModes = computed(() => [
  canHistoricalDashboard.value ? {key:'dashboard',label:'历史看板'} : null,
  canComparison.value ? {key:'comparison',label:'跨批次比较'} : null,
  canTrend.value ? {key:'trend',label:'趋势分析'} : null,
].filter(Boolean) as Array<{key:'dashboard'|'comparison'|'trend';label:string}>)

const auditFilters = reactive({ occurredFrom:'', occurredTo:'', operatorId:'', operatorRole:'', module:'', actionType:'', targetType:'', targetId:'', success:'', errorCode:'', requestId:'', networkAddress:'', keyword:'' })
const auditRows = ref<DataObject[]>([])
const selectedAudit = ref<DataObject | null>(null)
const auditTotal = ref(0)
const exportTasks = ref<DataObject[]>([])
const auditExportConfirm = ref(false)
const auditIncludeSensitive = ref(false)
const templateDraft = reactive({ templateCode:'', templateName:'', titleZhCn:'', contentZhCn:'', titleEnUs:'', contentEnUs:'', enabled:true, creationReason:'' })
const templates = ref<DataObject[]>([])
const recipientCriteria = reactive({ studentIds:[] as number[], batchId:'', majorId:'', gradeYear:'', degreeLevel:'', studentCategory:'', buildingId:'', unselectedOnly:false, pendingReviewOnly:false })
const selectedTemplateRevisionId = ref('')
const recipientCount = ref<number|undefined>()
const notificationPreview = ref<DataObject|null>(null)
const notificationTasks = ref<DataObject[]>([])
const notificationConfirm = ref(false)
const scheduledAt = ref('')
const notificationReason = ref('')
const analyticsFilters = reactive({ academicYear:'', batchId:'', majorId:'', gradeYear:'', degreeLevel:'', studentCategory:'', campusId:'', buildingId:'', roomType:'' })
const metricDefinitions = ref<DataObject[]>([])
const analyticsItems = ref<DataObject[]>([])
const analyticsMode = ref<'dashboard'|'comparison'|'trend'>('dashboard')
const analyticsPrivacy = ref<DataObject>({})
const reportMetadata = reactive({ fields:[] as string[], filters:[] as string[], sorts:[] as string[], metrics:[] as string[] })
const reportDefinition = reactive({ name:'批次历史分析报表', fields:[] as string[], filters:{} as Record<string,unknown>, sorts:[] as string[], metrics:[] as string[], locale:'zh-CN' as 'zh-CN'|'en-US' })
const reportReason = ref('')
const retentionPolicy = ref<DataObject|null>(null)
const retentionStatistics = ref<DataObject|null>(null)
const retentionSimulation = ref<DataObject|null>(null)
const retentionConfirm = ref(false)
let exportPollTimer:number|undefined

const auditPayload = computed(() => ({ occurredFrom:toIso(auditFilters.occurredFrom), occurredTo:toIso(auditFilters.occurredTo), operatorId:numberOrNull(auditFilters.operatorId), operatorRole:auditFilters.operatorRole, module:auditFilters.module, actionType:auditFilters.actionType, targetType:auditFilters.targetType, targetId:auditFilters.targetId, success:auditFilters.success===''?null:auditFilters.success==='true', errorCode:auditFilters.errorCode, requestId:auditFilters.requestId, networkAddress:auditFilters.networkAddress, keyword:auditFilters.keyword, page:1, size:50 }))

watchEffect(() => {
  const visible = availableTabs.value
  if (visible.length && !visible.some((tab) => tab.key === activeTab.value)) activeTab.value = visible[0].key
  const modes = analyticsModes.value
  if (modes.length && !modes.some((mode) => mode.key === analyticsMode.value)) analyticsMode.value = modes[0].key
})

onMounted(async () => {
  const requests: Promise<unknown>[] = []
  if (canAuditExport.value || hasFeature('P3_CUSTOM_REPORT_EXPORT')) requests.push(loadExports())
  if (canTemplateView.value) requests.push(loadTemplates())
  if (canNotificationStatus.value) requests.push(loadNotificationStatus())
  if (analyticsModes.value.length) requests.push(loadMetrics())
  if (hasFeature('P3_CUSTOM_REPORT_EXPORT')) requests.push(loadReportMetadata())
  if (hasFeature('P3_DATA_RETENTION_QUERY')) requests.push(loadRetention())
  if (canAuditQuery.value) requests.push(queryAudit())
  await Promise.allSettled(requests)
})

async function execute(action:()=>Promise<void>){ busy.value=true;error.value='';message.value='';try{await action()}catch(cause){error.value=cause instanceof Error?cause.message:'操作失败'}finally{busy.value=false} }
async function queryAudit(){if(!canAuditQuery.value)return;await execute(async()=>{const response=await api.post<ObjectSuccessResponse>('/api/v1/admin/governance/audit/query',auditPayload.value);const data=(response.data.data??{}) as DataObject;auditRows.value=(data.items??[]) as DataObject[];auditTotal.value=Number(data.total??auditRows.value.length)})}
function updateAuditFilters(value:typeof auditFilters){Object.assign(auditFilters,value)}
function resetAudit(){Object.assign(auditFilters,{occurredFrom:'',occurredTo:'',operatorId:'',operatorRole:'',module:'',actionType:'',targetType:'',targetId:'',success:'',errorCode:'',requestId:'',networkAddress:'',keyword:''})}
async function requestAuditExport(payload:{reason:string}){await api.post('/api/v1/admin/governance/audit/export',{query:auditPayload.value,includeSensitiveData:auditIncludeSensitive.value,reason:payload.reason});message.value='审计导出已进入异步任务队列。';await loadExports()}
async function loadExports(){const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/exports');exportTasks.value=(response.data.data??[]) as DataObject[];syncExportPolling()}
function syncExportPolling(){const active=exportTasks.value.some(task=>['QUEUED','RUNNING'].includes(String(task.task_status)));if(active&&!exportPollTimer)exportPollTimer=window.setInterval(()=>void loadExports(),2000);else if(!active&&exportPollTimer){window.clearInterval(exportPollTimer);exportPollTimer=undefined}}
onBeforeUnmount(()=>{if(exportPollTimer)window.clearInterval(exportPollTimer)})
async function cancelExport(id:number){await execute(async()=>{await api.post(`/api/v1/admin/governance/exports/${id}/cancel`);await loadExports()})}
function updateTemplateDraft(value:typeof templateDraft){Object.assign(templateDraft,value)}
function updateRecipientCriteria(value:typeof recipientCriteria){Object.assign(recipientCriteria,value)}
async function loadTemplates(){const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/templates');templates.value=(response.data.data??[]) as DataObject[]}
async function saveTemplate(){if(!canTemplateManage.value)return;await execute(async()=>{await api.post('/api/v1/admin/governance/notifications/templates/revisions',templateDraft);message.value='通知模板修订已保存，旧修订保持不变。';await loadTemplates()})}
function criteriaPayload(){return{studentIds:recipientCriteria.studentIds,batchId:numberOrNull(recipientCriteria.batchId),majorId:numberOrNull(recipientCriteria.majorId),gradeYear:numberOrNull(recipientCriteria.gradeYear),degreeLevel:recipientCriteria.degreeLevel,studentCategory:recipientCriteria.studentCategory,buildingId:numberOrNull(recipientCriteria.buildingId),unselectedOnly:recipientCriteria.unselectedOnly,pendingReviewOnly:recipientCriteria.pendingReviewOnly}}
async function preflightRecipients(){if(!canNotificationSend.value)return;await execute(async()=>{const response=await api.post<ObjectSuccessResponse>('/api/v1/admin/governance/notifications/preflight',{criteria:criteriaPayload(),templateRevisionId:Number(selectedTemplateRevisionId.value),variables:{}});notificationPreview.value=(response.data.data??{}) as DataObject;recipientCount.value=Number(notificationPreview.value.recipientCount??0)})}
async function sendNotification(payload:{reason:string}){await api.post('/api/v1/admin/governance/notifications/schedule',{criteria:criteriaPayload(),templateRevisionId:Number(selectedTemplateRevisionId.value),variables:{},scheduledAt:toIso(scheduledAt.value),zoneId:'Asia/Shanghai',reason:payload.reason||notificationReason.value});message.value=scheduledAt.value?'定时站内通知已创建。':'站内通知已进入发送任务。';if(canNotificationStatus.value)await loadNotificationStatus()}
async function loadNotificationStatus(){const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/status');notificationTasks.value=(response.data.data??[]) as DataObject[]}
async function cancelNotification(id:number){await execute(async()=>{await api.post(`/api/v1/admin/governance/notifications/${id}/cancel`);await loadNotificationStatus()})}
function analyticsPayload(){return{academicYear:numberOrNull(analyticsFilters.academicYear),batchId:numberOrNull(analyticsFilters.batchId),majorId:numberOrNull(analyticsFilters.majorId),gradeYear:numberOrNull(analyticsFilters.gradeYear),degreeLevel:analyticsFilters.degreeLevel,studentCategory:analyticsFilters.studentCategory,campusId:numberOrNull(analyticsFilters.campusId),buildingId:numberOrNull(analyticsFilters.buildingId),roomType:analyticsFilters.roomType}}
async function loadMetrics(){const response=await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/analytics/definitions');metricDefinitions.value=((((response.data.data??{}) as DataObject).items??[]) as DataObject[])}
async function runAnalytics(){await execute(async()=>{const response=await api.post<ObjectSuccessResponse>(`/api/v1/admin/governance/analytics/${analyticsMode.value}`,analyticsPayload());const data=(response.data.data??{}) as DataObject;analyticsItems.value=(data.items??[]) as DataObject[];analyticsPrivacy.value=data})}
function updateAnalyticsFilters(value:typeof analyticsFilters){Object.assign(analyticsFilters,value)}
function resetAnalytics(){Object.assign(analyticsFilters,{academicYear:'',batchId:'',majorId:'',gradeYear:'',degreeLevel:'',studentCategory:'',campusId:'',buildingId:'',roomType:''})}
function updateReportDefinition(value: typeof reportDefinition){Object.assign(reportDefinition, value)}
function auditJson(value:unknown){if(value==null||value==='')return '无';try{return JSON.stringify(typeof value==='string'?JSON.parse(value):value,null,2)}catch{return String(value)}}
async function loadReportMetadata(){const response=await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/reports/metadata');const data=(response.data.data??{}) as DataObject;reportMetadata.fields=(data.fields??[]) as string[];reportMetadata.filters=(data.filters??[]) as string[];reportMetadata.sorts=(data.sorts??[]) as string[];reportMetadata.metrics=(data.metrics??[]) as string[]}
async function saveReport(){await execute(async()=>{await api.post('/api/v1/admin/governance/reports/templates',{definition:reportDefinition,reason:reportReason.value});message.value='报表模板已保存。'})}
async function exportReport(){await execute(async()=>{await api.post('/api/v1/admin/governance/reports/export',{definition:reportDefinition,reason:reportReason.value});message.value='报表已进入异步生成队列。';await loadExports()})}
async function loadRetention(){const[policy,stats]=await Promise.all([api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/policy'),api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/statistics')]);retentionPolicy.value=(policy.data.data??{}) as DataObject;retentionStatistics.value=(stats.data.data??{}) as DataObject}
async function simulateRetention(){await execute(async()=>{const response=await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/simulate');retentionSimulation.value=(response.data.data??{}) as DataObject})}
async function retentionPreflight(payload:{reason:string}){await api.post('/api/v1/admin/governance/retention/preflight',{reason:payload.reason});message.value='数据保留清理预检已记录，本轮不会执行删除。';await simulateRetention()}
function numberOrNull(value:string){const parsed=Number(value);return value.trim()&&Number.isFinite(parsed)?parsed:null}
function toIso(value:string){return value?new Date(value).toISOString():null}
</script>

<template>
  <div class="content-column governance-page">
    <div class="page-title"><span class="eyebrow">GOVERNANCE CENTER</span><h2>审计、通知与数据分析</h2><p>学校业务审计、站内通知、历史快照、自定义报表和数据保留查询统一管理。外部短信、邮件和移动推送尚未接入。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="availableTabs.length===0" class="alert warning">当前服务未开通治理中心相关功能。</p>
    <nav v-else class="governance-tabs"><button v-for="tab in availableTabs" :key="tab.key" type="button" :class="{active:activeTab===tab.key}" @click="activeTab=tab.key"><strong>{{ tab.zh }}</strong><span>{{ tab.en }}</span></button></nav>

    <section v-if="activeTab==='audit'&&availableTabs.some(t=>t.key==='audit')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">AUDIT</span><h3>高级审计</h3><p>审计记录不可由学校管理员修改或删除；平台授权审计不在此查询中。</p></div></header>
      <AuditFilterBar v-if="canAuditQuery" :model-value="auditFilters" @update:model-value="updateAuditFilters" :busy="busy" @search="queryAudit" @reset="resetAudit" />
      <p v-else class="alert warning">当前仅开通审计导出，未开通高级查询。</p>
      <div class="button-row"><button v-if="canMaskedExport" class="button secondary" type="button" @click="auditIncludeSensitive=false;auditExportConfirm=true">脱敏导出</button><button v-if="canSensitiveExport" class="button danger" type="button" @click="auditIncludeSensitive=true;auditExportConfirm=true">完整敏感导出</button><span class="result-count">查询结果 {{ auditTotal }} 条</span></div>
      <div v-if="canAuditQuery" class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>目标</th><th>结果</th><th>请求编号</th><th>网络地址</th><th>详情</th></tr></thead><tbody><tr v-for="row in auditRows" :key="String(row.id)"><td>{{ row.occurred_at }}</td><td>{{ row.operator_type }} #{{ row.operator_user_id }}</td><td>{{ row.action_type }}</td><td>{{ row.resource_type }} {{ row.resource_id }}</td><td>{{ row.result_status }}</td><td>{{ row.request_id }}</td><td>{{ row.network_address }}</td><td><button class="button ghost small" type="button" @click="selectedAudit=row">查看</button></td></tr></tbody></table></div><article v-if="selectedAudit" class="audit-detail-card"><div class="section-head"><div><span class="eyebrow">AUDIT DETAIL</span><h4>{{ selectedAudit.action_type }}</h4><p>{{ selectedAudit.module || selectedAudit.resource_type }} · {{ selectedAudit.occurred_at }}</p></div><button class="button ghost small" type="button" @click="selectedAudit=null">关闭</button></div><div class="audit-detail-grid"><div><span>结果</span><strong>{{ selectedAudit.result_status }}</strong></div><div><span>错误代码</span><strong>{{ selectedAudit.error_code || '无' }}</strong></div><div><span>原因</span><strong>{{ selectedAudit.reason || '未填写' }}</strong></div><div><span>请求编号</span><strong>{{ selectedAudit.request_id || '无' }}</strong></div></div><div class="audit-json-grid"><div><strong>变更前</strong><pre>{{ auditJson(selectedAudit.before_data) }}</pre></div><div><strong>变更后</strong><pre>{{ auditJson(selectedAudit.after_data) }}</pre></div></div></article>
      <ExportTaskPanel v-if="canAuditExport" :tasks="exportTasks" :busy="busy" @refresh="loadExports" @cancel="cancelExport" />
    </section>

    <section v-if="activeTab==='notification'&&availableTabs.some(t=>t.key==='notification')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">NOTIFICATION</span><h3>统一通知中心</h3><p>Notification center · 本轮只实现站内通知，短信、邮件、移动推送和渐进式网页应用推送未接入。</p></div></header>
      <NotificationTemplateEditor v-if="canTemplateManage" :model-value="templateDraft" @update:model-value="updateTemplateDraft" :busy="busy" @save="saveTemplate" />
      <div v-if="canNotificationSend" class="notification-compose"><label><span>模板修订</span><select v-model="selectedTemplateRevisionId" class="input"><option value="">请选择</option><option v-for="item in templates" :key="String(item.revision_id)" :value="String(item.revision_id)">{{ item.template_name }} · 修订{{ item.revision }}</option></select></label><RecipientSelector :model-value="recipientCriteria" @update:model-value="updateRecipientCriteria" :recipient-count="recipientCount" :busy="busy" @preflight="preflightRecipients" /><div v-if="notificationPreview" class="preview-card"><strong>内容预览</strong><span>接收人 {{ notificationPreview.recipientCount }} 人</span><p>{{ notificationPreview.titleZhCn }}</p><p>{{ notificationPreview.contentZhCn }}</p></div><label v-if="canNotificationSchedule"><span>定时发送时间（留空立即执行）</span><input v-model="scheduledAt" class="input" type="datetime-local" /></label><button class="button primary" type="button" :disabled="!notificationPreview" @click="notificationConfirm=true">确认发送范围与内容</button></div>
      <div v-if="canNotificationStatus" class="task-list"><article v-for="task in notificationTasks" :key="String(task.id)"><strong>{{ task.task_status }}</strong><span>{{ task.recipient_count }}人 · {{ task.scheduled_at }} · {{ task.time_zone }}</span><button v-if="task.task_status==='SCHEDULED'&&canNotificationSchedule" class="button ghost small" @click="cancelNotification(Number(task.id))">取消</button></article></div>
    </section>

    <section v-if="activeTab==='analytics'&&availableTabs.some(t=>t.key==='analytics')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">ANALYTICS</span><h3>历史分析</h3><p>Historical analytics · 已结束批次使用不可变快照，后续换寝不会改变历史口径。</p></div></header>
      <div class="mode-switch"><button v-for="mode in analyticsModes" :key="mode.key" class="button" :class="analyticsMode===mode.key?'primary':'ghost'" @click="analyticsMode=mode.key">{{ mode.label }}</button></div>
      <AnalyticsFilterBar :model-value="analyticsFilters" @update:model-value="updateAnalyticsFilters" :busy="busy" @apply="runAnalytics" @reset="resetAnalytics" />
      <div class="analytics-summary-grid"><article><span>结果批次</span><strong>{{ analyticsItems.length }}</strong></article><article><span>统计口径</span><strong>{{ analyticsItems[0]?.metric_version || '待查询' }}</strong></article><article><span>隐私阈值</span><strong>{{ analyticsPrivacy.privacyThreshold || '—' }}</strong></article></div><p v-if="analyticsPrivacy.preferenceDimensionsSuppressed" class="alert warning">当前组合样本少于 {{ analyticsPrivacy.privacyThreshold }} 人，已隐藏个人偏好维度。</p>
      <div class="metric-definition-grid"><article v-for="definition in metricDefinitions" :key="String(definition.code)"><div><strong>{{ definition.nameZhCn }}</strong><span>{{ definition.nameEnUs }}</span></div><MetricDefinitionPopover :definition="definition" /></article></div>
      <div class="analytics-result-grid"><article v-for="item in analyticsItems" :key="String(item.id)"><strong>{{ item.batch_name }}</strong><span>{{ item.metric_version }} · {{ item.data_updated_at }}</span><pre>{{ JSON.stringify(item.metrics,null,2) }}</pre></article></div>
    </section>

    <section v-if="activeTab==='report'&&hasFeature('P3_CUSTOM_REPORT_EXPORT')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">REPORT</span><h3>自定义报表</h3><p>只能使用字段、筛选、排序和指标白名单，禁止输入任意结构化查询语言。</p></div></header>
      <label><span>保存或生成原因</span><textarea v-model="reportReason" class="input" rows="3" /></label><ReportBuilder :model-value="reportDefinition" @update:model-value="updateReportDefinition" v-bind="reportMetadata" :busy="busy" @save="saveReport" @export="exportReport" /><ExportTaskPanel :tasks="exportTasks" :busy="busy" @refresh="loadExports" @cancel="cancelExport" />
    </section>

    <section v-if="activeTab==='retention'&&hasFeature('P3_DATA_RETENTION_QUERY')" class="panel governance-section">
      <header class="section-head"><div><span class="eyebrow">RETENTION</span><h3>数据保留查询</h3><p>Data retention · 本轮仅展示策略、到期统计、模拟清理和清理预检，不执行生产删除、备份或恢复。</p></div></header>
      <div v-if="retentionPolicy" class="retention-summary"><article><span>业务数据保留</span><strong>{{ retentionPolicy.dataRetentionDays }}天</strong></article><article><span>审计保留</span><strong>{{ retentionPolicy.auditRetentionDays }}天</strong></article><article><span>执行清理</span><strong>未开放</strong></article></div>
      <div class="button-row"><button class="button secondary" type="button" @click="simulateRetention">模拟清理</button><button class="button danger" type="button" @click="retentionConfirm=true">记录清理预检</button></div><div v-if="retentionStatistics" class="json-card"><strong>到期数据统计</strong><pre>{{ JSON.stringify(retentionStatistics,null,2) }}</pre></div><div v-if="retentionSimulation" class="json-card"><strong>受保护数据与模拟结果</strong><pre>{{ JSON.stringify(retentionSimulation,null,2) }}</pre></div>
    </section>

    <AppConfirmDialog :open="auditExportConfirm" :title="auditIncludeSensitive?'确认完整敏感审计导出':'确认脱敏审计导出'" :variant="auditIncludeSensitive?'danger':'warning'" :confirmation-word="auditIncludeSensitive?'完整导出':''" require-reason reason-label="导出原因" confirm-text="创建异步导出任务" :action="requestAuditExport" @close="auditExportConfirm=false" />
    <AppConfirmDialog :open="notificationConfirm" title="确认发送站内通知" description="请再次核对接收人数和内容预览。服务重启不会重复发送，同一任务使用唯一执行标识。" variant="warning" require-reason reason-label="发送原因" confirm-text="确认创建发送任务" :action="sendNotification" @close="notificationConfirm=false" />
    <AppConfirmDialog :open="retentionConfirm" title="确认记录数据清理预检" description="该操作只记录策略快照和模拟结果，不会删除数据。" variant="danger" confirmation-word="仅预检" require-reason reason-label="预检原因" confirm-text="记录预检" :action="retentionPreflight" @close="retentionConfirm=false" />
  </div>
</template>

<style scoped>
.governance-page{gap:18px}.audit-detail-card{display:grid;gap:14px;margin-top:14px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.audit-detail-grid,.analytics-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.audit-detail-grid div,.analytics-summary-grid article{display:grid;gap:5px;padding:11px;border-radius:11px;background:var(--surface)}.audit-detail-grid span,.analytics-summary-grid span{color:var(--text-muted);font-size:12px}.audit-json-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.audit-json-grid pre{max-height:240px;overflow:auto;white-space:pre-wrap}.analytics-summary-grid{grid-template-columns:repeat(3,minmax(0,1fr));margin:12px 0}@media(max-width:760px){.audit-detail-grid,.analytics-summary-grid,.audit-json-grid{grid-template-columns:1fr}}.governance-tabs{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}.governance-tabs button{display:grid;gap:4px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface);text-align:left;color:inherit}.governance-tabs button.active{border-color:var(--primary);background:#eef4ff}.governance-tabs span{font-size:11px;color:var(--text-muted)}.governance-section{display:grid;gap:18px}.result-count{margin-left:auto;color:var(--text-muted)}.notification-compose{display:grid;gap:14px;padding-top:16px;border-top:1px solid var(--border)}.notification-compose>label,.governance-section>label{display:grid;gap:7px}.preview-card,.json-card{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.preview-card p{margin:0}.task-list{display:grid;gap:8px}.task-list article{display:flex;align-items:center;gap:12px;padding:11px;border:1px solid var(--border);border-radius:11px}.task-list article span{color:var(--text-muted);font-size:12px;flex:1}.mode-switch{display:flex;gap:8px;flex-wrap:wrap}.metric-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px}.metric-definition-grid article{display:flex;justify-content:space-between;gap:10px;padding:12px;border:1px solid var(--border);border-radius:12px}.metric-definition-grid article>div{display:grid;gap:3px}.metric-definition-grid span{color:var(--text-muted);font-size:12px}.analytics-result-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}.analytics-result-grid article{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px}.analytics-result-grid span{color:var(--text-muted);font-size:12px}.analytics-result-grid pre,.json-card pre{max-height:360px;overflow:auto;margin:0;white-space:pre-wrap;font-size:11px}.retention-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.retention-summary article{padding:16px;border-radius:13px;background:var(--surface-soft)}.retention-summary span,.retention-summary strong{display:block}.retention-summary span{color:var(--text-muted);font-size:12px}.retention-summary strong{margin-top:5px;font-size:22px}@media(max-width:620px){.retention-summary{grid-template-columns:1fr}.task-list article{align-items:flex-start;flex-direction:column}}
</style>
