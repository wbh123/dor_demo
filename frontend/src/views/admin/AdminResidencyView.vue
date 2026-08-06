<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppModal from '../../components/modal/AppModal.vue'
import TransientNotice from '../../components/common/TransientNotice.vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'
import AdminBedConfirmationView from './AdminBedConfirmationView.vue'

const residencyTab = ref<'RESIDENCY' | 'DECLARATION'>('RESIDENCY')
const items = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const keyword = ref('')
const mappingStatus = ref('ALL')
const roomId = ref<number | undefined>()
const selected = ref<DataObject | null>(null)
const roomBeds = ref<DataObject[]>([])
const selectedBedId = ref(0)
const reason = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const ending = ref<DataObject | null>(null)
const endReason = ref('')
const endingResidency = ref(false)

const filteredRooms = computed(() => rooms.value.filter((room) => Number(room.active_residents ?? 0) > 0))
const availableBeds = computed(() => roomBeds.value.filter((bed) => {
  const status = String(bed.operational_status ?? bed.operationalStatus)
  const occupant = bed.occupant ?? bed.student_name
  return status === 'ENABLED' && (!occupant || Number(bed.id) === Number(selected.value?.bed_id))
}))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/residencies', {
      params: {
        keyword: keyword.value || undefined,
        roomId: roomId.value,
        bedMappingStatus: mappingStatus.value,
      },
    })
    const data = (response.data.data ?? {}) as DataObject
    items.value = (data.items ?? []) as DataObject[]
    rooms.value = (data.rooms ?? []) as DataObject[]
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '在住信息加载失败'
  } finally {
    loading.value = false
  }
}

