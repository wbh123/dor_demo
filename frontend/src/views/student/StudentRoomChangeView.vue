<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const policy = ref<DataObject>({ mode: 'DISABLED', enabled: false, requiresApproval: false })
const candidates = ref<DataObject[]>([])
const requests = ref<DataObject[]>([])
const target = ref<DataObject | null>(null)
const submitting = ref(false)
const loading = ref(true)
const error = ref('')
const message = ref('')
const form = reactive({ reason: '' })
const { subtitle, translateError } = useI18n()

const modeText = computed(() => ({ DISABLED: '未开放', FREE: '自由换寝', APPROVAL_REQUIRED: '管理员审批' } as Record<string,string>)[String(policy.value.mode)] ?? '未开放')

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    const policyResponse = await api.get<ObjectSuccessResponse>('/api/v1/student/room-change/policy')
    policy.value = (policyResponse.data.data ?? {}) as DataObject
    const historyResponse = await api.get<ListSuccessResponse>('/api/v1/student/room-change/requests')
    requests.value = (historyResponse.data.data ?? []) as DataObject[]
    if (Boolean(policy.value.enabled)) {
      const candidateResponse = await api.get<ListSuccessResponse>('/api/v1/student/room-change/candidates')
      candidates.value = (candidateResponse.data.data ?? []) as DataObject[]
    } else candidates.value = []
  } catch (reason) { error.value = translateError(reason) }
  finally { loading.value = false }
}

function requestChange(room: DataObject) {
  target.value = room; form.reason = ''; error.value = ''; message.value = ''
}
function closeDialog() { if (!submitting.value) target.value = null }

async function submit() {
  if (!target.value || submitting.value) return
  submitting.value = true; error.value = ''; message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/student/room-change/requests', {
      targetRoomId: Number(target.value.id), reason: form.reason.trim(),
    })
    const result = (response.data.data ?? {}) as DataObject
    message.value = String(result.request_status) === 'EXECUTED' ? '换寝已执行，当前住宿信息已更新。' : '换寝申请已提交，请等待管理员审批。'
    target.value = null
    await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}

async function cancelRequest(item: DataObject) {
  const reason = '学生主动取消待审核换寝申请'
  try {
    await api.post(`/api/v1/student/room-change/requests/${item.id}/cancel`, { reason })
    message.value = '换寝申请已取消。'; await load()
  } catch (cause) { error.value = translateError(cause) }
}

function statusText(value: unknown) { return ({ PENDING:'待审批',APPROVED:'已批准',REJECTED:'已驳回',EXECUTED:'已完成',CANCELLED:'已取消',FAILED:'执行失败' } as Record<string,string>)[String(value)] ?? String(value) }
</script>

<template><div class="content-column"><header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整','ROOM CHANGE') }}</span><h2>申请换寝</h2><p>当前策略：<strong>{{ modeText }}</strong>。自由换寝会立即更新住宿；审批模式需管理员批准。</p></div><button class="button secondary" @click="load">刷新</button></header><p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
<section v-if="!policy.enabled" class="panel empty-state"><h3>学校当前未开放换寝</h3><p>如有特殊住宿需求，请联系学校管理员线下处理。</p></section><template v-else><section class="panel"><div class="section-head"><div><span class="eyebrow">可换入寝室</span><h3>符合条件的空余寝室</h3><p>系统已按性别、国内生/国际生属性和真实剩余容量过滤。</p></div></div><p v-if="loading" class="empty-state">正在加载…</p><div v-else class="change-room-grid"><article v-for="room in candidates" :key="String(room.id)" class="change-room-card"><div><span class="eyebrow">{{ room.building_name }}</span><h3>{{ room.room_number }}室</h3><p>{{ room.floor_number }}层 · {{ room.room_type }} · 剩余{{ room.available_count }}个名额</p></div><button class="button primary" @click="requestChange(room)">选择此寝室</button></article><p v-if="!candidates.length" class="empty-state">当前没有符合条件的空余寝室。</p></div></section></template>
<section class="panel"><div class="section-head"><div><span class="eyebrow">申请记录</span><h3>我的换寝历史</h3></div></div><div class="table-wrap"><table><thead><tr><th>申请时间</th><th>原寝室</th><th>目标寝室</th><th>状态</th><th>原因/意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td>{{ item.created_at }}</td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td><span class="status-pill">{{ statusText(item.request_status) }}</span></td><td>{{ item.review_reason || item.reason }}</td><td><button v-if="item.request_status==='PENDING'" class="button ghost small" @click="cancelRequest(item)">取消申请</button></td></tr></tbody></table></div></section>
<div v-if="target" class="modal-overlay room-change-overlay" @click.self="closeDialog"><section class="modal-card room-change-dialog" role="dialog" aria-modal="true"><header class="section-head split-title"><div><span class="eyebrow">确认换寝</span><h3>{{ target.building_name }} {{ target.room_number }}室</h3><p>{{ policy.requiresApproval ? '提交后由管理员审批。' : '确认后将立即更新住宿记录。' }}</p></div><button class="button ghost small" @click="closeDialog">关闭</button></header><form class="form-stack" @submit.prevent="submit"><label><span>换寝原因</span><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="5" placeholder="请说明换寝原因，便于学校留档" /></label><div class="button-row dialog-actions"><button type="button" class="button ghost" @click="closeDialog">取消</button><button class="button primary" :disabled="submitting">{{ submitting?'正在提交…':policy.requiresApproval?'提交申请':'确认自由换寝' }}</button></div></form></section></div></div></template>

<style scoped>.change-room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}.change-room-card{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.change-room-card h3{margin:5px 0}.change-room-card p{margin:0;color:var(--muted)}.room-change-overlay{z-index:1260;padding:30px;background:rgba(9,23,48,.78);backdrop-filter:blur(7px)}.room-change-dialog{width:min(620px,calc(100vw - 60px));padding:26px;border-radius:26px;background:var(--panel,#fff)}.dialog-actions{justify-content:flex-end}.status-pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#eef4ff;color:#315f9d;font-size:12px}@media(max-width:640px){.room-change-overlay{padding:10px}.room-change-dialog{width:100%;padding:18px;border-radius:22px}.change-room-card{align-items:flex-start;display:grid}}</style>
