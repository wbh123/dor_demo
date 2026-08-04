<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const settings = ref<DataObject>({ mode: 'DISABLED' })
const requests = ref<DataObject[]>([])
const exchangePolicy = ref<DataObject>({ mode: 'DISABLED' })
const exchangeRequests = ref<DataObject[]>([])
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
const form = reactive({ mode: 'DISABLED', reason: '' })
const exchangeForm = reactive({ mode: 'DISABLED', reason: '' })
const reviewForm = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [settingResponse, requestResponse, exchangeSettingResponse, exchangeRequestResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-change/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-change/requests', { params: { status: statusFilter.value, keyword: keyword.value || undefined } }),
      api.get<ObjectSuccessResponse>('/api/v1/admin/room-exchanges/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/room-exchanges', { params: { status: exchangeStatusFilter.value, keyword: exchangeKeyword.value || undefined } }),
    ])
    settings.value = (settingResponse.data.data ?? {}) as DataObject
    form.mode = String(settings.value.mode ?? 'DISABLED')
    requests.value = (requestResponse.data.data ?? []) as DataObject[]
    exchangePolicy.value = (exchangeSettingResponse.data.data ?? {}) as DataObject
    exchangeForm.mode = String(exchangePolicy.value.mode ?? 'DISABLED')
    exchangeRequests.value = (exchangeRequestResponse.data.data ?? []) as DataObject[]
  } catch (cause) {
    error.value = translateError(cause)
  }
}

async function saveSettings() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    await api.put('/api/v1/admin/room-change/settings', { mode: form.mode, reason: form.reason.trim() })
    message.value = '单人换寝策略已更新。'
    form.reason = ''
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

async function saveExchangePolicy() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    await api.put('/api/v1/admin/room-exchanges/settings', {
      mode: exchangeForm.mode,
      reason: exchangeForm.reason.trim(),
    })
    message.value = '寝室交换策略已更新。'
    exchangeForm.reason = ''
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

function openReview(item: DataObject, action: 'approve'|'reject', kind: 'change'|'exchange') {
  reviewTarget.value = item
  reviewAction.value = action
  reviewKind.value = kind
  reviewForm.reason = ''
  error.value = ''
  message.value = ''
}
function closeReview() { if (!saving.value) reviewTarget.value = null }

