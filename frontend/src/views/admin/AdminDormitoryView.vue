<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ImportWorkflowModal from '../../components/admin/ImportWorkflowModal.vue'
import RoomLayoutEditor from '../../components/admin/RoomLayoutEditor.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import { api } from '../../api/client'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
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
  FOUR_PERSON: '四人间', FIVE_PERSON: '五人间', SIX_PERSON: '六人间', OTHER: '其他',
}
const SCOPE_LABELS: Record<string, string> = {
  DOMESTIC_ONLY: '国内生宿舍', INTERNATIONAL_ONLY: '国际生宿舍', MIXED: '混住宿舍',
}

const router = useRouter()
const { hasFeature } = useFeatureAccess()
const canViewRoomLayout = computed(() => hasFeature('P2_ROOM_LAYOUT_VIEW') || hasFeature('P2_ROOM_LAYOUT_UPDATE'))
const buildings = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const buildingId = ref<number | undefined>()
const gender = ref('')
const page = ref(1)
const pageSize = ref(10)
const selectedRoom = ref<DataObject | null>(null)
const layoutRoom = ref<DataObject | null>(null)
const loadingRooms = ref(false)
const error = ref('')
const message = ref('')
const importOpen = ref(false)
const editForm = reactive<RoomEditForm>({
  capacity: 4, gender: 'F', residentScope: 'MIXED',
  operationalStatus: 'ENABLED', remark: '', reason: '',
})

const pagedRooms = computed(() => rooms.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const buildingTotals = computed(() => ({
  buildings: buildings.value.length,
  rooms: buildings.value.reduce((sum, item) => sum + Number(item.room_count ?? 0), 0),
  beds: buildings.value.reduce((sum, item) => sum + Number(item.bed_count ?? 0), 0),
}))

onMounted(async () => {
  await loadBuildings()
  await loadRooms()
})

watch([buildingId, gender], () => {
  page.value = 1
  void loadRooms()
})

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
    const maxPage = Math.max(1, Math.ceil(rooms.value.length / pageSize.value))
    if (page.value > maxPage) page.value = maxPage
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间列表加载失败'
  } finally {
    loadingRooms.value = false
  }
}

