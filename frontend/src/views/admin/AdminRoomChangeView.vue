<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const settings = ref<DataObject>({ mode: 'DISABLED' })
const requests = ref<DataObject[]>([])
const exchangeSettings = ref<DataObject>({ mode: 'DISABLED' })
const exchangeRequests = ref<DataObject[]>([])
const statusFilter = ref('ALL')
const exchangeStatusFilter = ref('ALL')
const keyword = ref('')
const exchangeKeyword = ref('')
const error = ref('')
const message = ref('')
const saving = ref(false)
const reviewTarget = ref<DataObject | null>(null)
const reviewType = ref<'change' | 'exchange'>('change')
const reviewAction = ref<'approve' | 'reject'>('approve')
const form = reactive({ mode: 'DISABLED', reason: '' })
const exchangeForm = reactive({ mode: 'DISABLED', reason: '' })
const reviewForm = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [settingResponse, requestResponse, exchangeSettingResponse, exchangeResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-change/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-change/requests', { params: { status: statusFilter.value, keyword: keyword.value || undefined } }),
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-exchanges/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-exchanges', { params: { status: exchangeStatusFilter.value, keyword: exchangeKeyword.value || undefined } }),
    ])
    settings.value = (settingResponse.data.data ?? {}) as DataObject
    requests.value = (requestResponse.data.data ?? []) as DataObject[]
    exchangeSettings.value = (exchangeSettingResponse.data.data ?? {}) as DataObject
    exchangeRequests.value = (exchangeResponse.data.data ?? []) as DataObject[]
    form.mode = String(settings.value.mode ?? 'DISABLED')
    exchangeForm.mode = String(exchangeSettings.value.mode ?? 'DISABLED')
  } catch (cause) {
    error.value = translateError(cause)
  }
}

async function saveSettings() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await api.put('/api/v1/admin/room-change/settings', { mode: form.mode, reason: form.reason.trim() })
    form.reason = ''
    message.value = '单人换寝策略已更新。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

async function saveExchangeSettings() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await api.put('/api/v1/admin/room-exchanges/settings', { mode: exchangeForm.mode, reason: exchangeForm.reason.trim() })
    exchangeForm.reason = ''
    message.value = '寝室交换策略已更新。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

function openReview(item: DataObject, type: 'change' | 'exchange', action: 'approve' | 'reject') {
  reviewTarget.value = item
  reviewType.value = type
  reviewAction.value = action
  reviewForm.reason = ''
}

