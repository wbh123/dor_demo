<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ThreeStateToggle from '../../components/admin/ThreeStateToggle.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const requests = ref<DataObject[]>([])
const exchangeRequests = ref<DataObject[]>([])
const roomChangeMode = ref('DISABLED')
const roomExchangeMode = ref('DISABLED')
const roomChangeReason = ref('')
const roomExchangeReason = ref('')
const statusFilter = ref('ALL')
const exchangeStatusFilter = ref('ALL')
const keyword = ref('')
const exchangeKeyword = ref('')
const error = ref('')
const message = ref('')
const saving = ref(false)
const reviewTarget = ref<DataObject | null>(null)
const reviewAction = ref<'approve'|'reject'>('approve')
const reviewKind = ref<'change'|'exchange'>('change')
const reviewForm = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

const roomChangeOptions = [
  { value:'DISABLED', label:'禁止换寝', description:'学生端不可提交空余寝室换寝' },
  { value:'FREE', label:'直接执行', description:'满足条件后立即迁入空余寝室或床位' },
  { value:'APPROVAL_REQUIRED', label:'管理员审批', description:'学生提交后由管理员批准执行' },
]
const roomExchangeOptions = [
  { value:'DISABLED', label:'禁止交换', description:'不开放双方寝室和床位交换' },
  { value:'MUTUAL_CONFIRMATION', label:'双方确认', description:'双方确认后立即完成交换' },
  { value:'APPROVAL_REQUIRED', label:'确认并审批', description:'双方确认后再由管理员批准' },
]

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [policy, list, exchangePolicy, exchangeList] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-change/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-change/requests', { params: { status: statusFilter.value, keyword: keyword.value || undefined } }),
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-exchanges/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-exchanges', { params: { status: exchangeStatusFilter.value, keyword: exchangeKeyword.value || undefined } }),
    ])
    roomChangeMode.value = String(((policy.data.data ?? {}) as DataObject).mode ?? 'DISABLED')
    roomExchangeMode.value = String(((exchangePolicy.data.data ?? {}) as DataObject).mode ?? 'DISABLED')
    requests.value = (list.data.data ?? []) as DataObject[]
    exchangeRequests.value = (exchangeList.data.data ?? []) as DataObject[]
  } catch (cause) { error.value = translateError(cause) }
}

async function savePolicy(kind: 'change'|'exchange') {
  if (saving.value) return
  const reason = (kind === 'change' ? roomChangeReason.value : roomExchangeReason.value).trim()
  if (!reason) { error.value = '请填写策略调整原因。'; return }
  saving.value = true; error.value = ''; message.value = ''
  try {
    if (kind === 'change') {
      await api.put('/api/v1/admin/room-change/settings', { mode: roomChangeMode.value, reason })
      roomChangeReason.value = ''
      message.value = '空余寝室换寝策略已更新。'
    } else {
      await api.put('/api/v1/admin/room-exchanges/settings', { mode: roomExchangeMode.value, reason })
      roomExchangeReason.value = ''
      message.value = '双方寝室交换策略已更新。'
    }
    await load()
  } catch (cause) { error.value = translateError(cause) }
  finally { saving.value = false }
}

