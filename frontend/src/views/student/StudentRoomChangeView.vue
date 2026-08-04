<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const policy = ref<DataObject>({ mode: 'DISABLED', enabled: false, requiresApproval: false })
const candidates = ref<DataObject[]>([])
const requests = ref<DataObject[]>([])
const profile = ref<DataObject>({})
const exchangeEnabled = ref(false)
const exchangeCandidates = ref<DataObject[]>([])
const exchanges = ref<DataObject[]>([])
const target = ref<DataObject | null>(null)
const exchangeTarget = ref<DataObject | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const form = reactive({ reason: '' })
const exchangeForm = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

const modeText = computed(() => ({
  DISABLED: '未开放', FREE: '自由换寝', APPROVAL_REQUIRED: '管理员审批',
} as Record<string, string>)[String(policy.value.mode)] ?? '未开放')
const currentStudentNumber = computed(() => String(profile.value.student_number ?? ''))
const incomingExchanges = computed(() => exchanges.value.filter((item) =>
  String(item.target_student_number) === currentStudentNumber.value
  && item.request_status === 'WAITING_TARGET'))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, policyResponse, historyResponse, exchangeResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),
      api.get<ObjectSuccessResponse>('/api/v1/student/room-change/policy'),
      api.get<ListSuccessResponse>('/api/v1/student/room-change/requests'),
      api.get<ListSuccessResponse>('/api/v1/student/room-exchanges'),
    ])
    profile.value = (profileResponse.data.data ?? {}) as DataObject
    policy.value = (policyResponse.data.data ?? {}) as DataObject
    requests.value = (historyResponse.data.data ?? []) as DataObject[]
    exchanges.value = (exchangeResponse.data.data ?? []) as DataObject[]

    if (Boolean(policy.value.enabled)) {
      const response = await api.get<ListSuccessResponse>('/api/v1/student/room-change/candidates')
      candidates.value = (response.data.data ?? []) as DataObject[]
    } else candidates.value = []

    try {
      const response = await api.get<ListSuccessResponse>('/api/v1/student/room-exchanges/candidates')
      exchangeCandidates.value = (response.data.data ?? []) as DataObject[]
      exchangeEnabled.value = true
    } catch {
      exchangeCandidates.value = []
      exchangeEnabled.value = false
    }
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

function openRoomChange(room: DataObject) {
  target.value = room
  form.reason = ''
}
function openExchange(item: DataObject) {
  exchangeTarget.value = item
  exchangeForm.reason = ''
}

async function submitRoomChange() {
  if (!target.value || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/student/room-change/requests', {
      targetRoomId: Number(target.value.id), reason: form.reason.trim(),
    })
    const result = (response.data.data ?? {}) as DataObject
    message.value = result.request_status === 'EXECUTED'
      ? '换寝已执行，住宿信息已经更新。' : '换寝申请已提交，等待管理员审批。'
    target.value = null
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    submitting.value = false
  }
}

async function submitExchange() {
  if (!exchangeTarget.value || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await api.post('/api/v1/student/room-exchanges', {
      targetStudentId: Number(exchangeTarget.value.target_student_id),
      reason: exchangeForm.reason.trim(),
    })
    exchangeTarget.value = null
    message.value = '寝室交换邀请已发送，等待对方学生确认。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    submitting.value = false
  }
}

