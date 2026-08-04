<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

interface RoomCandidate extends DataObject {
  beds: DataObject[]
}

const policy = ref<DataObject>({ enabled: false, offerTtlMinutes: 30 })
const candidates = ref<DataObject[]>([])
const entries = ref<DataObject[]>([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')
const now = ref(Date.now())
const { subtitle, translateError } = useI18n()
let timer = 0

const roomCandidates = computed<RoomCandidate[]>(() => {
  const grouped = new Map<string, RoomCandidate>()
  for (const candidate of candidates.value) {
    const key = String(candidate.room_id)
    const current = grouped.get(key)
    if (current) {
      if (candidate.bed_id) current.beds.push(candidate)
      continue
    }
    grouped.set(key, {
      ...candidate,
      beds: candidate.bed_id ? [candidate] : [],
    } as RoomCandidate)
  }
  return [...grouped.values()]
})

const activeEntries = computed(() => entries.value.filter((item) =>
  ['WAITING', 'OFFERED'].includes(String(item.entry_status))))
const historyEntries = computed(() => entries.value.filter((item) =>
  !['WAITING', 'OFFERED'].includes(String(item.entry_status))))

onMounted(() => {
  load()
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => window.clearInterval(timer))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [policyResponse, entryResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/waitlist/policy'),
      api.get<ListSuccessResponse>('/api/v1/student/waitlist/entries'),
    ])
    policy.value = (policyResponse.data.data ?? {}) as DataObject
    entries.value = (entryResponse.data.data ?? []) as DataObject[]
    if (Boolean(policy.value.enabled) && !activeEntries.value.length) {
      const candidateResponse = await api.get<ListSuccessResponse>('/api/v1/student/waitlist/candidates')
      candidates.value = (candidateResponse.data.data ?? []) as DataObject[]
    } else {
      candidates.value = []
    }
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function joinWaitlist(room: RoomCandidate, bed?: DataObject) {
  const target = bed?.bed_code
    ? `${room.building_name} ${room.room_number}室 ${bed.bed_code}`
    : `${room.building_name} ${room.room_number}室任一可用名额`
  const reason = window.prompt(`申请候补：${target}\n请填写候补原因`, '希望在有空位时获得补位机会')
  if (!reason?.trim() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.post('/api/v1/student/waitlist/entries', {
      targetRoomId: Number(room.room_id),
      targetBedId: bed?.bed_id ? Number(bed.bed_id) : null,
      reason: reason.trim(),
    })
    message.value = '候补申请已加入队列。出现可用资源后，系统会发送限时邀请。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    busy.value = false
  }
}

async function withdrawEntry(entry: DataObject) {
  const reason = window.prompt('请填写退出候补的原因', '个人计划有变')
  if (!reason?.trim() || busy.value) return
  busy.value = true
  try {
    await api.post(`/api/v1/student/waitlist/entries/${entry.id}/withdraw`, { reason: reason.trim() })
    message.value = '已退出本次候补。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    busy.value = false
  }
}

async function acceptOffer(entry: DataObject) {
  if (!entry.offer_id || busy.value) return
  const reason = window.prompt('确认接受候补名额？请填写确认说明', '确认接受并办理补位')
  if (!reason?.trim()) return
  busy.value = true
  try {
    await api.post(`/api/v1/student/waitlist/offers/${entry.offer_id}/accept`, { reason: reason.trim() })
    message.value = '候补名额已接受，住宿信息已经更新。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    busy.value = false
  }
}

async function rejectOffer(entry: DataObject) {
  if (!entry.offer_id || busy.value) return
  const reason = window.prompt('请填写拒绝本次候补名额的原因', '本次不接受该名额')
  if (!reason?.trim()) return
  busy.value = true
  try {
    await api.post(`/api/v1/student/waitlist/offers/${entry.offer_id}/reject`, { reason: reason.trim() })
    message.value = '已拒绝本次候补邀请。需要时可以重新加入候补。'
    await load()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    busy.value = false
  }
}

function remaining(expiresAt: unknown) {
  const deadline = new Date(String(expiresAt ?? '')).getTime()
  if (!Number.isFinite(deadline)) return '到期时间未知'
  const seconds = Math.max(0, Math.floor((deadline - now.value) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return hours > 0 ? `${hours}小时${minutes}分` : `${minutes}分${rest}秒`
}

function statusText(value: unknown) {
  return ({
    WAITING: '排队中', OFFERED: '待确认', ASSIGNED: '已补位',
    WITHDRAWN: '已退出', EXPIRED: '邀请已超时', CANCELLED: '已取消',
  } as Record<string, string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('候补补位', 'WAITLIST') }}</span><h2>候补补位</h2><p>尚未入住时，可以为心仪寝室或床位排队；出现空位后会收到限时确认邀请。</p></div>
      <button class="button secondary" @click="load">刷新</button>
    </header>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel policy-banner" :class="{ disabled: !policy.enabled }">
      <div><span>当前状态</span><strong>{{ policy.enabled ? '候补已开放' : '候补未开放' }}</strong></div>
      <div><span>邀请有效期</span><strong>{{ policy.offerTtlMinutes ?? 30 }}分钟</strong></div>
      <p>{{ policy.enabled ? '同一时间只能保留一条进行中的候补，请及时处理限时邀请。' : '管理员开放候补后，此处会展示可排队的寝室和床位。' }}</p>
    </section>

    <p v-if="loading" class="panel empty-state">正在加载候补信息…</p>

    <section v-if="!loading && activeEntries.length" class="panel">
      <div class="section-head"><div><span class="eyebrow">当前候补</span><h3>进行中的候补</h3></div></div>
      <article v-for="entry in activeEntries" :key="String(entry.id)" class="active-entry" :class="{ offered: entry.entry_status==='OFFERED' }">
        <div>
          <strong>{{ entry.building_name }} {{ entry.room_number }}室<span v-if="entry.bed_code"> · {{ entry.bed_code }}</span></strong>
          <p>优先分：{{ entry.priority_score ?? 0 }} · 加入时间：{{ entry.joined_at }}</p>
          <small>申请原因：{{ entry.reason }}</small>
        </div>
        <div v-if="entry.entry_status==='OFFERED'" class="offer-actions">
          <span class="countdown">剩余 {{ remaining(entry.expires_at) }}</span>
          <div class="button-row"><button class="button primary" :disabled="busy" @click="acceptOffer(entry)">接受名额</button><button class="button ghost" :disabled="busy" @click="rejectOffer(entry)">拒绝</button></div>
        </div>
        <button v-else class="button ghost" :disabled="busy" @click="withdrawEntry(entry)">退出候补</button>
      </article>
    </section>

    <section v-if="!loading && policy.enabled && !activeEntries.length" class="panel">
      <div class="section-head"><div><span class="eyebrow">可候补资源</span><h3>选择寝室或具体床位</h3><p>即使当前已满，也可以排队等待后续释放的名额。</p></div></div>
      <div class="room-grid">
        <article v-for="room in roomCandidates" :key="String(room.room_id)" class="room-card">
          <header><div><strong>{{ room.building_name }} {{ room.room_number }}室</strong><p>{{ room.floor_number }}层 · {{ room.room_type }}</p></div><span class="status-chip">当前余量 {{ room.available_capacity ?? 0 }}</span></header>
          <button class="button secondary full" :disabled="busy" @click="joinWaitlist(room)">候补该寝室任一名额</button>
          <div v-if="room.beds.length" class="bed-list">
            <button v-for="bed in room.beds" :key="String(bed.bed_id)" type="button" :disabled="busy" @click="joinWaitlist(room, bed)">
              <span>{{ bed.bed_code }} · {{ bed.bed_type }}</span><small>{{ Number(bed.bed_occupied ?? 0) > 0 ? '当前有人' : '当前空闲' }}</small>
            </button>
          </div>
        </article>
        <p v-if="!roomCandidates.length" class="empty-state">当前没有符合你性别和学生类别的候补资源。</p>
      </div>
    </section>

    <section v-if="!loading && historyEntries.length" class="panel">
      <div class="section-head"><div><span class="eyebrow">历史记录</span><h3>候补处理历史</h3></div></div>
      <div class="table-wrap"><table><thead><tr><th>目标</th><th>状态</th><th>加入时间</th><th>处理时间</th><th>说明</th></tr></thead><tbody><tr v-for="entry in historyEntries" :key="String(entry.id)"><td>{{ entry.building_name }} {{ entry.room_number }}室 {{ entry.bed_code || '' }}</td><td>{{ statusText(entry.entry_status) }}</td><td>{{ entry.joined_at }}</td><td>{{ entry.assigned_at || entry.withdrawn_at || entry.responded_at || '-' }}</td><td>{{ entry.exit_reason || entry.response_reason || entry.reason }}</td></tr></tbody></table></div>
    </section>
  </div>
</template>

<style scoped>
.policy-banner{display:grid;grid-template-columns:180px 180px 1fr;align-items:center;gap:18px}.policy-banner>div{display:grid;gap:5px}.policy-banner span{color:var(--muted);font-size:13px}.policy-banner p{margin:0;color:var(--muted)}.policy-banner.disabled{opacity:.78}.active-entry{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.active-entry.offered{border-color:rgba(35,126,95,.45);background:#f1fbf7}.active-entry p{margin:6px 0;color:var(--muted)}.offer-actions{display:grid;justify-items:end;gap:10px}.countdown{font-weight:700;color:#17664f}.room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:14px}.room-card{display:grid;gap:12px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.room-card header{display:flex;justify-content:space-between;gap:12px}.room-card p{margin:5px 0 0;color:var(--muted)}.bed-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.bed-list button{display:grid;gap:4px;text-align:left;padding:10px;border:1px solid var(--line);border-radius:12px;background:var(--panel);cursor:pointer}.bed-list button:hover{border-color:var(--primary)}.bed-list small{color:var(--muted)}@media(max-width:760px){.policy-banner{grid-template-columns:1fr}.active-entry{display:grid}.offer-actions{justify-items:start}.bed-list{grid-template-columns:1fr}}
</style>