function openReview(item: DataObject, action: 'approve'|'reject', kind: 'change'|'exchange') {
  reviewTarget.value = item; reviewAction.value = action; reviewKind.value = kind; reviewForm.reason = ''
}
async function submitReview() {
  if (!reviewTarget.value || saving.value || !reviewForm.reason.trim()) return
  saving.value = true
  try {
    const base = reviewKind.value === 'exchange'
      ? `/api/v1/admin/room-exchanges/${reviewTarget.value.id}`
      : `/api/v1/admin/room-change/requests/${reviewTarget.value.id}`
    await api.post(`${base}/${reviewAction.value}`, { reason: reviewForm.reason.trim() })
    reviewTarget.value = null; message.value = reviewAction.value === 'approve' ? '申请已批准并执行。' : '申请已驳回。'
    await load()
  } catch (cause) { error.value = translateError(cause) }
  finally { saving.value = false }
}
function statusText(value: unknown) {
  return ({ PENDING:'待审批',APPROVED:'已批准',REJECTED:'已驳回',EXECUTED:'已完成',CANCELLED:'已取消',FAILED:'执行失败',WAITING_TARGET:'等待对方确认',PENDING_ADMIN:'等待管理员审批' } as Record<string,string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整','ROOM CHANGE REVIEW') }}</span><h2>换寝与交换管理</h2><p>两个业务卡片均使用一致的三状态按钮切换。</p></div><button class="button secondary" @click="load">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="policy-grid">
      <article class="panel policy-panel"><div><span class="eyebrow">空余寝室换寝</span><h3>迁入空余寝室或床位</h3></div><ThreeStateToggle v-model="roomChangeMode" :options="roomChangeOptions" /><textarea v-model="roomChangeReason" class="input" rows="3" maxlength="500" placeholder="填写策略调整原因"/><button class="button primary" :disabled="saving" @click="savePolicy('change')">保存空余寝室换寝策略</button></article>
      <article class="panel policy-panel"><div><span class="eyebrow">双方寝室交换</span><h3>交换双方现有寝室和床位</h3></div><ThreeStateToggle v-model="roomExchangeMode" :options="roomExchangeOptions" /><textarea v-model="roomExchangeReason" class="input" rows="3" maxlength="500" placeholder="填写策略调整原因"/><button class="button primary" :disabled="saving" @click="savePolicy('exchange')">保存双方交换策略</button></article>
    </section>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">空余寝室换寝</span><h3>申请与历史</h3></div><div class="filter-row"><input v-model="keyword" class="input" placeholder="搜索学生或寝室" @keyup.enter="load"><select v-model="statusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="PENDING">待审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option></select></div></div><div class="table-wrap"><table><thead><tr><th>学生</th><th>原寝室</th><th>目标寝室</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td>{{ item.student_name }}<small>{{ item.student_number }}</small></td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td>{{ statusText(item.request_status) }}</td><td><div v-if="item.request_status==='PENDING'" class="button-row"><button class="button primary small" @click="openReview(item,'approve','change')">批准</button><button class="button ghost small" @click="openReview(item,'reject','change')">驳回</button></div></td></tr></tbody></table></div></section>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">双方寝室交换</span><h3>申请与历史</h3></div><div class="filter-row"><input v-model="exchangeKeyword" class="input" placeholder="搜索双方学生" @keyup.enter="load"><select v-model="exchangeStatusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="WAITING_TARGET">等待对方确认</option><option value="PENDING_ADMIN">等待管理员审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option></select></div></div><div class="table-wrap"><table><thead><tr><th>双方学生</th><th>发起方寝室</th><th>被邀请方寝室</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangeRequests" :key="String(item.id)"><td>{{ item.initiator_student_name }} ↔ {{ item.target_student_name }}</td><td>{{ item.initiator_building_name }} {{ item.initiator_room_number }} {{ item.initiator_bed_code || '' }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }} {{ item.target_bed_code || '' }}</td><td>{{ statusText(item.request_status) }}</td><td><div v-if="item.request_status==='PENDING_ADMIN'" class="button-row"><button class="button primary small" @click="openReview(item,'approve','exchange')">批准</button><button class="button ghost small" @click="openReview(item,'reject','exchange')">驳回</button></div></td></tr></tbody></table></div></section>

    <div v-if="reviewTarget" class="modal-overlay" @click.self="reviewTarget=null"><section class="modal-card review-dialog"><h3>{{ reviewAction==='approve' ? '批准并执行' : '驳回申请' }}</h3><textarea v-model="reviewForm.reason" class="input" rows="4" required placeholder="填写审批意见"/><div class="button-row"><button class="button ghost" @click="reviewTarget=null">取消</button><button class="button" :class="reviewAction==='approve'?'primary':'danger'" :disabled="saving" @click="submitReview">确认</button></div></section></div>
  </div>
</template>

<style scoped>.policy-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.policy-panel{display:grid;gap:16px}.filter-row{display:grid;grid-template-columns:minmax(180px,1fr) 180px;gap:8px}.review-dialog{width:min(560px,calc(100vw - 24px));display:grid;gap:14px;padding:24px;border-radius:24px}.review-dialog .button-row{justify-content:flex-end}@media(max-width:980px){.policy-grid{grid-template-columns:1fr}}@media(max-width:640px){.filter-row{grid-template-columns:1fr}}
</style>