async function respondExchange(item: DataObject, accepted: boolean) {
  const reason = window.prompt(
    accepted ? '请填写接受交换的说明' : '请填写拒绝交换的原因',
    accepted ? '双方已经达成交换意向' : '暂不同意本次交换',
  )
  if (!reason?.trim()) return
  submitting.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/student/room-exchanges/${item.id}/respond`, {
      accepted, reason: reason.trim(),
    })
    const result = (response.data.data ?? {}) as DataObject
    message.value = !accepted ? '已拒绝交换邀请。'
      : result.request_status === 'EXECUTED' ? '双方寝室与床位已完成交换。'
        : '已接受交换，等待管理员审批。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    submitting.value = false
  }
}

async function cancelExchange(item: DataObject) {
  const reason = window.prompt('请填写取消交换的原因', '双方计划有变')
  if (!reason?.trim()) return
  try {
    await api.post(`/api/v1/student/room-exchanges/${item.id}/cancel`, { reason: reason.trim() })
    message.value = '寝室交换申请已取消。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
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

function statusText(value: unknown) {
  return ({
    PENDING: '待审批', WAITING_TARGET: '等待对方确认', PENDING_ADMIN: '等待管理员审批',
    APPROVED: '已批准', REJECTED: '已驳回', EXECUTED: '已完成',
    CANCELLED: '已取消', FAILED: '执行失败',
  } as Record<string, string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('住宿调整', 'ROOM CHANGE') }}</span><h2>换寝与寝室交换</h2><p>既可以迁入空余寝室，也可以与另一名在住学生交换双方当前寝室和床位。</p></div>
      <button class="button secondary" @click="load">刷新</button>
    </header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel policy-summary"><article><span>单人换寝</span><strong>{{ modeText }}</strong></article><article><span>寝室交换</span><strong>{{ exchangeEnabled ? '已开放' : '未开放' }}</strong></article></section>

    <section v-if="policy.enabled" class="panel">
      <div class="section-head"><div><span class="eyebrow">空余资源换寝</span><h3>可迁入寝室</h3><p>系统已按性别、学生类别和真实剩余容量过滤。</p></div></div>
      <p v-if="loading" class="empty-state">正在加载…</p>
      <div v-else class="card-grid"><article v-for="room in candidates" :key="String(room.id)" class="action-card"><div><strong>{{ room.building_name }} {{ room.room_number }}室</strong><p>{{ room.floor_number }}层 · {{ room.room_type }} · 剩余{{ room.available_count }}个名额</p></div><button class="button primary" @click="openRoomChange(room)">申请换入</button></article><p v-if="!candidates.length" class="empty-state">当前没有符合条件的空余寝室。</p></div>
    </section>
    <section v-else class="panel empty-state"><h3>学校当前未开放单人换寝</h3></section>

    <section v-if="exchangeEnabled" class="panel">
      <div class="section-head"><div><span class="eyebrow">双方意向交换</span><h3>可邀请交换的学生</h3><p>对方接受后，按管理员设置直接交换或进入审批。</p></div></div>
      <div class="card-grid"><article v-for="item in exchangeCandidates" :key="String(item.target_student_id)" class="action-card"><div><strong>{{ item.student_name }}（{{ item.student_number }}）</strong><p>{{ item.building_name }} {{ item.room_number }}室<span v-if="item.bed_code"> · {{ item.bed_code }}</span></p></div><button class="button secondary" @click="openExchange(item)">发起交换</button></article><p v-if="!exchangeCandidates.length" class="empty-state">当前没有可发起交换的学生。</p></div>
    </section>
    <section v-else class="panel empty-state"><h3>学校当前未开放寝室交换</h3></section>

    <section v-if="incomingExchanges.length" class="panel">
      <div class="section-head"><div><span class="eyebrow">待你确认</span><h3>收到的交换邀请</h3></div></div>
      <article v-for="item in incomingExchanges" :key="String(item.id)" class="invitation-card"><div><strong>{{ item.initiator_student_name }}（{{ item.initiator_student_number }}）</strong><p>对方寝室：{{ item.initiator_building_name }} {{ item.initiator_room_number }}室 {{ item.initiator_bed_code || '未确认床位' }}</p><small>原因：{{ item.reason }}</small></div><div class="button-row"><button class="button primary" :disabled="submitting" @click="respondExchange(item, true)">接受</button><button class="button ghost" :disabled="submitting" @click="respondExchange(item, false)">拒绝</button></div></article>
    </section>

    <section class="panel"><div class="section-head"><div><span class="eyebrow">申请记录</span><h3>单人换寝历史</h3></div></div><div class="table-wrap"><table><thead><tr><th>申请时间</th><th>原寝室</th><th>目标寝室</th><th>状态</th><th>原因/意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td>{{ item.created_at }}</td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || item.reason }}</td><td><button v-if="item.request_status==='PENDING'" class="button ghost small" @click="cancelRequest(item)">取消</button></td></tr></tbody></table></div></section>

    <section class="panel"><div class="section-head"><div><span class="eyebrow">交换记录</span><h3>双方寝室交换历史</h3></div></div><div class="table-wrap"><table><thead><tr><th>发起人</th><th>对方</th><th>交换寝室</th><th>状态</th><th>原因/意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchanges" :key="String(item.id)"><td>{{ item.initiator_student_name }}</td><td>{{ item.target_student_name }}</td><td>{{ item.initiator_room_number }} ↔ {{ item.target_room_number }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || item.target_response_reason || item.reason }}</td><td><button v-if="String(item.initiator_student_number)===currentStudentNumber && ['WAITING_TARGET','PENDING_ADMIN'].includes(String(item.request_status))" class="button ghost small" @click="cancelExchange(item)">取消</button></td></tr></tbody></table></div></section>

    <div v-if="target" class="modal-overlay" @click.self="target=null"><section class="modal-card dialog"><h3>申请换入 {{ target.building_name }} {{ target.room_number }}室</h3><form class="form-stack" @submit.prevent="submitRoomChange"><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="4" placeholder="请填写换寝原因"/><div class="button-row"><button type="button" class="button ghost" @click="target=null">取消</button><button class="button primary" :disabled="submitting">提交</button></div></form></section></div>
    <div v-if="exchangeTarget" class="modal-overlay" @click.self="exchangeTarget=null"><section class="modal-card dialog"><h3>与 {{ exchangeTarget.student_name }} 交换寝室床位</h3><p>{{ exchangeTarget.building_name }} {{ exchangeTarget.room_number }}室 {{ exchangeTarget.bed_code || '未确认床位' }}</p><form class="form-stack" @submit.prevent="submitExchange"><textarea v-model.trim="exchangeForm.reason" class="input" required maxlength="500" rows="4" placeholder="请填写双方交换原因"/><div class="button-row"><button type="button" class="button ghost" @click="exchangeTarget=null">取消</button><button class="button primary" :disabled="submitting">发送邀请</button></div></form></section></div>
  </div>
</template>

<style scoped>
.policy-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.policy-summary article{display:grid;gap:5px;padding:15px;border-radius:14px;background:var(--soft)}.card-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}.action-card,.invitation-card{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.action-card p,.invitation-card p{margin:5px 0;color:var(--muted)}.dialog{width:min(580px,calc(100vw - 30px));padding:24px}.dialog .button-row{justify-content:flex-end}@media(max-width:680px){.policy-summary{grid-template-columns:1fr}.action-card,.invitation-card{display:grid;align-items:start}}
</style>
