<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const policy = ref<DataObject>({ mode: 'DISABLED', enabled: false, requiresApproval: false })
const candidates = ref<DataObject[]>([])
const requests = ref<DataObject[]>([])
const profile = ref<DataObject>({})
const exchangePolicy = ref<DataObject>({ mode: 'DISABLED', enabled: false, requiresApproval: false })
const exchangeCandidates = ref<DataObject[]>([])
const exchangeRequests = ref<DataObject[]>([])
const target = ref<DataObject | null>(null)
const exchangeTarget = ref<DataObject | null>(null)
const submitting = ref(false)
const loading = ref(true)
const error = ref('')
const message = ref('')
const CHANGE_ROOM_ROWS_PER_PAGE = 6
const candidateKeyword = ref('')
const changeRoomPage = ref(1)
const changeRoomColumnCount = ref(3)
const exchangeStudentNumber = ref('')
const exchangeSearchStarted = ref(false)
const exchangeSearching = ref(false)
const form = reactive({ reason: '' })
const exchangeForm = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

const modeText = computed(() => ({
  DISABLED: '未开放', FREE: '自由换寝', APPROVAL_REQUIRED: '管理员审批',
} as Record<string, string>)[String(policy.value.mode)] ?? '未开放')
const exchangeModeText = computed(() => ({
  DISABLED: '未开放', ENABLED: '已开放',
  MUTUAL_CONFIRMATION: '双方确认后直接交换',
  APPROVAL_REQUIRED: '双方确认后由管理员审批',
} as Record<string, string>)[String(exchangePolicy.value.mode)] ?? '已开放')
const incomingExchanges = computed(() => exchangeRequests.value.filter((item) =>
  String(item.target_student_number) === String(profile.value.student_number)
  && String(item.request_status) === 'WAITING_TARGET',
))
const outgoingExchanges = computed(() => exchangeRequests.value.filter((item) =>
  String(item.initiator_student_number) === String(profile.value.student_number),
))
const filteredCandidates = computed(() => {
  const term = candidateKeyword.value.trim().toLowerCase()
  return candidates.value
    .filter((room) => !term || `${room.building_name ?? ''} ${room.room_number ?? ''}`.toLowerCase().includes(term))
    .sort((left, right) => {
      const buildingDifference = String(left.building_name ?? '').localeCompare(String(right.building_name ?? ''), 'zh-CN')
      if (buildingDifference !== 0) return buildingDifference
      return String(left.room_number ?? '').localeCompare(String(right.room_number ?? ''), 'zh-CN', { numeric: true })
    })
})
const changeRoomPageSize = computed(() => CHANGE_ROOM_ROWS_PER_PAGE * changeRoomColumnCount.value)
const totalChangeRoomPages = computed(() => Math.max(1, Math.ceil(filteredCandidates.value.length / changeRoomPageSize.value)))
const pagedCandidates = computed(() => {
  const start = (changeRoomPage.value - 1) * changeRoomPageSize.value
  return filteredCandidates.value.slice(start, start + changeRoomPageSize.value)
})
const currentCandidateStart = computed(() => filteredCandidates.value.length === 0 ? 0 : (changeRoomPage.value - 1) * changeRoomPageSize.value + 1)
const currentCandidateEnd = computed(() => Math.min(changeRoomPage.value * changeRoomPageSize.value, filteredCandidates.value.length))

function updateChangeRoomColumnCount() {
  const width = window.innerWidth
  changeRoomColumnCount.value = width >= 1180 ? 3 : width >= 700 ? 2 : 1
  changeRoomPage.value = Math.min(changeRoomPage.value, totalChangeRoomPages.value)
}

