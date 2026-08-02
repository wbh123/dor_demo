<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import RoomLayoutEditor from '../../components/admin/RoomLayoutEditor.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse } from '../../api/types'

interface RoomEditForm {
  capacity: number
  gender: 'M' | 'F'
  residentScope: 'DOMESTIC_ONLY' | 'INTERNATIONAL_ONLY' | 'MIXED'
  operationalStatus: 'ENABLED' | 'DISABLED' | 'MAINTENANCE'
  remark: string
  reason: string
}

const ROOM_TYPE_LABELS: Record<string, string> = {
  FOUR_PERSON: '四人间',
  FIVE_PERSON: '五人间',
  SIX_PERSON: '六人间',
  OTHER: '其他',
}
const SCOPE_LABELS: Record<string, string> = {
  DOMESTIC_ONLY: '国内生宿舍',
  INTERNATIONAL_ONLY: '国际生宿舍',
  MIXED: '混住宿舍',
}

const router = useRouter()
const buildings = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const buildingId = ref<number | undefined>()
const gender = ref('')
const selectedRoom = ref<DataObject | null>(null)
const layoutRoom = ref<DataObject | null>(null)
const loadingRooms = ref(false)
const error = ref('')
const message = ref('')
const editForm = reactive<RoomEditForm>({
  capacity: 4,
  gender: 'F',
  residentScope: 'MIXED',
  operationalStatus: 'ENABLED',
  remark: '',
  reason: '',
})

onMounted(async () => {
  await loadBuildings()
  await loadRooms()
})

watch([buildingId, gender], () => void loadRooms())

async function loadBuildings() {
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/buildings')
    buildings.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '宿舍楼加载失败'
  }
}

async function loadRooms() {
  loadingRooms.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/rooms', {
      params: { buildingId: buildingId.value, gender: gender.value || undefined },
    })
    rooms.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间列表加载失败'
  } finally {
    loadingRooms.value = false
  }
}

function openRoomEditor(room: DataObject) {
  selectedRoom.value = room
  editForm.capacity = physicalBedCount(room)
  editForm.gender = String(room.gender_restriction) as RoomEditForm['gender']
  editForm.residentScope = String(room.resident_scope ?? 'MIXED') as RoomEditForm['residentScope']
  editForm.operationalStatus = String(room.operational_status) as RoomEditForm['operationalStatus']
  editForm.remark = String(room.remark ?? '')
  editForm.reason = ''
  error.value = ''
  message.value = ''
}

function openLayoutEditor(room: DataObject) {
  layoutRoom.value = room
  error.value = ''
  message.value = ''
}

function closeRoomEditor() {
  selectedRoom.value = null
  editForm.reason = ''
}

async function saveRoom() {
  if (!selectedRoom.value) return
  const capacity = physicalBedCount(selectedRoom.value)
  if (capacity < 1) {
    error.value = '该房间尚未配置床位，暂不能保存房间属性。'
    return
  }
  error.value = ''
  message.value = ''
  try {
    await api.put(`/api/v1/admin/rooms/${selectedRoom.value.id}`, {
      ...editForm,
      capacity,
      remark: editForm.remark.trim(),
      reason: editForm.reason.trim(),
    })
    message.value = '房间属性已更新。'
    closeRoomEditor()
    await Promise.all([loadBuildings(), loadRooms()])
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间更新失败'
  }
}

function physicalBedCount(room: DataObject | null) {
  return Number(room?.bed_count ?? room?.capacity ?? 0)
}
function enabledBedCount(room: DataObject | null) {
  return Number(room?.enabled_bed_count ?? 0)
}
function unavailableBedCount(room: DataObject | null) {
  const explicit = Number(room?.disabled_bed_count ?? 0) + Number(room?.maintenance_bed_count ?? 0)
  return explicit > 0 ? explicit : Math.max(0, physicalBedCount(room) - enabledBedCount(room))
}
function roomLabel(room: DataObject) { return `${String(room.building_name)} ${String(room.room_number)}` }
function roomType(value: unknown) { return ROOM_TYPE_LABELS[String(value)] ?? value }
function statusText(value: unknown) { return { ENABLED: '启用', DISABLED: '禁用', MAINTENANCE: '维护' }[String(value)] ?? value }
function scopeText(value: unknown) { return SCOPE_LABELS[String(value)] ?? '混住宿舍' }
function bedMappingText(room: DataObject) {
  const count = Number(room.unconfirmed_bed_count ?? 0)
  return count > 0 ? `${count}人待确认床位` : '床位映射完整'
}
</script>

