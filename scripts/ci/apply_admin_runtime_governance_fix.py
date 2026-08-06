#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace(path: str, old: str, new: str) -> None:
    content = read(path)
    if old not in content:
        raise RuntimeError(f"patch anchor missing: {path}: {old[:80]!r}")
    write(path, content.replace(old, new, 1))


# MySQL 8.4 下避免使用可能与 SQL 语义冲突的 assignment 别名。
replace(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java",
    """                AND NOT EXISTS (\n                    SELECT 1 FROM room_assignment assignment\n                    WHERE assignment.bed_id=bed.id AND assignment.assignment_status='ACTIVE'\n                )\n""",
    """                AND NOT EXISTS (\n                    SELECT 1 FROM room_assignment active_residency\n                    WHERE active_residency.bed_id=bed.id\n                      AND active_residency.assignment_status='ACTIVE'\n                )\n""",
)

# 原因字段上限为500，完全重置不得再拼接前缀导致数据库截断。
replace(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAccountAdminService.java",
    'residencyService.end(residencyId, "管理员完全重置学生：" + reason.trim(), operator);',
    'residencyService.end(residencyId, reason.trim(), operator);',
)

# 楼栋概况改为集中式高密度表格。
path = "frontend/src/views/admin/AdminDormitoryView.vue"
content = read(path)
content = content.replace(
    "function genderText(value: unknown) { return String(value) === 'M' ? '男生宿舍' : String(value) === 'F' ? '女生宿舍' : '按房间设置性别' }\n",
    "function genderText(value: unknown) { return String(value) === 'M' ? '男生宿舍' : String(value) === 'F' ? '女生宿舍' : '混合或按房间设置' }\nfunction educationText(value: unknown) { return ({ UNDERGRADUATE_ONLY: '本科生', GRADUATE_ONLY: '研究生', MIXED: '本研混合' } as Record<string,string>)[String(value)] ?? '本研混合' }\n",
    1,
)
old = """      <div class=\"building-summary-list\">\n        <article v-for=\"building in buildings\" :key=\"String(building.id)\">\n          <div><strong>{{ building.building_name }}</strong><small>{{ building.building_code }} · {{ genderText(building.gender_restriction) }}</small></div>\n          <div class=\"room-facts\"><span>{{ building.room_count }}间</span><span>{{ building.bed_count }}床</span></div>\n        </article>\n        <p v-if=\"!buildings.length\" class=\"empty-state\">暂无楼栋信息。</p>\n      </div>\n"""
new = """      <div class=\"table-wrap building-summary-table\">\n        <table>\n          <thead><tr><th>楼栋</th><th>适住范围</th><th>房间</th><th>床位</th><th>状态</th></tr></thead>\n          <tbody>\n            <tr v-for=\"building in buildings\" :key=\"String(building.id)\">\n              <td><strong>{{ building.building_name }}</strong><small>{{ building.building_code }}</small></td>\n              <td><span>{{ genderText(building.gender_restriction) }}</span><small>{{ educationText(building.education_level_scope) }} · {{ scopeText(building.resident_scope) }}</small></td>\n              <td><strong>{{ building.room_count ?? 0 }}</strong><small>间房</small></td>\n              <td><strong>{{ building.bed_count ?? 0 }}</strong><small>个有效床位</small></td>\n              <td><span class=\"status-chip compact\">{{ building.enabled ? '启用' : '停用' }}</span></td>\n            </tr>\n          </tbody>\n        </table>\n        <p v-if=\"!buildings.length\" class=\"empty-state\">暂无楼栋信息。</p>\n      </div>\n"""
if old not in content:
    raise RuntimeError("AdminDormitoryView building overview anchor missing")
content = content.replace(old, new, 1)
content = content.replace(
    ".building-summary-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:9px}.building-summary-list article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border:1px solid var(--line);border-radius:",
    ".building-summary-table{margin-top:2px}.building-summary-table table{min-width:760px}.building-summary-table td{vertical-align:middle}.building-summary-table td:nth-child(3),.building-summary-table td:nth-child(4){width:110px}.building-summary-table small{display:block;margin-top:3px;color:var(--muted)}.building-summary-table strong{font-size:14px}.building-summary-table{border-radius:",
    1,
)
write(path, content)