async function submitReview() {
  if (!reviewTarget.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    const base = reviewKind.value === 'exchange'
      ? `/api/v1/admin/room-exchanges/${reviewTarget.value.id}`
      : `/api/v1/admin/room-change/requests/${reviewTarget.value.id}`
    await api.post(`${base}/${reviewAction.value}`, { reason: reviewForm.reason.trim() })
    message.value = reviewKind.value === 'exchange'
      ? reviewAction.value === 'approve' ? '寝室交换已批准并完成。' : '寝室交换已驳回。'
      : reviewAction.value === 'approve' ? '换寝申请已批准并执行。' : '换寝申请已驳回。'
    reviewTarget.value = null
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

function approveExchange(item: DataObject) { openReview(item, 'approve', 'exchange') }
function rejectExchange(item: DataObject) { openReview(item, 'reject', 'exchange') }

function statusText(value: unknown) {
  return ({
    PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', EXECUTED: '已完成',
    CANCELLED: '已取消', FAILED: '执行失败', WAITING_TARGET: '等待对方确认',
    PENDING_ADMIN: '等待管理员审批',
  } as Record<string,string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整', 'ROOM CHANGE REVIEW') }}</span><h2>换寝与交换管理</h2><p>分别配置迁入空余寝室和双方寝室床位交换，并处理需要审批的申请。</p></div><button class="button secondary" @click="load">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="policy-grid">
      <article class="panel policy-panel"><div><span class="eyebrow">单人换寝策略</span><h3>迁入空余寝室或床位</h3><p>禁止换寝仅保留管理员人工调整；自由换寝立即执行；审批换寝由管理员确认。</p></div><form class="policy-form" @submit.prevent="saveSettings"><div class="mode-grid"><label :class="{active:form.mode==='DISABLED'}"><input v-model="form.mode" type="radio" value="DISABLED"/><strong>禁止换寝</strong><span>学生端不可提交</span></label><label :class="{active:form.mode==='FREE'}"><input v-model="form.mode" type="radio" value="FREE"/><strong>自由换寝</strong><span>符合条件即刻执行</span></label><label :class="{active:form.mode==='APPROVAL_REQUIRED'}"><input v-model="form.mode" type="radio" value="APPROVAL_REQUIRED"/><strong>管理员审批</strong><span>批准后原子执行</span></label></div><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="3" placeholder="填写策略调整原因"/><button class="button primary" :disabled="saving">{{ saving ? '保存中…' : '保存单人换寝策略' }}</button></form></article>

      <article class="panel policy-panel exchange-policy-panel"><div><span class="eyebrow">寝室交换策略</span><h3>双方交换现有寝室和床位</h3><p>无论是否需要管理员审批，均必须由发起人和被邀请学生双方明确确认。</p></div><form class="policy-form" @submit.prevent="saveExchangePolicy"><div class="mode-grid"><label :class="{active:exchangeForm.mode==='DISABLED'}"><input v-model="exchangeForm.mode" type="radio" value="DISABLED"/><strong>禁止交换</strong><span>不开放双人交换</span></label><label :class="{active:exchangeForm.mode==='MUTUAL_CONFIRMATION'}"><input v-model="exchangeForm.mode" type="radio" value="MUTUAL_CONFIRMATION"/><strong>双方确认</strong><span>对方接受后直接执行</span></label><label :class="{active:exchangeForm.mode==='APPROVAL_REQUIRED'}"><input v-model="exchangeForm.mode" type="radio" value="APPROVAL_REQUIRED"/><strong>双方确认并审批</strong><span>双方同意后管理员批准</span></label></div><textarea v-model.trim="exchangeForm.reason" class="input" required maxlength="500" rows="3" placeholder="填写交换策略调整原因"/><button class="button primary" :disabled="saving">{{ saving ? '保存中…' : '保存寝室交换策略' }}</button></form></article>
    </section>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">单人换寝审批</span><h3>空余寝室换寝申请与历史</h3></div><div class="filter-row"><input v-model.trim="keyword" class="input" placeholder="搜索学生或寝室" @keyup.enter="load"/><select v-model="statusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="PENDING">待审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option><option value="CANCELLED">已取消</option><option value="FAILED">执行失败</option></select><button class="button secondary" @click="load">查询</button></div></div><div class="table-wrap"><table><thead><tr><th>学生</th><th>原寝室</th><th>目标寝室</th><th>申请原因</th><th>状态</th><th>审批意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }}</small></td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td>{{ item.reason }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || '-' }}</td><td><div v-if="item.request_status==='PENDING'" class="button-row"><button class="button primary small" @click="openReview(item,'approve','change')">批准</button><button class="button ghost small danger-text" @click="openReview(item,'reject','change')">驳回</button></div></td></tr></tbody></table></div></section>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">双方交换审批</span><h3>寝室床位交换申请与历史</h3></div><div class="filter-row"><input v-model.trim="exchangeKeyword" class="input" placeholder="搜索双方学生" @keyup.enter="load"/><select v-model="exchangeStatusFilter" class="input" @change="load"><option value="ALL">全部状态</option><option value="WAITING_TARGET">等待对方确认</option><option value="PENDING_ADMIN">等待管理员审批</option><option value="EXECUTED">已完成</option><option value="REJECTED">已驳回</option><option value="CANCELLED">已取消</option><option value="FAILED">执行失败</option></select><button class="button secondary" @click="load">查询</button></div></div><div class="table-wrap"><table><thead><tr><th>双方学生</th><th>发起方寝室</th><th>被邀请方寝室</th><th>申请原因</th><th>状态</th><th>双方/审批意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangeRequests" :key="String(item.id)"><td><strong>{{ item.initiator_student_name }} ↔ {{ item.target_student_name }}</strong><small>{{ item.initiator_student_number }} / {{ item.target_student_number }}</small></td><td>{{ item.initiator_building_name }} {{ item.initiator_room_number }} {{ item.initiator_bed_code || '' }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }} {{ item.target_bed_code || '' }}</td><td>{{ item.reason }}</td><td>{{ statusText(item.request_status) }}</td><td>{{ item.review_reason || item.target_response_reason || '-' }}</td><td><div v-if="item.request_status==='PENDING_ADMIN'" class="button-row"><button class="button primary small" @click="approveExchange(item)">批准交换</button><button class="button ghost small danger-text" @click="rejectExchange(item)">驳回</button></div></td></tr></tbody></table></div></section>

    <div v-if="reviewTarget" class="modal-overlay review-overlay" @click.self="closeReview"><section class="modal-card review-dialog" role="dialog" aria-modal="true"><header class="section-head"><div><span class="eyebrow">{{ reviewKind==='exchange' ? '寝室交换审批' : '换寝审批' }}</span><h3>{{ reviewAction==='approve' ? '批准并执行' : '驳回申请' }}</h3><p v-if="reviewKind==='change'">{{ reviewTarget.student_name }}：{{ reviewTarget.source_room_number }} → {{ reviewTarget.target_room_number }}</p><p v-else>{{ reviewTarget.initiator_student_name }} 与 {{ reviewTarget.target_student_name }}：{{ reviewTarget.initiator_room_number }} ↔ {{ reviewTarget.target_room_number }}</p></div></header><form class="form-stack" @submit.prevent="submitReview"><textarea v-model.trim="reviewForm.reason" class="input" required maxlength="500" rows="4" placeholder="填写审批意见"/><div class="button-row dialog-actions"><button type="button" class="button ghost" @click="closeReview">取消</button><button class="button" :class="reviewAction==='approve'?'primary':'danger'" :disabled="saving">确认</button></div></form></section></div>
  </div>
</template>

<style scoped>
.policy-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.policy-panel{display:grid;gap:18px}.exchange-policy-panel{border-color:#b9d2ef}.policy-form{display:grid;gap:12px}.mode-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.mode-grid label{display:grid;gap:4px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft);cursor:pointer}.mode-grid label.active{border-color:#5684c9;background:#eef5ff}.mode-grid input{position:absolute;opacity:0}.mode-grid span{color:var(--muted);font-size:12px}.filter-row{display:grid;grid-template-columns:minmax(180px,1fr) 180px auto;gap:8px}.danger-text{color:var(--danger)}.review-overlay{z-index:1260;padding:30px;background:rgba(9,23,48,.78);backdrop-filter:blur(7px)}.review-dialog{width:min(580px,calc(100vw - 60px));padding:26px;border-radius:26px;background:var(--panel,#fff)}.dialog-actions{justify-content:flex-end}@media(max-width:1100px){.policy-grid{grid-template-columns:1fr}}@media(max-width:900px){.mode-grid{grid-template-columns:1fr}}@media(max-width:640px){.filter-row{grid-template-columns:1fr}.review-overlay{padding:10px}.review-dialog{width:100%;padding:18px;border-radius:22px}}
</style>