async function submitReview() {
  if (!reviewTarget.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    const base = reviewType.value === 'exchange' ? '/api/v1/admin/room-exchanges' : '/api/v1/admin/room-change/requests'
    await api.post(`${base}/${reviewTarget.value.id}/${reviewAction.value}`, { reason: reviewForm.reason.trim() })
    message.value = reviewAction.value === 'approve'
      ? reviewType.value === 'exchange' ? '寝室交换已批准并执行。' : '换寝申请已批准并执行。'
      : reviewType.value === 'exchange' ? '寝室交换已驳回。' : '换寝申请已驳回。'
    reviewTarget.value = null
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
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
    <header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整', 'ROOM CHANGE REVIEW') }}</span><h2>换寝与寝室交换管理</h2><p>分别配置迁入空余寝室和双方交换床位的业务规则。</p></div><button class="button secondary" @click="load">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <div class="policy-grid">
      <section class="panel policy-panel"><div><span class="eyebrow">单人换寝</span><h3>迁入空余寝室策略</h3><p>自由换寝立即执行；审批模式由管理员确认。</p></div><form class="policy-form" @submit.prevent="saveSettings"><label><span>开放方式</span><select v-model="form.mode" class="input"><option value="DISABLED">禁止换寝</option><option value="FREE">自由换寝</option><option value="APPROVAL_REQUIRED">管理员审批</option></select></label><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="3" placeholder="填写策略调整原因"/><button class="button primary" :disabled="saving">保存单人换寝策略</button></form></section>
      <section class="panel policy-panel"><div><span class="eyebrow">双方交换</span><h3>寝室床位交换策略</h3><p>双方必须先确认，管理员可设置是否继续审批。</p></div><form class="policy-form" @submit.prevent="saveExchangeSettings"><label><span>开放方式</span><select v-model="exchangeForm.mode" class="input"><option value="DISABLED">禁止交换</option><option value="MUTUAL_CONFIRMATION">双方确认后直接交换</option><option value="APPROVAL_REQUIRED">双方确认后管理员审批</option></select></label><textarea v-model.trim="exchangeForm.reason" class="input" required maxlength="500" rows="3" placeholder="填写策略调整原因"/><button class="button primary" :disabled="saving">保存寝室交换策略</button></form></section>
    </div>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">单人换寝</span><h3>换寝申请与历史</h3></div><div class="filter-row"><input v-model.trim="keyword" class="input" placeholder="搜索学生或寝室" @keyup.enter="load"/><select v-model="statusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="PENDING">待审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option><option value="CANCELLED">已取消</option><option value="FAILED">执行失败</option></select><button class="button secondary" @click="load">查询</button></div></div><div class="table-wrap"><table><thead><tr><th>学生</th><th>原寝室</th><th>目标寝室</th><th>原因</th><th>状态</th><th>意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }}</small></td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td>{{ item.reason }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || '-' }}</td><td><div v-if="item.request_status==='PENDING'" class="button-row"><button class="button primary small" @click="openReview(item,'change','approve')">批准</button><button class="button ghost small" @click="openReview(item,'change','reject')">驳回</button></div></td></tr></tbody></table></div></section>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">寝室交换</span><h3>双方交换申请与历史</h3></div><div class="filter-row"><input v-model.trim="exchangeKeyword" class="input" placeholder="搜索双方学生" @keyup.enter="load"/><select v-model="exchangeStatusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="WAITING_TARGET">等待对方确认</option><option value="PENDING_ADMIN">待管理员审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option><option value="CANCELLED">已取消</option></select><button class="button secondary" @click="load">查询</button></div></div><div class="table-wrap"><table><thead><tr><th>发起学生</th><th>目标学生</th><th>交换寝室</th><th>申请原因</th><th>状态</th><th>处理意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangeRequests" :key="String(item.id)"><td><strong>{{ item.initiator_student_name }}</strong><small>{{ item.initiator_student_number }}</small></td><td><strong>{{ item.target_student_name }}</strong><small>{{ item.target_student_number }}</small></td><td>{{ item.initiator_building_name }} {{ item.initiator_room_number }} ↔ {{ item.target_building_name }} {{ item.target_room_number }}</td><td>{{ item.reason }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || item.target_response_reason || '-' }}</td><td><div v-if="item.request_status==='PENDING_ADMIN'" class="button-row"><button class="button primary small" @click="openReview(item,'exchange','approve')">批准交换</button><button class="button ghost small" @click="openReview(item,'exchange','reject')">驳回</button></div></td></tr></tbody></table></div></section>

    <div v-if="reviewTarget" class="modal-overlay" @click.self="reviewTarget=null"><section class="modal-card review-dialog"><h3>{{ reviewAction==='approve' ? '批准并执行' : '驳回申请' }}</h3><p>{{ reviewType==='exchange' ? '双方寝室交换' : '学生换寝' }}</p><form class="form-stack" @submit.prevent="submitReview"><textarea v-model.trim="reviewForm.reason" class="input" required maxlength="500" rows="4" placeholder="填写审批意见"/><div class="button-row dialog-actions"><button type="button" class="button ghost" @click="reviewTarget=null">取消</button><button class="button primary" :disabled="saving">确认</button></div></form></section></div>
  </div>
</template>

<style scoped>
.policy-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.policy-panel{display:grid;gap:18px}.policy-form{display:grid;gap:12px}.filter-row{display:grid;grid-template-columns:minmax(180px,1fr) 180px auto;gap:8px}.review-dialog{width:min(560px,calc(100vw - 30px));padding:24px}.dialog-actions{justify-content:flex-end}@media(max-width:980px){.policy-grid{grid-template-columns:1fr}}@media(max-width:680px){.filter-row{grid-template-columns:1fr}}
</style>