# 退宿弹窗统一使用公共组件。
replace(
    "frontend/src/views/admin/AdminResidencyView.vue",
    """    <div v-if=\"ending\" class=\"modal-overlay\" @click.self=\"closeEndDialog\">\n      <section class=\"modal-card residency-end-dialog\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"residency-end-title\">\n        <span class=\"eyebrow\">END RESIDENCY</span><h3 id=\"residency-end-title\">办理 {{ ending.student_name }} 退宿</h3><p>退宿会结束当前在住记录并释放寝室容量，请填写可审计的处理原因。</p>\n        <label class=\"form-stack\"><span>退宿原因</span><textarea v-model.trim=\"endReason\" class=\"input\" rows=\"4\" maxlength=\"500\" placeholder=\"例如：学生申请退宿、转宿舍或毕业离校\"></textarea></label>\n        <div class=\"button-row dialog-actions\"><button class=\"button ghost\" type=\"button\" :disabled=\"endingResidency\" @click=\"closeEndDialog\">取消</button><button class=\"button danger\" type=\"button\" :disabled=\"!endReason.trim() || endingResidency\" @click=\"endResidency\">{{ endingResidency ? '处理中…' : '确认结束在住' }}</button></div>\n      </section>\n    </div>\n""",
    """    <AppModal :open=\"Boolean(ending)\" size=\"default\" :busy=\"endingResidency\" @close=\"closeEndDialog\">\n      <div v-if=\"ending\" class=\"residency-end-dialog\">\n        <span class=\"eyebrow\">END RESIDENCY</span><h3>办理 {{ ending.student_name }} 退宿</h3><p>退宿会结束当前在住记录并释放寝室容量，请填写可审计的处理原因。</p>\n        <label class=\"form-stack\"><span>退宿原因</span><textarea v-model.trim=\"endReason\" class=\"input\" rows=\"4\" maxlength=\"500\" placeholder=\"例如：学生申请退宿、转宿舍或毕业离校\"></textarea></label>\n        <div class=\"button-row dialog-actions\"><button class=\"button ghost\" type=\"button\" :disabled=\"endingResidency\" @click=\"closeEndDialog\">取消</button><button class=\"button danger\" type=\"button\" :disabled=\"!endReason.trim() || endingResidency\" @click=\"endResidency\">{{ endingResidency ? '处理中…' : '确认结束在住' }}</button></div>\n      </div>\n    </AppModal>\n""",
)
replace(
    "frontend/src/views/admin/AdminResidencyView.vue",
    ".residency-end-dialog{width:min(540px,calc(100vw - 32px));padding:24px}",
    ".residency-end-dialog{width:100%;padding:0}",
)

# 学生申报核查增加楼栋下拉筛选。
path = "frontend/src/views/admin/AdminBedConfirmationView.vue"
content = read(path)
content = content.replace("const reviewFilter = ref('ALL')\n", "const reviewFilter = ref('ALL')\nconst buildingFilter = ref('')\n", 1)
content = content.replace(
    "const readyCount = computed(() => students.value.filter(item => item.review_state === 'READY').length)\n",
    "const readyCount = computed(() => students.value.filter(item => item.review_state === 'READY').length)\nconst buildingOptions = computed(() => [...new Set(rooms.value.map(room => String(room.building_name ?? '')).filter(Boolean))].sort())\n",
    1,
)
content = content.replace(
    "const filteredRooms = computed(() => rooms.value.filter((room) => {\n",
    "const filteredRooms = computed(() => rooms.value.filter((room) => {\n  if (buildingFilter.value && String(room.building_name) !== buildingFilter.value) return false\n",
    1,
)
content = content.replace(
    "<section class=\"panel room-search\"><input v-model.trim=\"keyword\" class=\"input\" placeholder=\"搜索楼栋或寝室号\" @keyup.enter=\"loadRooms\"><select v-model=\"reviewFilter\" class=\"input\">",
    "<section class=\"panel room-search\"><input v-model.trim=\"keyword\" class=\"input\" placeholder=\"搜索楼栋或寝室号\" @keyup.enter=\"loadRooms\"><select v-model=\"buildingFilter\" class=\"input\"><option value=\"\">全部楼栋</option><option v-for=\"building in buildingOptions\" :key=\"building\" :value=\"building\">{{ building }}</option></select><select v-model=\"reviewFilter\" class=\"input\">",
    1,
)
content = content.replace(
    ".room-search{display:grid;grid-template-columns:minmax(220px,1fr) 220px auto;gap:10px}",
    ".room-search{display:grid;grid-template-columns:minmax(220px,1fr) 180px 220px auto;gap:10px}",
    1,
)
write(path, content)

