<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'

const { hasFeature } = useFeatureAccess()
const batches = ref<DataObject[]>([])
const ruleTemplates = ref<DataObject[]>([])
const error = ref('')
const message = ref('')
const allocationPreview = ref<DataObject | null>(null)
const allocationBatchId = ref<number | null>(null)
const roomPreflight = ref<DataObject | null>(null)
const preflightBatch = ref<DataObject | null>(null)
const copyDialog = ref(false)
const copying = ref(false)
const copySource = ref<DataObject | null>(null)
const scopeDialog = ref(false)
const scopeLoading = ref(false)
const scopeSaving = ref(false)
const scopeBatch = ref<DataObject | null>(null)
const scopeData = ref<DataObject | null>(null)
const selectedStudentIds = ref<number[]>([])
const selectedRoomIds = ref<number[]>([])
const studentFilter = ref('')
const roomFilter = ref('')
const publishAfterScope = ref(false)

const form = reactive({
  batchCode: '', batchName: '', startAt: '', endAt: '', ruleTemplateId: 0,
  selectionMode: 'ROOM' as 'ROOM' | 'BED', separateStudentCategories: false,
})
const copyForm = reactive({ batchCode: '', batchName: '', startAt: '', endAt: '', reason: '' })

const bedModeAuthorized = computed(() => hasFeature('P2_BED_SELECTION_MODE'))
const selectedRuleTemplate = computed(() => ruleTemplates.value.find((item) => Number(item.id) === Number(form.ruleTemplateId)) ?? null)
const ruleTemplateSummary = computed(() => {
  const item = selectedRuleTemplate.value
  if (!item) return '请选择规则模板'
  return `${item.allow_team ? `允许${item.team_min_size}—${item.team_max_size}人组队` : '不允许组队'}；临时占用${item.hold_duration_seconds}秒；${item.allow_student_random ? '允许' : '不允许'}随机推荐。`
})
const preflightRooms = computed(() => (roomPreflight.value?.rooms ?? []) as DataObject[])
const preflightBlockers = computed(() => (roomPreflight.value?.blockers ?? []) as DataObject[])
const allocationSummary = computed(() => (allocationPreview.value?.summary ?? {}) as DataObject)
const unassignedStudents = computed(() => (allocationPreview.value?.unassigned ?? []) as DataObject[])
const scopeStudents = computed(() => (scopeData.value?.students ?? []) as DataObject[])
const scopeRooms = computed(() => (scopeData.value?.rooms ?? []) as DataObject[])
const filteredStudents = computed(() => {
  const keyword = studentFilter.value.trim().toLowerCase()
  if (!keyword) return scopeStudents.value
  return scopeStudents.value.filter((student) => [
    student.student_number,
    student.student_name,
    student.major_name,
  ].some((value) => String(value ?? '').toLowerCase().includes(keyword)))
})
const filteredRooms = computed(() => {
  const keyword = roomFilter.value.trim().toLowerCase()
  if (!keyword) return scopeRooms.value
  return scopeRooms.value.filter((room) => [
    room.building_code,
    room.building_name,
    room.room_number,
    room.floor_number,
  ].some((value) => String(value ?? '').toLowerCase().includes(keyword)))
})

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [batchResponse, templateResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/batch-rule-templates'),
    ])
    batches.value = (batchResponse.data.data ?? []) as DataObject[]
    ruleTemplates.value = ((templateResponse.data.data ?? []) as DataObject[]).filter((item) => Boolean(item.enabled))
    if (!ruleTemplates.value.some((item) => Number(item.id) === form.ruleTemplateId)) {
      const defaultTemplate = ruleTemplates.value.find((item) => Boolean(item.is_default)) ?? ruleTemplates.value[0]
      form.ruleTemplateId = Number(defaultTemplate?.id ?? 0)
    }
    if (!bedModeAuthorized.value && form.selectionMode === 'BED') form.selectionMode = 'ROOM'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次加载失败'
  }
}