async function openBedDialog(item: DataObject) {
  selected.value = item
  selectedBedId.value = Number(item.bed_id ?? 0)
  reason.value = ''
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/rooms/${item.room_id}/bed-layout`)
    const data = (response.data.data ?? {}) as DataObject
    roomBeds.value = (data.beds ?? []) as DataObject[]
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '房间床位图加载失败'
  }
}

function closeDialog() {
  if (saving.value) return
  selected.value = null
  roomBeds.value = []
  selectedBedId.value = 0
  reason.value = ''
}

async function saveBed() {
  if (!selected.value || !selectedBedId.value || !reason.value.trim()) return
  saving.value = true
  try {
    await api.put(`/api/v1/admin/residencies/${selected.value.residency_id}/bed`, {
      bedId: selectedBedId.value,
      reason: reason.value.trim(),
    })
    message.value = `${selected.value.student_name}的实际床位已确认，来源已记录为管理员修改。`
    closeDialogAfterSave()
    await load()
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '床位确认失败'
  } finally {
    saving.value = false
  }
}

function requestEndResidency(item: DataObject) {
  ending.value = item
  endReason.value = ''
  error.value = ''
}

function closeEndDialog() {
  if (endingResidency.value) return
  ending.value = null
  endReason.value = ''
}

async function endResidency() {
  const item = ending.value
  if (!item || !endReason.value.trim() || endingResidency.value) return
  endingResidency.value = true
  try {
    await api.post(`/api/v1/admin/residencies/${item.residency_id}/end`, { reason: endReason.value.trim() })
    message.value = `${item.student_name}退宿办理成功，寝室容量已释放。`
    ending.value = null
    endReason.value = ''
    await load()
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '退宿处理失败'
  } finally {
    endingResidency.value = false
  }
}

function closeDialogAfterSave() {
  selected.value = null
  roomBeds.value = []
  selectedBedId.value = 0
  reason.value = ''
}

function scopeText(value: unknown) {
  return { DOMESTIC_ONLY: '国内生宿舍', INTERNATIONAL_ONLY: '国际生宿舍', MIXED: '混住宿舍' }[String(value)] ?? value
}
function methodText(value: unknown) {
  return {
    ROOM_SELECT: '个人选寝室', TEAM_ROOM_SELECT: '队伍选寝室', BED_SELECT: '个人选床位',
    TEAM_BED_SELECT: '队伍选床位', DIRECT_ROOM: '管理员直接分寝', DIRECT_BED: '管理员直接分床',
    MANUAL_ADJUSTMENT: '管理员修改', IMPORT_MIGRATION: '历史迁移',
  }[String(value)] ?? value
}
</script>

<template>
  <div class="content-column">
    <TransientNotice :message="message" type="success" @close="message = ''" />
    <div class="page-title"><span class="eyebrow">RESIDENCY AND BED REVIEW</span><h2>在住与床位核查</h2><p>统一维护正式在住关系、管理员床位确认和学生实际床位申报，避免两个页面重复处理同一业务。</p></div>
    <div class="residency-tabs"><button class="button" :class="residencyTab === 'RESIDENCY' ? 'primary' : 'ghost'" @click="residencyTab = 'RESIDENCY'">在住名单与管理员确认</button><button class="button" :class="residencyTab === 'DECLARATION' ? 'primary' : 'ghost'" @click="residencyTab = 'DECLARATION'">学生申报核查</button></div>
    <template v-if="residencyTab === 'RESIDENCY'">
    <p v-if="error" class="alert error">{{ error }}</p>

    <div class="residency-stats">
      <article class="panel"><span>有效在住</span><strong>{{ items.length }}</strong></article>
      <article class="panel"><span>待确认床位</span><strong>{{ items.filter((item) => !item.bed_id).length }}</strong></article>
      <article class="panel"><span>涉及寝室</span><strong>{{ filteredRooms.length }}</strong></article>
    </div>

    <section class="panel">
      <form class="residency-filter" @submit.prevent="load">
        <input v-model.trim="keyword" class="input" placeholder="搜索学号、姓名、楼栋或房间" />
        <select v-model="roomId" class="input"><option :value="undefined">全部在住寝室</option><option v-for="room in filteredRooms" :key="String(room.room_id)" :value="Number(room.room_id)">{{ room.building_name }} {{ room.room_number }} · 在住{{ room.active_residents }}人</option></select>
        <select v-model="mappingStatus" class="input"><option value="ALL">全部床位状态</option><option value="UNCONFIRMED">仅待确认</option><option value="CONFIRMED">仅已确认</option></select>
        <button class="button secondary">查询</button>
      </form>
      <p v-if="loading" class="empty-state">正在加载在住信息…</p>
      <div v-else class="table-wrap">
        <table><thead><tr><th>学生</th><th>类别</th><th>寝室</th><th>宿舍属性</th><th>实际床位</th><th>来源</th><th>入住时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in items" :key="String(item.residency_id)">
            <td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }}</small></td>
            <td>{{ item.student_category === 'INTERNATIONAL' ? '国际生' : '国内生' }}</td>
            <td>{{ item.building_name }} {{ item.room_number }}<small>{{ item.floor_number }}层</small></td>
            <td>{{ scopeText(item.resident_scope) }}</td>
            <td><span class="status-chip compact" :class="{ warning: !item.bed_id }">{{ item.bed_id ? `${item.bed_code} · ${bedTypeLabel(item.bed_type)}` : '待确认' }}</span></td>
            <td>{{ methodText(item.assignment_method) }}</td><td>{{ new Date(String(item.assigned_at)).toLocaleString() }}</td>
            <td><div class="button-row compact-actions"><button class="button primary small" type="button" @click="openBedDialog(item)">{{ item.bed_id ? '调整床位' : '确认床位' }}</button><button class="button danger small" type="button" @click="requestEndResidency(item)">办理退宿</button></div></td>
          </tr></tbody></table>
      </div>
    </section>

    <AppModal :open="Boolean(ending)" size="default" :busy="endingResidency" busy-text="正在办理退宿并释放寝室容量…" @close="closeEndDialog">
      <div v-if="ending" class="residency-end-dialog">
        <span class="eyebrow">END RESIDENCY</span><h3>办理 {{ ending.student_name }} 退宿</h3><p>退宿会结束当前在住记录并释放寝室容量，请填写可审计的处理原因。</p>
        <label class="form-stack"><span>退宿原因</span><textarea v-model.trim="endReason" class="input" rows="4" maxlength="500" placeholder="例如：学生申请退宿、转宿舍或毕业离校"></textarea></label>
        <div class="button-row dialog-actions"><button class="button ghost" type="button" :disabled="endingResidency" @click="closeEndDialog">取消</button><button class="button danger" type="button" :disabled="!endReason.trim() || endingResidency" @click="endResidency">{{ endingResidency ? '正在办理退宿…' : '确认结束在住' }}</button></div>
      </div>
    </AppModal>

    </template>
    <AdminBedConfirmationView v-else embedded />

    <AppModal :open="Boolean(selected)" size="wide" :busy="saving" busy-text="正在保存床位修改…" @close="closeDialog"><div v-if="selected" class="bed-confirm-dialog"><header class="section-head split-title"><div><span class="eyebrow">BED MAPPING</span><h3>{{ selected.student_name }} · {{ selected.building_name }} {{ selected.room_number }}</h3><p>点击学生现实中实际使用的床位。已被其他在住学生确认的床位不可选择。</p></div><button class="button ghost small" @click="closeDialog">关闭</button></header>
        <div class="bed-card-grid"><button v-for="bed in availableBeds" :key="String(bed.id)" type="button" class="bed-card" :class="{ selected: selectedBedId === Number(bed.id) }" @click="selectedBedId = Number(bed.id)"><strong>{{ bed.bed_code }}</strong><span>{{ bedTypeLabel(bed.bed_type) }}</span><small>{{ selectedBedId === Number(bed.id) ? '已选择' : '可确认' }}</small></button></div>
        <label class="form-stack"><span>确认或调整原因</span><textarea v-model.trim="reason" class="input" required minlength="2" maxlength="500" rows="3" placeholder="例如：线下核对学生实际入住床位"></textarea></label>
        <div class="button-row dialog-actions"><button class="button ghost" type="button" @click="closeDialog">取消</button><button class="button primary" type="button" :disabled="!selectedBedId || reason.trim().length < 2 || saving" @click="saveBed">{{ saving ? '保存中…' : '确认实际床位' }}</button></div>
      </div></AppModal>
  </div>
</template>

<style scoped>
.residency-tabs{display:flex;gap:10px;flex-wrap:wrap}.residency-end-dialog{width:100%;padding:0}.residency-end-dialog p{color:var(--text-muted);line-height:1.7}.residency-end-dialog .dialog-actions{justify-content:flex-end;margin-top:16px}
.residency-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.residency-stats article{padding:18px}.residency-stats span{color:var(--text-muted)}.residency-stats strong{display:block;margin-top:6px;font-size:28px}.residency-filter{display:grid;grid-template-columns:minmax(220px,1fr) 220px 160px auto;gap:10px;margin-bottom:16px}.warning{background:#fff7ed;color:#c2410c}.bed-confirm-dialog{width:100%}.bed-card-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px;margin:16px 0}.bed-card{display:grid;gap:5px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface);text-align:left;color:inherit}.bed-card.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}.bed-card span,.bed-card small{color:var(--text-muted)}.dialog-actions{justify-content:flex-end;margin-top:16px}@media(max-width:800px){.residency-stats,.residency-filter{grid-template-columns:1fr}}
</style>