# 运营与健康：批次下拉，加载完成前只显示加载状态。
write("frontend/src/views/admin/AdminOperationsView.vue", '''<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const overview = ref<DataObject>({})
const health = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const preview = ref<DataObject | null>(null)
const loading = ref(true)
const previewLoading = ref(false)
const error = ref('')
const form = reactive({ batchId: 0, randomSeed: 2026 })
const { subtitle, translateError } = useI18n()
const bedUtilization = computed(() => (overview.value.bedUtilization ?? {}) as DataObject)
const fairness = computed(() => (preview.value?.fairness ?? {}) as DataObject)
const selectableBatches = computed(() => batches.value.filter((batch) => !['DRAFT','CANCELLED'].includes(String(batch.batch_status))))

onMounted(load)
async function load(){
  loading.value=true;error.value=''
  try{
    const[overviewResponse,healthResponse,batchResponse]=await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/operations/overview'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/operations/health'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
    ])
    overview.value=(overviewResponse.data.data??{})as DataObject
    health.value=(healthResponse.data.data??{})as DataObject
    batches.value=(batchResponse.data.data??[])as DataObject[]
    if(!form.batchId&&selectableBatches.value.length)form.batchId=Number(selectableBatches.value[0].id)
  }catch(cause){error.value=translateError(cause)}finally{loading.value=false}
}
async function loadPreview(){
  if(!form.batchId||previewLoading.value)return
  previewLoading.value=true;error.value=''
  try{const response=await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${form.batchId}/allocation/optimized-preview`,{params:{randomSeed:form.randomSeed}});preview.value=(response.data.data??{})as DataObject}
  catch(cause){error.value=translateError(cause)}finally{previewLoading.value=false}
}
function number(value:unknown){return Number(value??0).toLocaleString()}
function batchLabel(batch:DataObject){return `${batch.batch_name??batch.batch_code??`批次${batch.id}`} · ${batch.batch_status??'未知状态'}`}
</script>

<template><div class="content-column"><header class="page-title split-title"><div><span class="eyebrow">{{subtitle('运营分析','OPERATIONS')}}</span><h2>运营与健康</h2><p>集中查看床位利用率、未选学生、人工调整和统一分配公平性。</p></div><button class="button secondary" :disabled="loading" @click="load">刷新</button></header>
<p v-if="loading" class="panel empty-state">正在加载运营与健康数据…</p>
<p v-else-if="error" class="alert error">{{error}}</p>
<template v-else>
<section class="metric-grid"><article class="panel metric-card"><span>启用床位利用率</span><strong>{{bedUtilization.rate??0}}%</strong><small>{{number(bedUtilization.occupiedBeds)}} / {{number(bedUtilization.enabledBeds)}} 张床</small></article><article class="panel metric-card"><span>未选学生</span><strong>{{number(overview.unselectedStudents)}}</strong><small>活动或已关闭批次中仍未入住</small></article><article class="panel metric-card"><span>人工调整</span><strong>{{number(overview.manualAdjustments)}}</strong><small>换床与换寝历史事件</small></article><article class="panel metric-card"><span>待审换寝</span><strong>{{number(overview.pendingRoomChanges)}}</strong><small>需要管理员处理</small></article><article class="panel metric-card"><span>床位待确认</span><strong>{{number(overview.unknownBedResidents)}}</strong><small>已住寝室但未确认具体床位</small></article><article class="panel metric-card"><span>活动批次</span><strong>{{number(overview.activeBatches)}}</strong><small>已发布、开放或暂停</small></article></section>
<section class="panel health-summary-card" :class="{ healthy: health.healthy, unhealthy: !health.healthy }"><div class="health-icon">{{health.healthy?'✓':'!'}}</div><div><span class="eyebrow">运行健康</span><h3>{{health.healthy?'系统运行正常':'系统需要检查'}}</h3><p>{{health.healthy?'核心服务当前可用，可以继续开展学生、宿舍和选寝业务。':'部分核心服务不可用，请联系系统管理员查看依赖、锁和数据库诊断详情。'}}</p></div><span class="health-badge" :class="{ok:health.healthy}">{{health.healthy?'健康':'异常'}}</span></section>
<section class="panel"><div class="section-head"><div><span class="eyebrow">统一分配评估</span><h3>公平性预演</h3><p>只读取候选结果并计算得分离散程度，不写入正式分配。</p></div></div><form class="preview-form" @submit.prevent="loadPreview"><label><span>选择批次</span><select v-model.number="form.batchId" class="input" required><option :value="0" disabled>请选择批次</option><option v-for="batch in selectableBatches" :key="String(batch.id)" :value="Number(batch.id)">{{batchLabel(batch)}}</option></select></label><label><span>随机种子</span><input v-model.number="form.randomSeed" class="input" type="number" required/></label><button class="button primary" :disabled="previewLoading||!form.batchId">{{previewLoading?'正在预演…':'运行预演'}}</button></form><div v-if="preview" class="preview-result"><article><span>平均得分</span><strong>{{fairness.averageScore}}</strong></article><article><span>最低得分</span><strong>{{fairness.minimumScore}}</strong></article><article><span>标准差</span><strong>{{fairness.standardDeviation}}</strong></article><article><span>公平性指数</span><strong>{{fairness.fairness}}</strong></article><article><span>已分配</span><strong>{{fairness.assignedCount}}</strong></article><article><span>未分配</span><strong>{{fairness.unassignedCount}}</strong></article><p>{{preview.notice}}</p></div></section>
</template></div></template>

<style scoped>.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.metric-card{display:grid;gap:5px}.metric-card span{color:var(--muted)}.metric-card strong{font-size:28px}.metric-card small{color:var(--muted)}.health-summary-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:16px}.health-summary-card.healthy{border-color:#c8eadc;background:#f7fdf9}.health-summary-card.unhealthy{border-color:#f1c9cf;background:#fff8f8}.health-summary-card h3{margin-bottom:4px}.health-summary-card p{margin:0}.health-icon{width:48px;height:48px;display:grid;place-items:center;border-radius:50%;color:white;background:#c33c4b;font-size:24px;font-weight:800}.healthy .health-icon{background:#158467}.health-badge{padding:6px 10px;border-radius:999px;background:#fff0f0;color:#a33}.health-badge.ok{background:#eafaf2;color:#16734f}.preview-form{display:grid;grid-template-columns:minmax(260px,1.5fr) 1fr auto;gap:10px;align-items:end}.preview-form label{display:grid;gap:6px}.preview-result{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-top:16px}.preview-result article{padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.preview-result span{display:block;color:var(--muted);font-size:12px}.preview-result strong{display:block;margin-top:5px}.preview-result p{grid-column:1/-1;color:var(--muted)}@media(max-width:1000px){.metric-grid{grid-template-columns:repeat(2,1fr)}.preview-result{grid-template-columns:repeat(3,1fr)}}@media(max-width:640px){.metric-grid,.preview-result,.preview-form,.health-summary-card{grid-template-columns:1fr}.health-badge{justify-self:start}}</style>
''')