async function createBatch() {
  error.value = ''; message.value = ''
  if (form.selectionMode === 'BED' && !bedModeAuthorized.value) {
    error.value = '当前服务未开放选择床位模式。'
    return
  }
  try {
    await api.post('/api/v1/admin/batches', {
      batchCode: form.batchCode,
      batchName: form.batchName,
      startAt: new Date(form.startAt).toISOString(),
      endAt: new Date(form.endAt).toISOString(),
      ruleTemplateId: form.ruleTemplateId,
      selectionMode: form.selectionMode,
      separateStudentCategories: form.separateStudentCategories,
    })
    message.value = `批次已创建为草稿，模式为${modeText(form.selectionMode)}。请配置参与学生与宿舍范围。`
    form.batchCode = ''; form.batchName = ''
    await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '批次创建失败' }
}

async function openScope(batch: DataObject, continuePublish = false) {
  scopeDialog.value = true
  scopeLoading.value = true
  scopeBatch.value = batch
  scopeData.value = null
  selectedStudentIds.value = []
  selectedRoomIds.value = []
  studentFilter.value = ''
  roomFilter.value = ''
  publishAfterScope.value = continuePublish
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/scope`)
    const data = (response.data.data ?? {}) as DataObject
    scopeData.value = data
    selectedStudentIds.value = ((data.students ?? []) as DataObject[])
      .filter((student) => Boolean(student.selected))
      .map((student) => Number(student.id))
    selectedRoomIds.value = ((data.rooms ?? []) as DataObject[])
      .filter((room) => Boolean(room.selected) && Boolean(room.selectable))
      .map((room) => Number(room.id))
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次范围加载失败'
    scopeDialog.value = false
  } finally {
    scopeLoading.value = false
  }
}

function closeScope() {
  if (scopeSaving.value) return
  scopeDialog.value = false
  scopeBatch.value = null
  scopeData.value = null
  publishAfterScope.value = false
}

function toggleStudent(id: number) {
  selectedStudentIds.value = toggleId(selectedStudentIds.value, id)
}

function toggleRoom(id: number) {
  selectedRoomIds.value = toggleId(selectedRoomIds.value, id)
}

function selectAllStudents() {
  selectedStudentIds.value = filteredStudents.value.map((student) => Number(student.id))
}

function selectAllRooms() {
  selectedRoomIds.value = filteredRooms.value
    .filter((room) => Boolean(room.selectable))
    .map((room) => Number(room.id))
}

function toggleId(values: number[], id: number) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
}

async function saveScope() {
  if (!scopeBatch.value) return
  if (publishAfterScope.value && selectedStudentIds.value.length === 0) {
    error.value = '发布前至少选择一名参与学生。'
    return
  }
  if (publishAfterScope.value && selectedRoomIds.value.length === 0) {
    error.value = '发布前至少选择一间可选宿舍。'
    return
  }
  const batch = scopeBatch.value
  const continuePublish = publishAfterScope.value
  scopeSaving.value = true
  error.value = ''
  try {
    await api.put(`/api/v1/admin/batches/${Number(batch.id)}/scope`, {
      studentIds: selectedStudentIds.value,
      roomIds: selectedRoomIds.value,
    })
    message.value = `已保存${selectedStudentIds.value.length}名学生和${selectedRoomIds.value.length}间宿舍。`
    closeScope()
    await load()
    if (continuePublish) await publishBatch(batch)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次范围保存失败'
  } finally {
    scopeSaving.value = false
  }
}

async function preflight(batch: DataObject) {
  error.value = ''; preflightBatch.value = batch
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/room-preflight`)
    roomPreflight.value = (response.data.data ?? {}) as DataObject
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '发布预检失败' }
}

async function publishBatch(batch: DataObject) {
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/room-preflight`)
    roomPreflight.value = (response.data.data ?? {}) as DataObject
    preflightBatch.value = batch
    if (!Boolean(roomPreflight.value.publishable)) {
      error.value = Number(roomPreflight.value.roomCount ?? 0) === 0
        ? '当前批次尚未选择宿舍，请重新配置参与范围。'
        : '发布前检查未通过，请处理阻断宿舍后重试。'
      return
    }
    await run(async () => {
      await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/PUBLISHED`)
      message.value = '批次已发布。'
      roomPreflight.value = null
      preflightBatch.value = null
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次发布失败'
  }
}