watch(candidateKeyword, () => {
  changeRoomPage.value = 1
})
watch([filteredCandidates, changeRoomPageSize], () => {
  changeRoomPage.value = Math.min(changeRoomPage.value, totalChangeRoomPages.value)
})
onMounted(() => {
  updateChangeRoomColumnCount()
  window.addEventListener('resize', updateChangeRoomColumnCount)
  void load()
})
onBeforeUnmount(() => window.removeEventListener('resize', updateChangeRoomColumnCount))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, policyResponse, historyResponse, exchangeHistoryResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),
      api.get<ObjectSuccessResponse>('/api/v1/student/room-change/policy'),
      api.get<ListSuccessResponse>('/api/v1/student/room-change/requests'),
      api.get<ListSuccessResponse>('/api/v1/student/room-exchanges'),
    ])
    profile.value = (profileResponse.data.data ?? {}) as DataObject
    policy.value = (policyResponse.data.data ?? {}) as DataObject
    requests.value = (historyResponse.data.data ?? []) as DataObject[]
    exchangeRequests.value = (exchangeHistoryResponse.data.data ?? []) as DataObject[]

    if (Boolean(policy.value.enabled)) {
      const candidateResponse = await api.get<ListSuccessResponse>('/api/v1/student/room-change/candidates')
      candidates.value = (candidateResponse.data.data ?? []) as DataObject[]
    } else {
      candidates.value = []
    }

    exchangeCandidates.value = []
    exchangeSearchStarted.value = false
    const latestMode = String(exchangeRequests.value[0]?.policy_mode ?? 'ENABLED')
    exchangePolicy.value = {
      mode: latestMode,
      enabled: latestMode !== 'DISABLED',
      requiresApproval: latestMode === 'APPROVAL_REQUIRED',
    }
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

async function searchExchangeCandidates() {
  const studentNumber = exchangeStudentNumber.value.trim()
  exchangeSearchStarted.value = true
  exchangeCandidates.value = []
  if (!studentNumber) return
  exchangeSearching.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/student/room-exchanges/candidates', {
      params: { studentNumber },
    })
    exchangeCandidates.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    exchangeSearching.value = false
  }
}

function clearExchangeSearch() {
  exchangeStudentNumber.value = ''
  exchangeCandidates.value = []
  exchangeSearchStarted.value = false
}

function requestChange(room: DataObject) {
  target.value = room
  form.reason = ''
  error.value = ''
  message.value = ''
}
function closeDialog() { if (!submitting.value) target.value = null }

async function submit() {
  if (!target.value || submitting.value) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/student/room-change/requests', {
      targetRoomId: Number(target.value.id), reason: form.reason.trim(),
    })
    const result = (response.data.data ?? {}) as DataObject
    message.value = String(result.request_status) === 'EXECUTED'
      ? '换寝已执行，当前住宿信息已更新。'
      : '换寝申请已提交，请等待管理员审批。'
    target.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    submitting.value = false
  }
}

async function cancelRequest(item: DataObject) {
  try {
    await api.post(`/api/v1/student/room-change/requests/${item.id}/cancel`, {
      reason: '学生主动取消待审核换寝申请',
    })
    message.value = '换寝申请已取消。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  }
}

function openExchange(candidate: DataObject) {
  exchangeTarget.value = candidate
  exchangeForm.reason = ''
  error.value = ''
  message.value = ''
}
function closeExchangeDialog() { if (!submitting.value) exchangeTarget.value = null }

async function submitExchange() {
  if (!exchangeTarget.value || submitting.value) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    await api.post('/api/v1/student/room-exchanges', {
      targetStudentId: Number(exchangeTarget.value.target_student_id),
      reason: exchangeForm.reason.trim(),
    })
    exchangeTarget.value = null
    message.value = '交换邀请已发送，需对方学生确认后继续。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    submitting.value = false
  }
}