# 组件级 v-model 不再修改 const reactive 绑定。
path = "frontend/src/views/admin/AdminGovernanceView.vue"
content = read(path)
content = content.replace("import { computed, onMounted, reactive, ref, watchEffect } from 'vue'", "import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'", 1)
content = content.replace(
    "function resetAudit(){Object.assign(auditFilters,{occurredFrom:'',occurredTo:'',operatorId:'',operatorRole:'',module:'',actionType:'',targetType:'',targetId:'',success:'',errorCode:'',requestId:'',networkAddress:'',keyword:''})}\n",
    "function updateAuditFilters(value:typeof auditFilters){Object.assign(auditFilters,value)}\nfunction resetAudit(){Object.assign(auditFilters,{occurredFrom:'',occurredTo:'',operatorId:'',operatorRole:'',module:'',actionType:'',targetType:'',targetId:'',success:'',errorCode:'',requestId:'',networkAddress:'',keyword:''})}\n",
    1,
)
content = content.replace(
    "async function loadTemplates(){",
    "function updateTemplateDraft(value:typeof templateDraft){Object.assign(templateDraft,value)}\nfunction updateRecipientCriteria(value:typeof recipientCriteria){Object.assign(recipientCriteria,value)}\nasync function loadTemplates(){",
    1,
)
content = content.replace(
    "function resetAnalytics(){Object.assign(analyticsFilters,{academicYear:'',batchId:'',majorId:'',gradeYear:'',degreeLevel:'',studentCategory:'',campusId:'',buildingId:'',roomType:''})}\n",
    "function updateAnalyticsFilters(value:typeof analyticsFilters){Object.assign(analyticsFilters,value)}\nfunction resetAnalytics(){Object.assign(analyticsFilters,{academicYear:'',batchId:'',majorId:'',gradeYear:'',degreeLevel:'',studentCategory:'',campusId:'',buildingId:'',roomType:''})}\n",
    1,
)
content = content.replace('v-model="auditFilters"', ':model-value="auditFilters" @update:model-value="updateAuditFilters"', 1)
content = content.replace('v-model="templateDraft"', ':model-value="templateDraft" @update:model-value="updateTemplateDraft"', 1)
content = content.replace('v-model="recipientCriteria"', ':model-value="recipientCriteria" @update:model-value="updateRecipientCriteria"', 1)
content = content.replace('v-model="analyticsFilters"', ':model-value="analyticsFilters" @update:model-value="updateAnalyticsFilters"', 1)
# 有排队或生成中任务时自动刷新。
content = content.replace("const retentionConfirm = ref(false)\n", "const retentionConfirm = ref(false)\nlet exportPollTimer:number|undefined\n", 1)
content = content.replace(
    "async function loadExports(){const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/exports');exportTasks.value=(response.data.data??[]) as DataObject[]}",
    "async function loadExports(){const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/exports');exportTasks.value=(response.data.data??[]) as DataObject[];syncExportPolling()}\nfunction syncExportPolling(){const active=exportTasks.value.some(task=>['QUEUED','RUNNING'].includes(String(task.task_status)));if(active&&!exportPollTimer)exportPollTimer=window.setInterval(()=>void loadExports(),2000);else if(!active&&exportPollTimer){window.clearInterval(exportPollTimer);exportPollTimer=undefined}}\nonBeforeUnmount(()=>{if(exportPollTimer)window.clearInterval(exportPollTimer)})",
    1,
)
write(path, content)