<template>
  <div class="content-column">
    <div class="page-title dormitory-title">
      <div>
        <span class="eyebrow">DORMITORY RESOURCES</span>
        <h2>宿舍、房间与床位</h2>
        <p>图形化维护性别、国内生/国际生属性、运行状态、容量和床位布局。存在未确认床位的在住学生时，该寝室不能开放选床模式。</p>
      </div>
      <button class="button secondary" type="button" @click="router.push('/admin/residencies')">在住与床位确认</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="stat-grid compact-grid">
      <article v-for="building in buildings" :key="String(building.id)" class="panel building-card">
        <span class="eyebrow">{{ building.building_code }}</span><h3>{{ building.building_name }}</h3>
        <p>{{ building.gender_restriction === 'M' ? '男生宿舍' : building.gender_restriction === 'F' ? '女生宿舍' : '按房间设置性别' }}</p>
        <div class="room-facts"><span>{{ building.room_count }}间</span><span>{{ building.bed_count }}床</span></div>
      </article>
    </div>

    <section class="panel">
      <div class="section-head split-title">
        <div><span class="eyebrow">ROOMS</span><h3>房间列表</h3></div>
        <div class="inline-form" aria-label="房间筛选">
          <select v-model="buildingId" class="input"><option :value="undefined">全部楼栋</option><option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option></select>
          <select v-model="gender" class="input"><option value="">全部性别</option><option value="M">男寝</option><option value="F">女寝</option></select>
        </div>
      </div>
      <p v-if="loadingRooms" class="empty-state">正在刷新房间列表…</p>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>楼栋/房间</th><th>房型</th><th>性别</th><th>学生类别</th><th>在住容量</th><th>床位状态</th><th>运行</th><th /></tr></thead>
          <tbody>
            <tr v-for="room in rooms" :key="String(room.id)">
              <td><strong>{{ room.building_name }} {{ room.room_number }}</strong><small>{{ room.floor_number }}层</small></td>
              <td>{{ roomType(room.room_type) }}</td><td>{{ room.gender_restriction === 'M' ? '男寝' : '女寝' }}</td>
              <td><span class="status-chip compact">{{ scopeText(room.resident_scope) }}</span></td>
              <td><strong>{{ room.active_resident_count ?? 0 }}/{{ room.capacity }}</strong><small>剩余{{ room.remaining_capacity ?? room.capacity }}人</small></td>
              <td><strong>{{ enabledBedCount(room) }}/{{ physicalBedCount(room) }}</strong><small :class="{ warning: Number(room.unconfirmed_bed_count ?? 0) > 0 }">{{ bedMappingText(room) }}</small></td>
              <td><span class="status-chip compact">{{ statusText(room.operational_status) }}</span></td>
              <td><div class="button-row compact-actions"><button class="button ghost small" type="button" @click="openRoomEditor(room)">属性</button><button class="button ghost small" type="button" @click="openLayoutEditor(room)">布局</button></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="selectedRoom" class="modal-overlay" @click.self="closeRoomEditor">
      <section class="modal-dialog room-editor-dialog" role="dialog" aria-modal="true" aria-labelledby="room-editor-title">
        <div class="modal-head"><div><span class="eyebrow">EDIT ROOM</span><h3 id="room-editor-title">{{ selectedRoom.building_name }} {{ selectedRoom.room_number }}</h3><p>宿舍属性变更会校验当前在住学生，避免把国际生所在寝室改为国内生专用等冲突。</p></div><button class="modal-close" type="button" @click="closeRoomEditor">×</button></div>
        <div class="room-editor-summary">
          <article><span>当前在住</span><strong>{{ selectedRoom.active_resident_count ?? 0 }}</strong><small>剩余{{ selectedRoom.remaining_capacity ?? 0 }}人</small></article>
          <article><span>已确认床位</span><strong>{{ selectedRoom.confirmed_bed_count ?? 0 }}</strong><small>现实床位已记录</small></article>
          <article><span>待确认床位</span><strong>{{ selectedRoom.unconfirmed_bed_count ?? 0 }}</strong><small>大于0时禁止选床模式</small></article>
        </div>
        <form class="form-grid two-column room-editor-form" @submit.prevent="saveRoom">
          <label><span>规划容量</span><input v-model.number="editForm.capacity" class="input" type="number" readonly /></label>
          <label><span>房间性别</span><select v-model="editForm.gender" class="input"><option value="M">男寝</option><option value="F">女寝</option></select></label>
          <div class="span-2"><span class="field-label">学生类别属性</span><div class="scope-segments"><button v-for="option in [{value:'DOMESTIC_ONLY',label:'国内生宿舍'},{value:'INTERNATIONAL_ONLY',label:'国际生宿舍'},{value:'MIXED',label:'混住宿舍'}]" :key="option.value" type="button" :class="{ active: editForm.residentScope === option.value }" @click="editForm.residentScope = option.value as RoomEditForm['residentScope']">{{ option.label }}</button></div></div>
          <label><span>运行状态</span><select v-model="editForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select></label>
          <label><span>备注</span><input v-model="editForm.remark" class="input" maxlength="500" /></label>
          <label class="span-2"><span>修改原因</span><input v-model="editForm.reason" class="input" required maxlength="500" placeholder="必填，将写入审计" /></label>
          <div class="button-row span-2 room-editor-actions"><button type="button" class="button ghost" @click="closeRoomEditor">取消</button><button class="button primary">保存修改</button></div>
        </form>
      </section>
    </div>

    <RoomLayoutEditor v-if="layoutRoom" :room-id="Number(layoutRoom.id)" :room-label="roomLabel(layoutRoom)" @close="layoutRoom = null" @saved="loadRooms" />
  </div>
</template>

<style scoped>
.dormitory-title { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
.warning { color:#b45309; font-weight:700; }
.field-label { display:block; margin-bottom:7px; }
.scope-segments { display:grid; grid-template-columns:repeat(3,1fr); padding:4px; border:1px solid var(--border); border-radius:12px; background:var(--surface-soft); }
.scope-segments button { border:0; border-radius:9px; padding:10px 8px; background:transparent; color:var(--text-muted); }
.scope-segments button.active { background:var(--primary); color:white; }
@media(max-width:720px){.dormitory-title{flex-direction:column}.scope-segments{grid-template-columns:1fr}}
</style>