async function respondExchange(item: DataObject, accepted: boolean) {
  const action = accepted ? '接受' : '拒绝'
  const reason = window.prompt(`请填写${action}本次寝室交换的说明`, accepted ? '双方已就交换达成一致' : '暂不同意本次交换')
  if (!reason?.trim()) return
  submitting.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/student/room-exchanges/${item.id}/respond`, {
      accepted, reason: reason.trim(),
    })
    const result = (response.data.data ?? {}) as DataObject
    if (!accepted) message.value = '已拒绝本次交换邀请。'
    else if (String(result.request_status) === 'EXECUTED') message.value = '双方寝室床位已完成交换。'
    else message.value = '已接受交换，申请已转交管理员审批。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    submitting.value = false
  }
}

async function cancelExchange(item: DataObject) {
  const reason = window.prompt('请填写取消交换的原因', '双方计划有变，取消本次交换')
  if (!reason?.trim()) return
  try {
    await api.post(`/api/v1/student/room-exchanges/${item.id}/cancel`, { reason: reason.trim() })
    message.value = '寝室交换申请已取消。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  }
}

function statusText(value: unknown) {
  return ({
    PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', EXECUTED: '已完成',
    CANCELLED: '已取消', FAILED: '执行失败', WAITING_TARGET: '等待对方确认',
    PENDING_ADMIN: '等待管理员审批',
  } as Record<string, string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整', 'ROOM CHANGE') }}</span><h2>换寝与寝室交换</h2><p>可以申请迁入空余寝室，也可以在双方达成意向后交换现有寝室和床位。</p></div><button class="button secondary" @click="load">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel policy-summary-grid"><article><span>单人换寝策略</span><strong>{{ modeText }}</strong><small>迁入空余寝室或床位</small></article><article><span>双方交换策略</span><strong>{{ exchangeModeText }}</strong><small>互换双方当前寝室与床位</small></article></section>

    <section v-if="policy.enabled" class="panel change-room-section">
      <div class="section-head"><div><span class="eyebrow">空余资源换寝</span><h3>符合条件的可用寝室</h3><p>先对全部可迁入寝室按楼栋或房间号查找，再分页显示结果。</p></div></div>
      <div class="change-room-search-row"><label class="search-field"><span>查找寝室</span><input v-model="candidateKeyword" class="input" placeholder="输入楼栋名称或房间号" /></label><div class="filter-summary"><strong>{{ filteredCandidates.length }}</strong><span>个可迁入寝室</span></div></div>
      <p v-if="loading" class="empty-state">正在加载…</p>
      <div v-else-if="pagedCandidates.length" class="change-room-grid"><article v-for="room in pagedCandidates" :key="String(room.id)" class="change-room-card change-room-card-equal"><div><span class="eyebrow">{{ room.building_name }}</span><h3>{{ room.room_number }}室</h3><p>{{ room.floor_number }}层 · {{ room.room_type }} · 剩余{{ room.available_count }}个名额</p></div><button class="button primary" @click="requestChange(room)">选择此寝室</button></article></div>
      <p v-else class="empty-state">当前没有符合查找条件的空余寝室。</p>
      <nav v-if="!loading && filteredCandidates.length" class="change-room-pagination" aria-label="可迁入寝室分页"><div><strong>{{ currentCandidateStart }}–{{ currentCandidateEnd }}</strong><span> / 共{{ filteredCandidates.length }}条 · 第{{ changeRoomPage }} / {{ totalChangeRoomPages }}页</span></div><div class="button-row"><button class="button ghost small" :disabled="changeRoomPage <= 1" @click="changeRoomPage--">上一页</button><button class="button ghost small" :disabled="changeRoomPage >= totalChangeRoomPages" @click="changeRoomPage++">下一页</button></div></nav>
    </section>
    <section v-else class="panel empty-state"><h3>学校当前未开放单人换寝</h3><p>如有特殊需求，请联系管理员处理。</p></section>

    <section v-if="exchangePolicy.enabled" class="panel"><div class="section-head"><div><span class="eyebrow">双方意向交换</span><h3>按学号查找可交换学生</h3><p>查找前不会显示任何学生信息；仅支持完整或部分学号，不支持姓名模糊查询。</p></div></div><form class="exchange-search-row" @submit.prevent="searchExchangeCandidates"><label class="search-field"><span>学生学号</span><input v-model.trim="exchangeStudentNumber" class="input" autocomplete="off" maxlength="32" placeholder="请输入完整或部分学号" /></label><div class="button-row"><button v-if="exchangeSearchStarted" type="button" class="button ghost" @click="clearExchangeSearch">清空</button><button class="button secondary" :disabled="exchangeSearching || !exchangeStudentNumber.trim()">{{ exchangeSearching ? '正在查找…' : '查找学生' }}</button></div></form><div v-if="exchangeSearchStarted" class="exchange-candidate-grid"><article v-for="candidate in exchangeCandidates" :key="String(candidate.target_student_id)" class="exchange-candidate-card"><div><strong>{{ candidate.student_name }}</strong><small>{{ candidate.student_number }}</small></div><p>{{ candidate.building_name }} {{ candidate.room_number }}室<span v-if="candidate.bed_code"> · {{ candidate.bed_code }}</span></p><button class="button secondary" @click="openExchange(candidate)">发起交换</button></article><p v-if="!exchangeSearching && !exchangeCandidates.length" class="empty-state">未找到符合条件且可交换的在住学生。</p></div><p v-else class="empty-state">输入学号后再查找，不会默认列出全校学生。</p></section>
    <section v-else class="panel empty-state"><h3>学校当前未开放寝室交换</h3><p>单人换寝策略不受影响。</p></section>

    <section v-if="incomingExchanges.length" class="panel incoming-panel"><div class="section-head"><div><span class="eyebrow">待你确认</span><h3>收到的寝室交换邀请</h3></div></div><article v-for="item in incomingExchanges" :key="String(item.id)" class="incoming-exchange-card"><div><strong>{{ item.initiator_student_name }}（{{ item.initiator_student_number }}）</strong><p>希望用 {{ item.initiator_building_name }} {{ item.initiator_room_number }}室 {{ item.initiator_bed_code || '未确认床位' }} 与你的当前寝室床位交换。</p><small>原因：{{ item.reason }}</small></div><div class="button-row"><button class="button primary" :disabled="submitting" @click="respondExchange(item, true)">接受交换</button><button class="button ghost" :disabled="submitting" @click="respondExchange(item, false)">拒绝</button></div></article></section>

    <section class="panel"><div class="section-head"><div><span class="eyebrow">单人换寝记录</span><h3>我的换寝历史</h3></div></div><div class="table-wrap"><table><thead><tr><th>申请时间</th><th>原寝室</th><th>目标寝室</th><th>状态</th><th>原因/意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td>{{ item.created_at }}</td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td><span class="status-pill">{{ statusText(item.request_status) }}</span></td><td>{{ item.review_reason || item.reason }}</td><td><button v-if="item.request_status==='PENDING'" class="button ghost small" @click="cancelRequest(item)">取消申请</button></td></tr></tbody></table></div></section>

    <section class="panel"><div class="section-head"><div><span class="eyebrow">寝室交换记录</span><h3>双方交换历史</h3></div></div><div class="table-wrap"><table><thead><tr><th>对方学生</th><th>交换方向</th><th>状态</th><th>申请原因</th><th>处理意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangeRequests" :key="String(item.id)"><td>{{ item.initiator_student_number===profile.student_number ? item.target_student_name : item.initiator_student_name }}</td><td>{{ item.initiator_room_number }} ↔ {{ item.target_room_number }}</td><td><span class="status-pill">{{ statusText(item.request_status) }}</span></td><td>{{ item.reason }}</td><td>{{ item.review_reason || item.target_response_reason || '-' }}</td><td><button v-if="outgoingExchanges.includes(item) && ['WAITING_TARGET','PENDING_ADMIN'].includes(String(item.request_status))" class="button ghost small" @click="cancelExchange(item)">取消交换</button></td></tr></tbody></table></div></section>

    <AppModal :open="Boolean(target)" size="default" :busy="submitting" @close="closeDialog"><div v-if="target" class="room-change-dialog" role="dialog"><header class="section-head split-title"><div><span class="eyebrow">确认换寝</span><h3>{{ target.building_name }} {{ target.room_number }}室</h3><p>{{ policy.requiresApproval ? '提交后由管理员审批。' : '确认后将立即更新住宿记录。' }}</p></div><button class="button ghost small" @click="closeDialog">关闭</button></header><form class="form-stack" @submit.prevent="submit"><label><span>换寝原因</span><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="5" placeholder="请说明换寝原因，便于学校留档" /></label><div class="button-row dialog-actions"><button type="button" class="button ghost" @click="closeDialog">取消</button><button class="button primary" :disabled="submitting">{{ submitting ? '正在提交…' : policy.requiresApproval ? '提交申请' : '确认自由换寝' }}</button></div></form></div></AppModal>

    <div v-if="exchangeTarget" class="modal-overlay room-change-overlay" @click.self="closeExchangeDialog"><section class="modal-card room-change-dialog" role="dialog" aria-modal="true"><header class="section-head split-title"><div><span class="eyebrow">发起寝室交换</span><h3>邀请 {{ exchangeTarget.student_name }}</h3><p>对方当前住宿：{{ exchangeTarget.building_name }} {{ exchangeTarget.room_number }}室 {{ exchangeTarget.bed_code || '未确认床位' }}</p></div><button class="button ghost small" @click="closeExchangeDialog">关闭</button></header><form class="form-stack" @submit.prevent="submitExchange"><label><span>交换原因与双方意向说明</span><textarea v-model.trim="exchangeForm.reason" class="input" required maxlength="500" rows="5" placeholder="请说明交换原因；对方接受后系统才会继续处理" /></label><div class="button-row dialog-actions"><button type="button" class="button ghost" @click="closeExchangeDialog">取消</button><button class="button primary" :disabled="submitting">{{ submitting ? '正在发送…' : '发送交换邀请' }}</button></div></form></section></div>
  </div>
</template>

<style scoped>
.policy-summary-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.policy-summary-grid article{display:grid;gap:5px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.policy-summary-grid span,.policy-summary-grid small{color:var(--muted)}.change-room-search-row,.exchange-search-row,.change-room-pagination{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:16px}.change-room-search-row .search-field,.exchange-search-row .search-field{flex:1}.change-room-pagination{align-items:center;margin:18px 0 0;padding-top:14px;border-top:1px solid var(--line);color:var(--muted)}.change-room-pagination strong{color:var(--text)}.change-room-grid,.exchange-candidate-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));align-items:stretch;gap:12px}.change-room-card{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.change-room-card-equal{min-width:0;min-height:156px;height:100%}.change-room-card h3{margin:5px 0}.change-room-card p,.exchange-candidate-card p{margin:0;color:var(--muted)}.exchange-candidate-card{display:grid;gap:10px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.exchange-candidate-card div{display:grid;gap:3px}.exchange-candidate-card small{color:var(--muted)}.incoming-panel{border-color:#8fb8ef;background:#f7fbff}.incoming-exchange-card{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:15px;border:1px solid #bfd7f5;border-radius:14px;background:#fff}.incoming-exchange-card p{margin:6px 0}.incoming-exchange-card small{color:var(--muted)}.room-change-overlay{z-index:1260;padding:30px;background:rgba(9,23,48,.78);backdrop-filter:blur(7px)}.room-change-dialog{width:min(620px,calc(100vw - 60px));padding:26px;border-radius:26px;background:var(--panel,#fff)}.dialog-actions{justify-content:flex-end}.status-pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#eef4ff;color:#315f9d;font-size:12px}@media(max-width:720px){.policy-summary-grid{grid-template-columns:1fr}.change-room-search-row,.exchange-search-row,.change-room-pagination{align-items:stretch;flex-direction:column}.change-room-search-row .filter-summary,.exchange-search-row .button-row,.change-room-pagination .button-row{width:100%}.change-room-pagination .button-row{display:grid;grid-template-columns:1fr 1fr}.incoming-exchange-card{display:grid}.room-change-overlay{padding:10px}.room-change-dialog{width:100%;padding:18px;border-radius:22px}.change-room-card{align-items:flex-start;display:grid}}
</style>