# 导出面板提供下载动作。
write("frontend/src/components/admin/ExportTaskPanel.vue", '''<script setup lang="ts">
import type { DataObject } from '../../api/types'

defineProps<{ tasks: DataObject[]; busy?: boolean }>()
const emit = defineEmits<{ refresh: []; cancel: [taskId: number] }>()

function statusText(value: unknown) {
  return ({ QUEUED: '排队中', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[String(value)] ?? String(value)
}
function taskTypeText(value:unknown){return({AUDIT_EXPORT:'审计记录导出',CUSTOM_REPORT:'自定义报表导出'}as Record<string,string>)[String(value)]??String(value)}
function download(task:DataObject){
  const token=encodeURIComponent(String(task.downloadToken??''))
  const anchor=document.createElement('a')
  anchor.href=`/api/v1/admin/governance/exports/${Number(task.id)}/download?token=${token}`
  anchor.download=String(task.file_name??'export.csv')
  document.body.appendChild(anchor);anchor.click();anchor.remove()
}
</script>

<template>
  <section class="export-task-panel">
    <header><div><strong>异步导出任务</strong><span>大结果不会阻塞在线选寝，完成后在有效期内下载。</span></div><button class="button ghost small" type="button" :disabled="busy" @click="emit('refresh')">刷新</button></header>
    <div class="task-list">
      <article v-for="task in tasks" :key="String(task.id)">
        <div><strong>{{ taskTypeText(task.task_type) }}</strong><span>{{ statusText(task.task_status) }} · {{ task.progress ?? 0 }}%</span></div>
        <progress :value="Number(task.progress ?? 0)" max="100" />
        <small v-if="task.error_message" class="error-text">{{ task.error_message }}</small>
        <small v-else>下载有效期：{{ task.expires_at ?? '任务完成后24小时' }}</small>
        <button v-if="task.task_status === 'QUEUED'" class="button ghost small" type="button" @click="emit('cancel', Number(task.id))">取消</button>
        <button v-else-if="task.task_status === 'SUCCEEDED'" class="button primary small" type="button" @click="download(task)">下载文件</button>
      </article>
      <p v-if="tasks.length === 0" class="empty-state compact">暂无导出任务。</p>
    </div>
  </section>
</template>

<style scoped>
.export-task-panel{display:grid;gap:12px}.export-task-panel>header{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.export-task-panel>header div{display:grid;gap:4px}.export-task-panel>header span,.task-list span,.task-list small{color:var(--text-muted);font-size:12px}.task-list{display:grid;gap:10px}.task-list article{display:grid;grid-template-columns:minmax(160px,1fr) minmax(150px,2fr) minmax(180px,1fr) auto;align-items:center;gap:12px;padding:12px;border:1px solid var(--border);border-radius:12px}.task-list article>div{display:grid;gap:3px}.task-list progress{width:100%}.error-text{color:#b4232f!important}@media(max-width:720px){.task-list article{grid-template-columns:1fr}.export-task-panel>header{flex-direction:column}}
</style>
''')