async function downloadRoomTemplate(format: 'xlsx' | 'csv') {
  try {
    const response = await api.get('/api/v1/admin/import/rooms/template', { params: { format }, responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `宿舍导入模板.${format}`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '模板下载失败'
  }
}

function importCommitted() {
  importOpen.value = false
  message.value = '宿舍文件已通过预检并完成导入。'
  page.value = 1
  void Promise.all([loadBuildings(), loadRooms()])
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

function physicalBedCount(room: DataObject | null) { return Number(room?.bed_count ?? room?.capacity ?? 0) }
function enabledBedCount(room: DataObject | null) { return Number(room?.enabled_bed_count ?? 0) }
function roomLabel(room: DataObject) { return `${String(room.building_name)} ${String(room.room_number)}` }
function roomType(value: unknown) { return ROOM_TYPE_LABELS[String(value)] ?? value }
function statusText(value: unknown) { return ({ ENABLED: '启用', DISABLED: '禁用', MAINTENANCE: '维护' } as Record<string, string>)[String(value)] ?? value }
function scopeText(value: unknown) { return SCOPE_LABELS[String(value)] ?? '混住宿舍' }
function genderText(value: unknown) { return String(value) === 'M' ? '男生宿舍' : String(value) === 'F' ? '女生宿舍' : '按房间设置性别' }
function bedMappingText(room: DataObject) {
  const count = Number(room.unconfirmed_bed_count ?? 0)
  return count > 0 ? `${count}人待确认床位` : '床位映射完整'
}
</script>

<template>
  <div class="content-column">
    <div class="page-title dormitory-title">
      <div><span class="eyebrow">宿舍资源</span><h2>宿舍、房间与床位</h2><p>统一维护楼栋、房间属性、床位布局和批量宿舍数据。</p></div>
      <div class="button-row wrap"><button class="button ghost" @click="downloadRoomTemplate('xlsx')">下载Excel模板</button><button class="button ghost" @click="downloadRoomTemplate('csv')">下载CSV模板</button><button class="button primary" @click="importOpen = true">批量导入</button><button class="button secondary" type="button" @click="router.push('/admin/residencies')">在住与床位确认</button></div>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel building-overview-panel">
      <div class="section-head split-title compact-section-head"><div><span class="eyebrow">宿舍概况</span><h3>全部楼栋</h3><p>{{ buildingTotals.buildings }} 栋楼、{{ buildingTotals.rooms }} 间房、{{ buildingTotals.beds }} 个床位</p></div></div>
      <div class="building-summary-list">
        <article v-for="building in buildings" :key="String(building.id)">
          <div><strong>{{ building.building_name }}</strong><small>{{ building.building_code }} · {{ genderText(building.gender_restriction) }}</small></div>
          <div class="room-facts"><span>{{ building.room_count }}间</span><span>{{ building.bed_count }}床</span></div>
        </article>
        <p v-if="!buildings.length" class="empty-state">暂无楼栋信息。</p>
      </div>
    </section>

    <section class="panel">
      <div class="section-head split-title compact-section-head">
        <div><span class="eyebrow">房间管理</span><h3>房间列表</h3><p>筛选结果共 {{ rooms.length }} 间</p></div>
        <div class="inline-form" aria-label="房间筛选"><select v-model="buildingId" class="input"><option :value="undefined">全部楼栋</option><option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option></select><select v-model="gender" class="input"><option value="">全部性别</option><option value="M">男寝</option><option value="F">女寝</option></select></div>
      </div>
      <p v-if="loadingRooms" class="empty-state">正在刷新房间列表…</p>
      <div v-else class="table-wrap">
        <table><thead><tr><th>楼栋/房间</th><th>房型</th><th>性别</th><th>学生类别</th><th>在住容量</th><th>床位状态</th><th>运行</th><th /></tr></thead><tbody><tr v-for="room in pagedRooms" :key="String(room.id)"><td><strong>{{ room.building_name }} {{ room.room_number }}</strong><small>{{ room.floor_number }}层</small></td><td>{{ roomType(room.room_type) }}</td><td>{{ room.gender_restriction === 'M' ? '男寝' : '女寝' }}</td><td><span class="status-chip compact">{{ scopeText(room.resident_scope) }}</span></td><td><strong>{{ room.active_resident_count ?? 0 }}/{{ room.capacity }}</strong><small>剩余{{ room.remaining_capacity ?? room.capacity }}人</small></td><td><strong>{{ enabledBedCount(room) }}/{{ physicalBedCount(room) }}</strong><small :class="{ warning: Number(room.unconfirmed_bed_count ?? 0) > 0 }">{{ bedMappingText(room) }}</small></td><td><span class="status-chip compact">{{ statusText(room.operational_status) }}</span></td><td><div class="button-row compact-actions"><button class="button ghost small" type="button" @click="openRoomEditor(room)">属性</button><button v-if="canViewRoomLayout" class="button ghost small" type="button" @click="openLayoutEditor(room)">布局</button></div></td></tr></tbody></table>
      </div>
      <PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="rooms.length" />
    </section>

    <ImportWorkflowModal :open="importOpen" import-type="ROOM" title="批量导入宿舍" @close="importOpen = false" @committed="importCommitted" />

    <div v-if="selectedRoom" class="modal-overlay" @click.self="closeRoomEditor">
      <section class="modal-dialog room-editor-dialog" role="dialog" aria-modal="true" aria-labelledby="room-editor-title">
        <div class="modal-head"><div><span class="eyebrow">编辑房间</span><h3 id="room-editor-title">{{ selectedRoom.building_name }} {{ selectedRoom.room_number }}</h3><p>属性变更会校验当前在住学生，避免产生性别或学生类别冲突。</p></div><button class="modal-close" type="button" @click="closeRoomEditor">×</button></div>
        <div class="room-editor-summary"><article><span>当前在住</span><strong>{{ selectedRoom.active_resident_count ?? 0 }}</strong><small>剩余{{ selectedRoom.remaining_capacity ?? 0 }}人</small></article><article><span>已确认床位</span><strong>{{ selectedRoom.confirmed_bed_count ?? 0 }}</strong><small>现实床位已记录</small></article><article><span>待确认床位</span><strong>{{ selectedRoom.unconfirmed_bed_count ?? 0 }}</strong><small>大于0时禁止选床模式</small></article></div>
        <form class="form-grid two-column room-editor-form" @submit.prevent="saveRoom">
          <label><span>规划容量</span><input v-model.number="editForm.capacity" class="input" type="number" readonly /></label><label><span>房间性别</span><select v-model="editForm.gender" class="input"><option value="M">男寝</option><option value="F">女寝</option></select></label>
          <div class="span-2"><span class="field-label">学生类别属性</span><div class="scope-segments"><button v-for="option in [{value:'DOMESTIC_ONLY',label:'国内生宿舍'},{value:'INTERNATIONAL_ONLY',label:'国际生宿舍'},{value:'MIXED',label:'混住宿舍'}]" :key="option.value" type="button" :class="{ active: editForm.residentScope === option.value }" @click="editForm.residentScope = option.value as RoomEditForm['residentScope']">{{ option.label }}</button></div></div>
          <label><span>运行状态</span><select v-model="editForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select></label><label><span>备注</span><input v-model="editForm.remark" class="input" maxlength="500" /></label><label class="span-2"><span>修改原因</span><input v-model="editForm.reason" class="input" required maxlength="500" placeholder="必填，将写入审计" /></label><div class="button-row span-2 room-editor-actions"><button type="button" class="button ghost" @click="closeRoomEditor">取消</button><button class="button primary">保存修改</button></div>
        </form>
      </section>
    </div>

    <RoomLayoutEditor v-if="layoutRoom" :room-id="Number(layoutRoom.id)" :room-label="roomLabel(layoutRoom)" @close="layoutRoom = null" @saved="loadRooms" />
  </div>
</template>

<style scoped>
.dormitory-title{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.compact-section-head{margin-bottom:12px}.building-overview-panel{display:grid;gap:10px}.building-summary-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:9px}.building-summary-list article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border:1px solid var(--line);border-radius:13px;background:var(--soft)}.building-summary-list article div:first-child{display:grid;gap:3px}.building-summary-list small{color:var(--muted)}.room-facts{display:flex;gap:7px;flex-wrap:wrap}.room-facts span{padding:5px 8px;border-radius:999px;background:#fff;color:var(--muted);font-size:12px}.warning{color:#b45309;font-weight:700}.field-label{display:block;margin-bottom:8px}.scope-segments{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;padding:6px;border-radius:14px;background:var(--soft)}.scope-segments button{padding:9px;border:0;border-radius:10px;background:transparent;cursor:pointer}.scope-segments button.active{background:var(--primary);color:white}.room-editor-dialog{width:min(820px,calc(100vw - 40px));padding:24px}.room-editor-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:16px 0}.room-editor-summary article{padding:13px;border:1px solid var(--line);border-radius:13px;background:var(--soft)}.room-editor-summary span,.room-editor-summary small{display:block;color:var(--muted)}.room-editor-summary strong{display:block;margin:5px 0;font-size:24px}.room-editor-actions{justify-content:flex-end}@media(max-width:720px){.dormitory-title{flex-direction:column}.scope-segments,.room-editor-summary{grid-template-columns:1fr}.building-summary-list{grid-template-columns:1fr}}
</style>
