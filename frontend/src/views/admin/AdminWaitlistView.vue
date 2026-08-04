<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const settings = ref<DataObject>({ enabled: false, offerTtlMinutes: 30, priorityMode: 'PRIORITY_THEN_FIFO', scanBatchSize: 50 })
const entries = ref<DataObject[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const status = ref('ALL')
const keyword = ref('')
const policyForm = reactive({ enabled: false, offerTtlMinutes: 30, priorityMode: 'PRIORITY_THEN_FIFO', scanBatchSize: 50, reason: '' })
const { subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [settingResponse, entryResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/waitlist/settings'),
      api.get<ListSuccessResponse>('/api/v1/admin/waitlist/entries', { params: { status: status.value, keyword: keyword.value || undefined } }),
    ])
    settings.value = (settingResponse.data.data ?? {}) as DataObject
    entries.value = (entryResponse.data.data ?? []) as DataObject[]
    policyForm.enabled = Boolean(settings.value.enabled)
    policyForm.offerTtlMinutes = Number(settings.value.offerTtlMinutes ?? 30)
    policyForm.priorityMode = String(settings.value.priorityMode ?? 'PRIORITY_THEN_FIFO')
    policyForm.scanBatchSize = Number(settings.value.scanBatchSize ?? 50)
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await api.put('/api/v1/admin/waitlist/settings', {
      enabled: policyForm.enabled,
      offerTtlMinutes: policyForm.offerTtlMinutes,
      priorityMode: policyForm.priorityMode,
      scanBatchSize: policyForm.scanBatchSize,
      reason: policyForm.reason.trim(),
    })
    policyForm.reason = ''
    message.value = '候补策略已保存。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

async function updatePriority(entry: DataObject) {
  const scoreText = window.prompt('请输入新的候补优先分（-100000至100000）', String(entry.priority_score ?? 0))
  if (scoreText === null) return
  const score = Number(scoreText)
  if (!Number.isInteger(score)) {
    error.value = '优先分必须是整数。'
    return
  }
  const reason = window.prompt('请填写调整优先分的原因', '根据线下核实结果调整')
  if (!reason?.trim()) return
  await perform(() => api.post(`/api/v1/admin/waitlist/entries/${entry.id}/priority`, { priorityScore: score, reason: reason.trim() }), '优先分已更新。')
}

async function createOffer(entry: DataObject) {
  const reason = window.prompt('请填写立即发送候补邀请的原因', '管理员确认资源可用')
  if (!reason?.trim()) return
  await perform(() => api.post(`/api/v1/admin/waitlist/entries/${entry.id}/offer`, { reason: reason.trim() }), '限时候补邀请已发送。')
}

async function directAssign(entry: DataObject) {
  const reason = window.prompt('直接分配将立即建立住宿记录，请填写原因', '管理员人工确认并直接补位')
  if (!reason?.trim()) return
  await perform(() => api.post(`/api/v1/admin/waitlist/entries/${entry.id}/assign`, { reason: reason.trim() }), '候补学生已直接分配。')
}

async function scanWaitlist() {
  const reason = window.prompt('请填写手动扫描原因', '管理员手动检查空余资源')
  if (!reason?.trim()) return
  saving.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/waitlist/scan', { reason: reason.trim() })
    const result = (response.data.data ?? {}) as DataObject
    message.value = `扫描完成：检查${result.scanned ?? 0}条，发出${result.offered ?? 0}个邀请，跳过${result.skipped ?? 0}条。`
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

async function perform(action: () => Promise<unknown>, success: string) {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await action()
    message.value = success
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}

function statusText(value: unknown) {
  return ({
    WAITING: '排队中', OFFERED: '待学生确认', ASSIGNED: '已补位',
    WITHDRAWN: '学生退出', EXPIRED: '邀请超时', CANCELLED: '已取消',
  } as Record<string, string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('候补管理', 'WAITLIST OPERATIONS') }}</span><h2>候补管理</h2><p>配置候补规则，查看队列，并对空余资源发送限时邀请或人工补位。</p></div>
      <button class="button secondary" :disabled="saving" @click="scanWaitlist">立即扫描空位</button>
    </header>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">候补策略</span><h3>开放与邀请规则</h3><p>关闭后不会新增候补或自动发出邀请，已有历史仍可查询。</p></div></div>
      <form class="policy-form" @submit.prevent="saveSettings">
        <label class="toggle-field"><input v-model="policyForm.enabled" type="checkbox"/><span>开放学生候补补位</span></label>
        <label><span>邀请有效期（分钟）</span><input v-model.number="policyForm.offerTtlMinutes" class="input" type="number" min="5" max="1440" required/></label>
        <label><span>排序方式</span><select v-model="policyForm.priorityMode" class="input"><option value="PRIORITY_THEN_FIFO">优先分优先，同分按加入时间</option><option value="FIFO">完全按加入时间</option></select></label>
        <label><span>每轮最多扫描</span><input v-model.number="policyForm.scanBatchSize" class="input" type="number" min="1" max="500" required/></label>
        <label class="reason-field"><span>调整原因</span><input v-model.trim="policyForm.reason" class="input" maxlength="500" required placeholder="填写本次策略调整原因"/></label>
        <button class="button primary" :disabled="saving">保存策略</button>
      </form>
    </section>

    <section class="panel">
      <div class="section-head split-title">
        <div><span class="eyebrow">候补队列</span><h3>候补申请与邀请</h3></div>
        <div class="filters"><input v-model.trim="keyword" class="input" placeholder="搜索学生、楼栋或房间" @keyup.enter="load"/><select v-model="status" class="input" @change="load"><option value="ALL">全部状态</option><option value="WAITING">排队中</option><option value="OFFERED">待学生确认</option><option value="ASSIGNED">已补位</option><option value="WITHDRAWN">已退出</option><option value="EXPIRED">已超时</option></select><button class="button secondary" @click="load">查询</button></div>
      </div>
      <p v-if="loading" class="empty-state">正在加载候补队列…</p>
      <div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>目标资源</th><th>优先分</th><th>状态</th><th>加入/邀请时间</th><th>原因与响应</th><th>操作</th></tr></thead><tbody><tr v-for="entry in entries" :key="String(entry.id)"><td><strong>{{ entry.student_name }}</strong><small>{{ entry.student_number }}</small></td><td>{{ entry.building_name }} {{ entry.room_number }}室<span v-if="entry.bed_code"> · {{ entry.bed_code }}</span></td><td><strong>{{ entry.priority_score ?? 0 }}</strong><button v-if="entry.entry_status==='WAITING'" class="text-button" @click="updatePriority(entry)">调整</button></td><td>{{ statusText(entry.entry_status) }}<small v-if="entry.offer_status">邀请：{{ entry.offer_status }}</small></td><td>{{ entry.joined_at }}<small v-if="entry.expires_at">到期：{{ entry.expires_at }}</small></td><td>{{ entry.response_reason || entry.exit_reason || entry.reason }}</td><td><div v-if="entry.entry_status==='WAITING'" class="button-row compact-actions"><button class="button secondary small" :disabled="saving" @click="createOffer(entry)">发送邀请</button><button class="button primary small" :disabled="saving" @click="directAssign(entry)">直接分配</button></div></td></tr><tr v-if="!entries.length"><td colspan="7" class="empty-state">暂无候补记录。</td></tr></tbody></table></div>
    </section>
  </div>
</template>

<style scoped>
.policy-form{display:grid;grid-template-columns:1.1fr repeat(3,minmax(150px,1fr));gap:12px;align-items:end}.policy-form label{display:grid;gap:6px}.toggle-field{display:flex!important;align-items:center;gap:9px;padding:11px 13px;border:1px solid var(--line);border-radius:12px}.reason-field{grid-column:1/-2}.filters{display:grid;grid-template-columns:minmax(220px,1fr) 180px auto;gap:8px}.compact-actions{flex-wrap:nowrap}.text-button{display:block;margin-top:4px;color:var(--primary);font-size:12px}@media(max-width:1100px){.policy-form{grid-template-columns:repeat(2,minmax(0,1fr))}.reason-field{grid-column:1/-1}}@media(max-width:760px){.policy-form,.filters{grid-template-columns:1fr}.reason-field{grid-column:auto}}
</style>