# 首页业务操作中文映射全面化。
path = "frontend/src/views/admin/AdminDashboardView.vue"
content = read(path)
start = content.index("function auditActionText")
end = content.index("function batchStatus", start)
replacement = '''const auditActionLabels:Record<string,string>={
  STUDENT_CREATE:'录入学生',STUDENT_UPDATE:'修改学生资料',STUDENT_PHONE_UPDATE:'修改学生手机号',STUDENT_PASSWORD_RESET:'重置学生密码',STUDENT_STATE_RESET:'完全重置学生状态',
  MAJOR_CREATE:'新增专业',MAJOR_UPDATE:'修改专业',BUILDING_CREATE:'新增宿舍楼',BUILDING_UPDATE:'修改宿舍楼',ROOM_CREATE:'新增房间',ROOM_UPDATE:'修改房间属性',ROOM_LAYOUT_UPDATE:'修改房间床位布局',BED_UPDATE:'修改床位状态',
  BATCH_CREATE:'创建选寝批次',BATCH_COPY:'复制选寝批次',BATCH_PUBLISH:'发布选寝批次',BATCH_OPEN:'开放选寝批次',BATCH_PAUSE:'暂停选寝批次',BATCH_CLOSE:'关闭选寝批次',BATCH_FINISH:'完成选寝批次',BATCH_CANCEL:'取消选寝批次',
  RESIDENCY_ASSIGN:'分配寝室床位',RESIDENCY_ADJUST:'调整寝室床位',RESIDENCY_BED_CONFIRM:'确认实际床位',RESIDENCY_END:'办理退宿',
  BED_CONFIRMATION_SUBMIT:'提交实际床位申报',BED_CONFIRMATION_CANCEL:'取消实际床位申报',BED_CONFIRMATION_ROOM_APPROVE:'通过寝室床位核查',BED_CONFIRMATION_REJECT:'驳回实际床位申报',
  ROOM_CHANGE_REQUEST:'提交换寝申请',ROOM_CHANGE_APPROVE:'批准换寝申请',ROOM_CHANGE_REJECT:'驳回换寝申请',ROOM_CHANGE_CANCEL:'取消换寝申请',ROOM_CHANGE_EXECUTE:'执行换寝',
  ROOM_EXCHANGE_REQUEST:'发起寝室交换',ROOM_EXCHANGE_APPROVE:'批准寝室交换',ROOM_EXCHANGE_REJECT:'驳回寝室交换',ROOM_EXCHANGE_CANCEL:'取消寝室交换',ROOM_EXCHANGE_EXECUTE:'执行寝室交换',
  WAITLIST_REQUEST:'提交候补申请',WAITLIST_CANCEL:'取消候补申请',WAITLIST_OFFER:'发放候补名额',WAITLIST_ASSIGN:'完成候补补位',
  TEAM_CREATE:'创建选寝队伍',TEAM_INVITE:'邀请队员',TEAM_INVITATION_RESPOND:'处理组队邀请',TEAM_MEMBER_REMOVE:'移除队员',TEAM_DISSOLVE:'解散队伍',
  SELECTION_POLICY_UPDATE:'修改选寝策略',MATCHING_SCHEME_CREATE:'创建匹配方案',MATCHING_SCHEME_REVISION:'创建匹配方案修订',SYSTEM_SETTING_UPDATE:'修改系统设置',
  IMPORT_CREATE:'创建导入任务',IMPORT_COMMIT:'提交批量导入',AUDIT_EXPORT:'导出审计记录',REPORT_EXPORT:'导出自定义报表',REPORT_TEMPLATE_SAVE:'保存报表模板',NOTIFICATION_SEND:'发送站内通知',NOTIFICATION_TEMPLATE_UPDATE:'修改通知模板',RETENTION_PREFLIGHT:'执行数据保留预检'
}
const auditResourceLabels:Record<string,string>={STUDENT:'学生',MAJOR:'专业',DORMITORY_BUILDING:'宿舍楼',BUILDING:'宿舍楼',ROOM:'房间',BED:'床位',ROOM_LAYOUT:'房间布局',SELECTION_BATCH:'选寝批次',BATCH:'选寝批次',ROOM_ASSIGNMENT:'在住记录',RESIDENCY:'在住记录',BED_CONFIRMATION_REQUEST:'实际床位申报',ROOM_CHANGE_REQUEST:'换寝申请',ROOM_EXCHANGE_REQUEST:'寝室交换申请',WAITLIST_ENTRY:'候补申请',SELECTION_TEAM:'选寝队伍',SYSTEM_SETTING:'系统设置',MATCHING_SCHEME:'匹配方案',IMPORT_JOB:'导入任务',EXPORT_TASK:'导出任务',REPORT_TEMPLATE:'报表模板',NOTIFICATION_TASK:'通知任务'}
const auditTokenLabels:Record<string,string>={CREATE:'创建',UPDATE:'修改',DELETE:'删除',RESET:'重置',PASSWORD:'密码',STATE:'状态',PUBLISH:'发布',OPEN:'开放',PAUSE:'暂停',CLOSE:'关闭',FINISH:'完成',CANCEL:'取消',APPROVE:'批准',REJECT:'驳回',ASSIGN:'分配',ADJUST:'调整',END:'结束',SUBMIT:'提交',REQUEST:'申请',EXECUTE:'执行',EXPORT:'导出',IMPORT:'导入',SEND:'发送',SAVE:'保存',ROOM:'寝室',BED:'床位',STUDENT:'学生',TEAM:'队伍',BATCH:'批次',POLICY:'策略',LAYOUT:'布局',CONFIRMATION:'核查',WAITLIST:'候补',NOTIFICATION:'通知',REPORT:'报表',AUDIT:'审计'}
function auditActionText(value:unknown){const key=String(value??'');if(!key)return'完成业务操作';return auditActionLabels[key]??key.split('_').map(part=>auditTokenLabels[part]??part).join('')}
function auditResourceText(value:unknown){const key=String(value??'业务对象');return auditResourceLabels[key]??key.split('_').map(part=>auditTokenLabels[part]??part).join('')}
function auditTargetText(log:DataObject){const type=auditResourceText(log.resource_type??log.target_type);const id=log.resource_id??log.target_id;return id==null||id===''?type:`${type} #${id}`}
function auditResultText(value:unknown){return String(value)==='SUCCESS'?'成功':String(value)==='FAILED'?'失败':String(value)==='REJECTED'?'已拒绝':String(value??'已记录')}
function formatAuditTime(value:unknown){const date=new Date(String(value??''));return Number.isNaN(date.getTime())?'时间未记录':date.toLocaleString()}

'''
content = content[:start] + replacement + content[end:]
write(path, content)