async function changeStatus(batch: DataObject, target: string) {
  if (target === 'PUBLISHED') {
    await openScope(batch, true)
    return
  }
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/${target}`)
    message.value = `批次已切换为“${statusText(target)}”。`
    roomPreflight.value = null; preflightBatch.value = null
  })
}

function openCopy(batch: DataObject) {
  copySource.value = batch; copyDialog.value = true
  Object.assign(copyForm, { batchCode: '', batchName: '', startAt: '', endAt: '', reason: '' })
}
function closeCopy() { if (!copying.value) { copyDialog.value = false; copySource.value = null } }
async function copyBatch() {
  if (!copySource.value) return
  copying.value = true; error.value = ''
  try {
    await api.post(`/api/v1/admin/batches/${Number(copySource.value.id)}/copy`, {
      batchCode: copyForm.batchCode, batchName: copyForm.batchName,
      startAt: new Date(copyForm.startAt).toISOString(), endAt: new Date(copyForm.endAt).toISOString(),
      reason: copyForm.reason,
    })
    message.value = `批次已复制为草稿，并保留${modeText(copySource.value.selection_mode)}与学生类别隔离设置。`
    closeCopy(); await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '批次复制失败' }
  finally { copying.value = false }
}

async function previewAllocation(batch: DataObject) {
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/allocation/preview`, { params: { randomSeed: 20260801 } })
    allocationPreview.value = (response.data.data ?? {}) as DataObject; allocationBatchId.value = Number(batch.id)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '分配预演失败' }
}
async function commitAllocation() {
  if (!allocationBatchId.value) return
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/batches/${allocationBatchId.value}/allocation/commit`, { randomSeed: 20260801, idempotencyKey: crypto.randomUUID() })
    const result = (response.data.data ?? {}) as DataObject
    allocationPreview.value = { summary: result.summary ?? {}, unassigned: result.unassigned ?? [] }
    allocationBatchId.value = null
    message.value = Number(((result.summary ?? {}) as DataObject).unassignedCount ?? 0) > 0 ? '统一分配已执行，仍有未分配学生需要处理。' : '统一分配已完成。'
    await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '统一分配执行失败' }
}
async function download(batch: DataObject) {
  try {
    const response = await api.get(`/api/v1/admin/batches/${Number(batch.id)}/assignments.csv`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data); const link = document.createElement('a')
    link.href = url; link.download = `assignments-${batch.id}.csv`; link.click(); URL.revokeObjectURL(url)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '导出失败' }
}
async function run(action: () => Promise<void>) {
  error.value = ''; message.value = ''
  try { await action(); await load() } catch (reason) { error.value = reason instanceof Error ? reason.message : '操作失败' }
}
function nextActions(status: unknown) {
  return ({ DRAFT: ['PUBLISHED','CANCELLED'], PUBLISHED: ['OPEN','CANCELLED'], OPEN: ['PAUSED','CLOSED'], PAUSED: ['OPEN','CLOSED'], CLOSED: ['ALLOCATING','FINISHED'], ALLOCATING: ['FINISHED','CLOSED'] } as Record<string,string[]>)[String(status)] ?? []
}
function modeText(value: unknown) { return String(value) === 'BED' ? '选择床位' : '选择寝室' }
function statusText(value: unknown) { return ({ DRAFT:'草稿',PUBLISHED:'已发布',OPEN:'选寝中',PAUSED:'已暂停',CLOSED:'已关闭',ALLOCATING:'分配中',FINISHED:'已完成',CANCELLED:'已取消' } as Record<string,string>)[String(value)] ?? String(value) }
function actionText(value: string) { return ({ PUBLISHED:'发布活动',OPEN:'开放选择',PAUSED:'暂停选择',CLOSED:'结束选择',ALLOCATING:'进入统一分配',FINISHED:'标记完成',CANCELLED:'取消批次' } as Record<string,string>)[value] ?? value }
function issueText(room: DataObject) { const issues=(room.issues??[]) as DataObject[]; return issues.map((i)=>String(i.message)).join('；') }
</script>

<template>
  <div class="content-column">
    <div class="page-title"><span class="eyebrow">SELECTION OPERATIONS</span><h2>选寝批次与统一分配</h2><p>创建草稿后先选择参与学生与可选宿舍，再执行发布预检。选床模式会阻止包含未确认实际床位住户的宿舍发布。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">NEW BATCH</span><h3>创建选寝批次</h3></div></div>
      <form class="batch-create-form" @submit.prevent="createBatch">
        <div class="mode-card-grid">
          <button type="button" class="mode-card" :class="{ selected: form.selectionMode==='ROOM' }" @click="form.selectionMode='ROOM'"><strong>选择寝室</strong><span>学生只确定寝室归属，具体床位由入住成员自行商议。</span><small>基础模式</small></button>
          <button type="button" class="mode-card" :class="{ selected: form.selectionMode==='BED', disabled: !bedModeAuthorized }" :disabled="!bedModeAuthorized" @click="form.selectionMode='BED'"><strong>选择床位</strong><span>学生进入寝室后选择系统确认真实空闲的具体床位。</span><small>{{ bedModeAuthorized ? '已授权' : '当前服务未开通' }}</small></button>
        </div>
        <label class="separation-switch"><button type="button" role="switch" :aria-checked="form.separateStudentCategories" :class="{ checked: form.separateStudentCategories }" @click="form.separateStudentCategories=!form.separateStudentCategories"><span /></button><div><strong>国内生与国际生分开选寝</strong><p>开启后仅允许国内生使用国内生专用宿舍、国际生使用国际生专用宿舍，混住宿舍不进入本批次。</p></div></label>
        <div class="form-grid three-column">
          <label><span>批次编号</span><input v-model.trim="form.batchCode" class="input" required maxlength="32" /></label>
          <label><span>批次名称</span><input v-model.trim="form.batchName" class="input" required maxlength="128" /></label>
          <label><span>规则模板</span><select v-model.number="form.ruleTemplateId" class="input" required><option :value="0" disabled>请选择</option><option v-for="item in ruleTemplates" :key="String(item.id)" :value="Number(item.id)">{{ item.rule_name }} · 修订{{ item.revision }}</option></select></label>
          <label><span>开始时间</span><input v-model="form.startAt" class="input" type="datetime-local" required /></label><label><span>结束时间</span><input v-model="form.endAt" class="input" type="datetime-local" required /></label>
          <div class="rule-summary"><strong>规则摘要</strong><span>{{ ruleTemplateSummary }}</span></div>
        </div>
        <button class="button primary">创建草稿批次</button>
      </form>
    </section>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">BATCH LIST</span><h3>批次管理</h3></div></div>
      <div class="batch-list">
        <article v-for="batch in batches" :key="String(batch.id)" class="batch-card">
          <header><div><div class="badge-row"><span class="status-chip compact">{{ statusText(batch.batch_status) }}</span><span class="status-chip compact mode">{{ modeText(batch.selection_mode) }}</span><span v-if="batch.separate_student_categories" class="status-chip compact category">国内/国际隔离</span></div><h3>{{ batch.batch_name }}</h3><p>{{ batch.batch_code }}</p></div><div class="batch-counts"><strong>{{ batch.eligible_count ?? 0 }}</strong><span>已选参与学生</span></div></header>
          <div class="batch-facts"><span>寝室结果 {{ batch.room_assigned_count ?? 0 }}</span><span>床位结果 {{ batch.bed_assigned_count ?? batch.assigned_count ?? 0 }}</span><span>活动锁定宿舍 {{ batch.locked_room_count ?? 0 }}</span><span v-if="Number(batch.unconfirmed_bed_resident_count ?? 0)>0" class="warn">{{ batch.unconfirmed_bed_resident_count }}人待确认床位</span></div>
          <div class="button-row wrap"><button v-if="batch.batch_status==='DRAFT'" class="button secondary small" @click="openScope(batch)">配置参与范围</button><button class="button ghost small" @click="preflight(batch)">宿舍预检</button><button class="button ghost small" @click="openCopy(batch)">复制配置</button><button v-for="target in nextActions(batch.batch_status)" :key="target" class="button small" :class="target==='CANCELLED'?'danger':'primary'" @click="changeStatus(batch,target)">{{ actionText(target) }}</button><button v-if="['CLOSED','ALLOCATING'].includes(String(batch.batch_status))" class="button secondary small" @click="previewAllocation(batch)">统一分配预演</button><button class="button ghost small" @click="download(batch)">导出结果</button></div>
        </article>
      </div>
    </section>

    <div v-if="scopeDialog" class="modal-overlay" @click.self="closeScope">
      <section class="modal-card scope-dialog">
        <header class="section-head split-title"><div><span class="eyebrow">BATCH SCOPE</span><h3>{{ scopeBatch?.batch_name }} · 参与范围</h3><p>选择本批次允许参与的学生和宿舍，保存后才可发布。</p></div><button class="button ghost small" :disabled="scopeSaving" @click="closeScope">关闭</button></header>
        <p v-if="scopeLoading" class="empty-state">正在加载学生与宿舍…</p>
        <template v-else>
          <div class="scope-summary"><article><span>已选学生</span><strong>{{ selectedStudentIds.length }}</strong></article><article><span>已选宿舍</span><strong>{{ selectedRoomIds.length }}</strong></article></div>
          <div class="scope-grid">
            <section class="scope-column"><header><div><strong>参与学生</strong><small>按学号、姓名或专业筛选</small></div><div class="button-row"><button class="button ghost small" @click="selectAllStudents">全选当前结果</button><button class="button ghost small" @click="selectedStudentIds=[]">清空</button></div></header><input v-model.trim="studentFilter" class="input" placeholder="搜索学号、姓名或专业" /><div class="scope-options"><label v-for="student in filteredStudents" :key="String(student.id)" class="scope-option"><input type="checkbox" :checked="selectedStudentIds.includes(Number(student.id))" @change="toggleStudent(Number(student.id))" /><div><strong>{{ student.student_number }} · {{ student.student_name }}</strong><span>{{ student.major_name }} · {{ student.gender==='M'?'男':'女' }} · {{ student.student_category==='INTERNATIONAL'?'国际生':'国内生' }}</span></div></label></div></section>
            <section class="scope-column"><header><div><strong>可选宿舍</strong><small>停用或维护中的宿舍不可选择</small></div><div class="button-row"><button class="button ghost small" @click="selectAllRooms">全选当前可用</button><button class="button ghost small" @click="selectedRoomIds=[]">清空</button></div></header><input v-model.trim="roomFilter" class="input" placeholder="搜索楼栋、楼层或房间号" /><div class="scope-options"><label v-for="room in filteredRooms" :key="String(room.id)" class="scope-option" :class="{ disabled: !room.selectable }"><input type="checkbox" :disabled="!room.selectable" :checked="selectedRoomIds.includes(Number(room.id))" @change="toggleRoom(Number(room.id))" /><div><strong>{{ room.building_name }} {{ room.room_number }}</strong><span>{{ room.floor_number }}层 · 容量{{ room.capacity }} · {{ room.gender_restriction==='M'?'男寝':'女寝' }} · {{ room.operational_status }}</span></div></label></div></section>
          </div>
          <div class="scope-footer"><span v-if="publishAfterScope">保存后将自动执行宿舍预检并发布。</span><span v-else>草稿阶段可以反复调整范围。</span><button class="button primary" :disabled="scopeSaving" @click="saveScope">{{ scopeSaving?'保存中…':publishAfterScope?'保存范围并继续发布':'保存参与范围' }}</button></div>
        </template>
      </section>
    </div>

    <div v-if="preflightBatch && roomPreflight" class="modal-overlay" @click.self="preflightBatch=null;roomPreflight=null">
      <section class="modal-card preflight-dialog"><header class="section-head split-title"><div><span class="eyebrow">ROOM PREFLIGHT</span><h3>{{ preflightBatch.batch_name }} · 宿舍发布预检</h3><p>可用容量{{ roomPreflight.availableCapacity }}，涉及{{ roomPreflight.roomCount }}间宿舍。</p></div><button class="button ghost small" @click="preflightBatch=null;roomPreflight=null">关闭</button></header>
        <div class="preflight-summary" :class="{ pass: roomPreflight.publishable }"><strong>{{ roomPreflight.publishable ? '检查通过，可以发布' : Number(roomPreflight.roomCount??0)===0 ? '尚未选择任何宿舍' : `存在${preflightBlockers.length}间阻断宿舍` }}</strong><span>{{ Number(roomPreflight.roomCount??0)===0 ? '返回参与范围选择至少一间可用宿舍。' : '同一宿舍活动互斥；选床模式要求现实床位映射完整。' }}</span></div>
        <div v-if="Number(roomPreflight.roomCount??0)===0 && preflightBatch.batch_status==='DRAFT'" class="button-row preflight-action"><button class="button primary" @click="preflightBatch=null;roomPreflight=null;openScope(preflightBatch as DataObject)">配置参与范围</button></div>
        <div class="preflight-room-grid"><article v-for="room in preflightRooms" :key="String(room.id)" :class="{ blocker: ((room.issues??[]) as unknown[]).length>0 }"><strong>{{ room.building_name }} {{ room.room_number }}</strong><span>在住{{ room.activeResidents }} · 剩余{{ room.remainingCapacity }}</span><small v-if="((room.issues??[]) as unknown[]).length">{{ issueText(room) }}</small><small v-else>符合发布条件</small></article></div>
      </section>
    </div>

    <div v-if="copyDialog" class="modal-overlay" @click.self="closeCopy"><section class="modal-card copy-dialog"><header class="section-head split-title"><div><span class="eyebrow">COPY BATCH</span><h3>复制“{{ copySource?.batch_name }}”</h3><p>自动保留选择模式、类别隔离、规则模板和宿舍范围。</p></div><button class="button ghost small" @click="closeCopy">关闭</button></header><form class="form-grid two-column" @submit.prevent="copyBatch"><label><span>新批次编号</span><input v-model.trim="copyForm.batchCode" class="input" required /></label><label><span>新批次名称</span><input v-model.trim="copyForm.batchName" class="input" required /></label><label><span>开始时间</span><input v-model="copyForm.startAt" class="input" type="datetime-local" required /></label><label><span>结束时间</span><input v-model="copyForm.endAt" class="input" type="datetime-local" required /></label><label class="span-2"><span>复制原因</span><textarea v-model.trim="copyForm.reason" class="input" required rows="3" /></label><div class="button-row span-2"><button class="button ghost" type="button" @click="closeCopy">取消</button><button class="button primary" :disabled="copying">{{ copying?'复制中…':'创建草稿副本' }}</button></div></form></section></div>

    <div v-if="allocationPreview" class="modal-overlay" @click.self="allocationPreview=null;allocationBatchId=null"><section class="modal-card allocation-dialog"><header class="section-head split-title"><div><span class="eyebrow">ALLOCATION PREVIEW</span><h3>统一分配预演</h3></div><button class="button ghost small" @click="allocationPreview=null;allocationBatchId=null">关闭</button></header><div class="allocation-stats"><article><span>学生</span><strong>{{ allocationSummary.studentCount ?? 0 }}</strong></article><article><span>预计成功</span><strong>{{ allocationSummary.assignedCount ?? 0 }}</strong></article><article><span>未分配</span><strong>{{ allocationSummary.unassignedCount ?? 0 }}</strong></article></div><div v-if="unassignedStudents.length" class="table-wrap"><table><thead><tr><th>学号</th><th>姓名</th><th>原因</th></tr></thead><tbody><tr v-for="student in unassignedStudents" :key="String(student.studentId)"><td>{{ student.studentNumber }}</td><td>{{ student.studentName }}</td><td>{{ student.reason }}</td></tr></tbody></table></div><button v-if="allocationBatchId" class="button primary" @click="commitAllocation">确认执行统一分配</button></section></div>
  </div>
</template>

<style scoped>
.batch-create-form{display:grid;gap:18px}.mode-card-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.mode-card{display:grid;gap:8px;padding:20px;border:1px solid var(--border);border-radius:16px;background:var(--surface);text-align:left;color:inherit}.mode-card span,.mode-card small{color:var(--text-muted)}.mode-card.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}.mode-card.disabled{opacity:.55}.separation-switch{display:flex;align-items:center;gap:14px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.separation-switch>button{position:relative;width:50px;height:28px;border:0;border-radius:999px;background:#cbd5e1;flex:0 0 auto}.separation-switch>button span{position:absolute;left:3px;top:3px;width:22px;height:22px;border-radius:50%;background:white;transition:.2s}.separation-switch>button.checked{background:var(--primary)}.separation-switch>button.checked span{transform:translateX(22px)}.separation-switch p{margin:4px 0 0;color:var(--text-muted)}.rule-summary{display:grid;gap:6px;padding:12px;border-radius:12px;background:var(--surface-soft)}.batch-list{display:grid;gap:14px}.batch-card{padding:18px;border:1px solid var(--border);border-radius:16px;background:var(--surface)}.batch-card header{display:flex;justify-content:space-between;gap:16px}.batch-card h3{margin:8px 0 3px}.batch-card p{margin:0;color:var(--text-muted)}.badge-row,.batch-facts{display:flex;gap:8px;flex-wrap:wrap}.status-chip.mode{background:#eff6ff;color:#1d4ed8}.status-chip.category{background:#f5f3ff;color:#6d28d9}.batch-counts{text-align:right}.batch-counts strong{display:block;font-size:26px}.batch-counts span,.batch-facts{color:var(--text-muted);font-size:13px}.batch-facts{margin:14px 0}.batch-facts .warn{color:#b45309;font-weight:700}.scope-dialog{width:min(1180px,calc(100vw - 32px));max-height:calc(100vh - 32px);overflow:auto;padding:24px}.scope-summary{display:grid;grid-template-columns:repeat(2,minmax(0,180px));gap:12px;margin-bottom:14px}.scope-summary article{padding:14px;border-radius:12px;background:var(--surface-soft)}.scope-summary strong{display:block;font-size:24px}.scope-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.scope-column{display:grid;gap:12px;min-width:0}.scope-column>header{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.scope-column>header small{display:block;color:var(--text-muted);margin-top:4px}.scope-options{display:grid;gap:8px;max-height:440px;overflow:auto;padding-right:4px}.scope-option{display:flex;gap:10px;align-items:flex-start;padding:11px;border:1px solid var(--border);border-radius:11px;background:var(--surface)}.scope-option input{margin-top:4px}.scope-option div{display:grid;gap:3px}.scope-option span{color:var(--text-muted);font-size:12px}.scope-option.disabled{opacity:.55;background:var(--surface-soft)}.scope-footer{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-top:18px;padding-top:16px;border-top:1px solid var(--border);color:var(--text-muted)}.preflight-dialog,.allocation-dialog{width:min(980px,calc(100vw - 32px));max-height:calc(100vh - 32px);overflow:auto;padding:24px}.preflight-summary{display:grid;gap:5px;padding:15px;border-radius:13px;background:#fef2f2;color:#991b1b}.preflight-summary.pass{background:#f0fdf4;color:#166534}.preflight-action{margin-top:12px}.preflight-room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px;margin-top:14px}.preflight-room-grid article{display:grid;gap:5px;padding:14px;border:1px solid var(--border);border-radius:12px}.preflight-room-grid article.blocker{border-color:#fecaca;background:#fff7f7}.preflight-room-grid span,.preflight-room-grid small{color:var(--text-muted)}.copy-dialog{width:min(720px,calc(100vw - 32px));padding:24px}.allocation-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:14px 0}.allocation-stats article{padding:14px;background:var(--surface-soft);border-radius:12px}.allocation-stats strong{display:block;font-size:24px}@media(max-width:900px){.scope-grid{grid-template-columns:1fr}.scope-options{max-height:320px}}@media(max-width:720px){.mode-card-grid,.allocation-stats,.scope-summary{grid-template-columns:1fr}.batch-card header,.scope-footer,.scope-column>header{flex-direction:column}.batch-counts{text-align:left}}
</style>