# 下载接口。
path = "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminGovernanceController.java"
content = read(path)
content = content.replace("import org.springframework.http.HttpStatus;\n", "import org.springframework.core.io.FileSystemResource;\nimport org.springframework.core.io.Resource;\nimport org.springframework.http.HttpHeaders;\nimport org.springframework.http.HttpStatus;\nimport org.springframework.http.MediaType;\n", 1)
content = content.replace("import java.time.LocalDateTime;\n", "import java.net.URLEncoder;\nimport java.nio.charset.StandardCharsets;\nimport java.time.LocalDateTime;\n", 1)
anchor = '''    @PostMapping("/exports/{taskId}/cancel")
    public ResponseEntity<ObjectSuccessResponse> cancelExport(@PathVariable long taskId) {
        requireAny(FeatureCodes.P2_AUDIT_EXPORT, FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        exportTaskService.cancel(taskId, SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(exportTaskService.get(taskId)));
    }
'''
addition = anchor + '''
    @GetMapping("/exports/{taskId}/download")
    public ResponseEntity<Resource> downloadExport(
            @PathVariable long taskId,
            @RequestParam String token) {
        SecurityUsers.requireAdmin();
        requireAny(FeatureCodes.P2_AUDIT_EXPORT, FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        ExportTaskService.ExportDownload download = exportTaskService.download(taskId, token);
        String encoded = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(download.path()));
    }
'''
if anchor not in content:
    raise RuntimeError("AdminGovernanceController download anchor missing")
content = content.replace(anchor, addition, 1)
write(path, content)

print("admin runtime governance fixes applied")
