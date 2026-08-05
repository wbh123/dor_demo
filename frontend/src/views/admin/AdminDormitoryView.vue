<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ImportWorkflowModal from '../../components/admin/ImportWorkflowModal.vue'
import RoomLayoutEditor from '../../components/admin/RoomLayoutEditor.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

type EducationScope = 'UNDERGRADUATE_ONLY' | 'GRADUATE_ONLY' | 'MIXED'
type ResidentScope = 'DOMESTIC_ONLY' | 'INTERNATIONAL_ONLY' | 'MIXED'
type BuildingGender = 'M' | 'F' | 'MIXED'
type RoomGender = 'M' | 'F'
type OperationalStatus = 'ENABLED' | 'DISABLED' | 'MAINTENANCE'

interface RoomEditForm {
  capacity: number
  gender: RoomGender
  educationLevelScope: EducationScope
  residentScope: ResidentScope
  operationalStatus: OperationalStatus
  remark: string
  reason: string
}
interface BuildingCreateForm {
  buildingCode: string
  buildingName: string
  gender: BuildingGender
  educationLevelScope: EducationScope
  residentScope: ResidentScope
  floorCount: number
  reason: string
}
interface RoomCreateForm {
  buildingId: number | undefined
  floorNumber: number
  roomNumber: string
  capacity: number
  gender: RoomGender
  educationLevelScope: EducationScope
  residentScope: ResidentScope
  operationalStatus: OperationalStatus
  remark: string
  reason: string
}

const ROOM_TYPE_LABELS: Record<string, string> = {
  FOUR_PERSON: '四人间', FIVE_PERSON: '五人间', SIX_PERSON: '六人间', OTHER: '其他',
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
const buildingDialogOpen = ref(false)
const roomDialogOpen = ref(false)
const loadingRooms = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const importOpen = ref(false)

const editForm = reactive<RoomEditForm>({ capacity: 4, gender: 'F', educationLevelScope: 'MIXED', residentScope: 'MIXED', operationalStatus: 'ENABLED', remark: '', reason: '' })
const buildingForm = reactive<BuildingCreateForm>({ buildingCode: '', buildingName: '', gender: 'MIXED', educationLevelScope: 'MIXED', residentScope: 'MIXED', floorCount: 6, reason: '' })
const roomForm = reactive<RoomCreateForm>({ buildingId: undefined, floorNumber: 1, roomNumber: '', capacity: 4, gender: 'F', educationLevelScope: 'MIXED', residentScope: 'MIXED', operationalStatus: 'ENABLED', remark: '', reason: '' })

const pagedRooms = computed(() => rooms.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const buildingTotals = computed(() => ({ buildings: buildings.value.length, rooms: buildings.value.reduce((sum, item) => sum + Number(item.room_count ?? 0), 0), beds: buildings.value.reduce((sum, item) => sum + Number(item.bed_count ?? 0), 0) }))
const selectedCreateBuilding = computed(() => buildings.value.find(item => Number(item.id) === roomForm.buildingId))

onMounted(async () => { await loadBuildings(); await loadRooms() })
watch([buildingId, gender], () => { page.value = 1; void loadRooms() })
watch(() => roomForm.buildingId, () => alignRoomCreateScopes())

async function loadBuildings() {
  try { const response = await api.get<ListSuccessResponse>('/api/v1/admin/buildings'); buildings.value = (response.data.data ?? []) as DataObject[] }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '宿舍楼加载失败' }
}
async function loadRooms() {
  loadingRooms.value = true; error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/rooms', { params: { buildingId: buildingId.value, gender: gender.value || undefined } })
    rooms.value = (response.data.data ?? []) as DataObject[]
    page.value = Math.min(page.value, Math.max(1, Math.ceil(rooms.value.length / pageSize.value)))
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '房间列表加载失败' }
  finally { loadingRooms.value = false }
}
async function downloadRoomTemplate(format: 'xlsx' | 'csv') {
  try {
    const response = await api.get('/api/v1/admin/import/rooms/template', { params: { format }, responseType: 'blob' })
    const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `宿舍导入模板.${format}`; anchor.click(); URL.revokeObjectURL(url)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '模板下载失败' }
}
function importCommitted() { importOpen.value = false; message.value = '宿舍文件已通过预检并完成导入。'; page.value = 1; void Promise.all([loadBuildings(), loadRooms()]) }
function openRoomEditor(room: DataObject) {
  selectedRoom.value = room
  editForm.capacity = physicalBedCount(room)
  editForm.gender = String(room.gender_restriction) as RoomGender
  editForm.educationLevelScope = String(room.education_level_scope ?? 'MIXED') as EducationScope
  editForm.residentScope = String(room.resident_scope ?? 'MIXED') as ResidentScope
  editForm.operationalStatus = String(room.operational_status) as OperationalStatus
  editForm.remark = String(room.remark ?? '')
  editForm.reason = ''; error.value = ''; message.value = ''
}
function openLayoutEditor(room: DataObject) { layoutRoom.value = room; error.value = ''; message.value = '' }
function closeRoomEditor() { selectedRoom.value = null; editForm.reason = '' }
function openBuildingCreate() { Object.assign(buildingForm, { buildingCode:'', buildingName:'', gender:'MIXED', educationLevelScope:'MIXED', residentScope:'MIXED', floorCount:6, reason:'' }); buildingDialogOpen.value = true }
function openRoomCreate() { Object.assign(roomForm, { buildingId:buildingId.value ?? (buildings.value[0] ? Number(buildings.value[0].id) : undefined), floorNumber:1, roomNumber:'', capacity:4, gender:'F', educationLevelScope:'MIXED', residentScope:'MIXED', operationalStatus:'ENABLED', remark:'', reason:'' }); alignRoomCreateScopes(); roomDialogOpen.value = true }
function alignRoomCreateScopes() {
  const building = selectedCreateBuilding.value
  if (!building) return
  if (String(building.gender_restriction) !== 'MIXED') roomForm.gender = String(building.gender_restriction) as RoomGender
  if (String(building.education_level_scope) !== 'MIXED') roomForm.educationLevelScope = String(building.education_level_scope) as EducationScope
  if (String(building.resident_scope) !== 'MIXED') roomForm.residentScope = String(building.resident_scope) as ResidentScope
}
async function createBuilding() {
  saving.value = true; error.value = ''
  try { await api.post<ObjectSuccessResponse>('/api/v1/admin/buildings', buildingForm); buildingDialogOpen.value = false; message.value = '宿舍楼已添加。'; await loadBuildings() }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '宿舍楼添加失败' }
  finally { saving.value = false }
}
async function createRoom() {
  saving.value = true; error.value = ''
  try { await api.post<ObjectSuccessResponse>('/api/v1/admin/rooms', roomForm); roomDialogOpen.value = false; message.value = '宿舍已添加，并已生成默认床位。'; await Promise.all([loadBuildings(), loadRooms()]) }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '宿舍添加失败' }
  finally { saving.value = false }
}
async function saveRoom() {
  if (!selectedRoom.value) return
  const capacity = physicalBedCount(selectedRoom.value)
  if (capacity < 1) { error.value = '该房间尚未配置床位，暂不能保存房间属性。'; return }
  saving.value = true; error.value = ''; message.value = ''
  try {
    await api.put(`/api/v1/admin/rooms/${selectedRoom.value.id}`, { ...editForm, capacity, remark: editForm.remark.trim(), reason: editForm.reason.trim() })
    message.value = '房间属性已更新。'; closeRoomEditor(); await Promise.all([loadBuildings(), loadRooms()])
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '房间更新失败' }
  finally { saving.value = false }
}
function optionAllowed(building: DataObject | null, field: string, value: string) { const scope = String(building?.[field] ?? 'MIXED'); return scope === 'MIXED' || scope === value }
function physicalBedCount(room: DataObject | null) { return Number(room?.bed_count ?? room?.capacity ?? 0) }
function enabledBedCount(room: DataObject | null) { return Number(room?.enabled_bed_count ?? 0) }
function roomLabel(room: DataObject) { return `${String(room.building_name)} ${String(room.room_number)}` }
function roomType(value: unknown) { return ROOM_TYPE_LABELS[String(value)] ?? value }
function statusText(value: unknown) { return ({ ENABLED:'启用', DISABLED:'禁用', MAINTENANCE:'维护' } as Record<string,string>)[String(value)] ?? value }
function residentScopeText(value: unknown) { return ({ DOMESTIC_ONLY:'国内生', INTERNATIONAL_ONLY:'国际生', MIXED:'国内/国际混合' } as Record<string,string>)[String(value)] ?? '国内/国际混合' }
function educationScopeText(value: unknown) { return ({ UNDERGRADUATE_ONLY:'本科生', GRADUATE_ONLY:'研究生', MIXED:'培养层次混合' } as Record<string,string>)[String(value)] ?? '培养层次混合' }
function genderText(value: unknown) { return ({ M:'男生', F:'女生', MIXED:'性别混合' } as Record<string,string>)[String(value)] ?? '性别混合' }
function bedMappingText(room: DataObject) { const count = Number(room.unconfirmed_bed_count ?? 0); return count > 0 ? `${count}人待确认床位` : '床位映射完整' }
</script>

<template>
  <div class="content-column">
    <div class="page-title dormitory-title">
      <h2>宿舍资源</h2>
      <div class="button-row wrap">
        <button class="button primary" type="button" @click="openBuildingCreate">添加宿舍楼</button>
        <button class="button secondary" type="button" :disabled="!buildings.length" @click="openRoomCreate">添加宿舍</button>
        <button class="button ghost" @click="downloadRoomTemplate('xlsx')">下载Excel模板</button>
        <button class="button ghost" @click="downloadRoomTemplate('csv')">下载CSV模板</button>
        <button class="button ghost" @click="importOpen = true">批量导入</button>
        <button class="button ghost" type="button" @click="router.push('/admin/residencies')">在住管理</button>
      </div>
    </div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel building-overview-panel">
      <div class="section-head compact-section-head"><h3>全部楼栋</h3><span class="compact-count">{{ buildingTotals.buildings }}栋 · {{ buildingTotals.rooms }}间 · {{ buildingTotals.beds }}床</span></div>
      <div class="table-wrap building-compact-table">
        <table><thead><tr><th>楼栋</th><th>适用范围</th><th>资源</th><th>状态</th></tr></thead><tbody>
          <tr v-for="building in buildings" :key="String(building.id)">
            <td><strong>{{ building.building_name }}</strong><small>{{ building.building_code }} · {{ building.campus_name }}</small></td>
            <td><div class="tag-row"><span class="status-chip compact">{{ genderText(building.gender_restriction) }}</span><span class="status-chip compact">{{ educationScopeText(building.education_level_scope) }}</span><span class="status-chip compact">{{ residentScopeText(building.resident_scope) }}</span></div></td>
            <td>{{ building.room_count }}间 · {{ building.bed_count }}床</td><td>{{ building.enabled ? '启用' : '停用' }}</td>
          </tr></tbody></table><p v-if="!buildings.length" class="empty-state">暂无楼栋信息。</p>
      </div>
    </section>

    <section class="panel">
      <div class="section-head split-title compact-section-head"><h3>宿舍列表</h3><div class="inline-form"><select v-model="buildingId" class="input"><option :value="undefined">全部楼栋</option><option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option></select><select v-model="gender" class="input"><option value="">全部性别</option><option value="M">男寝</option><option value="F">女寝</option></select></div></div>
      <p v-if="loadingRooms" class="empty-state">正在刷新宿舍列表…</p>
      <div v-else class="table-wrap"><table><thead><tr><th>楼栋/宿舍</th><th>房型</th><th>性别</th><th>培养层次</th><th>学生类别</th><th>在住容量</th><th>床位状态</th><th>运行</th><th /></tr></thead><tbody>
        <tr v-for="room in pagedRooms" :key="String(room.id)"><td><strong>{{ room.building_name }} {{ room.room_number }}</strong><small>{{ room.floor_number }}层</small></td><td>{{ roomType(room.room_type) }}</td><td>{{ genderText(room.gender_restriction) }}</td><td>{{ educationScopeText(room.education_level_scope) }}</td><td>{{ residentScopeText(room.resident_scope) }}</td><td><strong>{{ room.active_resident_count ?? 0 }}/{{ room.capacity }}</strong><small>剩余{{ room.remaining_capacity ?? room.capacity }}人</small></td><td><strong>{{ enabledBedCount(room) }}/{{ physicalBedCount(room) }}</strong><small :class="{ warning: Number(room.unconfirmed_bed_count ?? 0) > 0 }">{{ bedMappingText(room) }}</small></td><td><span class="status-chip compact">{{ statusText(room.operational_status) }}</span></td><td><div class="button-row compact-actions"><button class="button ghost small" @click="openRoomEditor(room)">属性</button><button v-if="canViewRoomLayout" class="button ghost small" @click="openLayoutEditor(room)">布局</button></div></td></tr>
      </tbody></table></div>
      <PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="rooms.length" />
    </section>

    <ImportWorkflowModal :open="importOpen" import-type="ROOM" title="批量导入宿舍" @close="importOpen = false" @committed="importCommitted" />

    <AppModal :open="buildingDialogOpen" title="添加宿舍楼" size="default" @close="buildingDialogOpen = false">
      <form id="building-create-form" class="form-grid two-column" @submit.prevent="createBuilding"><label><span>楼栋代码</span><input v-model.trim="buildingForm.buildingCode" class="input" required maxlength="32" /></label><label><span>楼栋名称</span><input v-model.trim="buildingForm.buildingName" class="input" required maxlength="128" /></label><label><span>性别范围</span><select v-model="buildingForm.gender" class="input"><option value="M">男生</option><option value="F">女生</option><option value="MIXED">混合</option></select></label><label><span>培养层次</span><select v-model="buildingForm.educationLevelScope" class="input"><option value="UNDERGRADUATE_ONLY">本科生</option><option value="GRADUATE_ONLY">研究生</option><option value="MIXED">混合</option></select></label><label><span>学生类别</span><select v-model="buildingForm.residentScope" class="input"><option value="DOMESTIC_ONLY">国内生</option><option value="INTERNATIONAL_ONLY">国际生</option><option value="MIXED">混合</option></select></label><label><span>楼层数</span><input v-model.number="buildingForm.floorCount" class="input" type="number" min="1" max="50" required /></label><label class="span-2"><span>添加原因</span><input v-model.trim="buildingForm.reason" class="input" required maxlength="500" /></label></form>
      <template #footer><button class="button ghost" @click="buildingDialogOpen = false">取消</button><button class="button primary" form="building-create-form" :disabled="saving">{{ saving ? '添加中…' : '添加宿舍楼' }}</button></template>
    </AppModal>

    <AppModal :open="roomDialogOpen" title="添加宿舍" size="default" @close="roomDialogOpen = false">
      <form id="room-create-form" class="form-grid two-column" @submit.prevent="createRoom"><label><span>所属楼栋</span><select v-model="roomForm.buildingId" class="input" required><option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option></select></label><label><span>楼层</span><input v-model.number="roomForm.floorNumber" class="input" type="number" min="1" required /></label><label><span>宿舍号</span><input v-model.trim="roomForm.roomNumber" class="input" required maxlength="32" /></label><label><span>初始床位数</span><input v-model.number="roomForm.capacity" class="input" type="number" min="1" max="8" required /></label><label><span>性别</span><select v-model="roomForm.gender" class="input"><option value="M" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'gender_restriction','M')">男生</option><option value="F" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'gender_restriction','F')">女生</option></select></label><label><span>培养层次</span><select v-model="roomForm.educationLevelScope" class="input"><option value="UNDERGRADUATE_ONLY" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'education_level_scope','UNDERGRADUATE_ONLY')">本科生</option><option value="GRADUATE_ONLY" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'education_level_scope','GRADUATE_ONLY')">研究生</option><option value="MIXED" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'education_level_scope','MIXED')">混合</option></select></label><label><span>学生类别</span><select v-model="roomForm.residentScope" class="input"><option value="DOMESTIC_ONLY" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'resident_scope','DOMESTIC_ONLY')">国内生</option><option value="INTERNATIONAL_ONLY" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'resident_scope','INTERNATIONAL_ONLY')">国际生</option><option value="MIXED" :disabled="!optionAllowed(selectedCreateBuilding ?? null,'resident_scope','MIXED')">混合</option></select></label><label><span>运行状态</span><select v-model="roomForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select></label><label class="span-2"><span>备注</span><input v-model.trim="roomForm.remark" class="input" maxlength="500" /></label><label class="span-2"><span>添加原因</span><input v-model.trim="roomForm.reason" class="input" required maxlength="500" /></label></form>
      <template #footer><button class="button ghost" @click="roomDialogOpen = false">取消</button><button class="button primary" form="room-create-form" :disabled="saving">{{ saving ? '添加中…' : '添加宿舍' }}</button></template>
    </AppModal>

    <AppModal :open="Boolean(selectedRoom)" :title="selectedRoom ? `${selectedRoom.building_name} ${selectedRoom.room_number}` : '编辑宿舍'" size="wide" @close="closeRoomEditor">
      <form v-if="selectedRoom" id="room-edit-form" class="form-grid two-column" @submit.prevent="saveRoom"><label><span>规划容量</span><input v-model.number="editForm.capacity" class="input" type="number" readonly /></label><label><span>性别</span><select v-model="editForm.gender" class="input"><option value="M" :disabled="!optionAllowed(selectedRoom,'building_gender_restriction','M')">男生</option><option value="F" :disabled="!optionAllowed(selectedRoom,'building_gender_restriction','F')">女生</option></select></label><label><span>培养层次</span><select v-model="editForm.educationLevelScope" class="input"><option value="UNDERGRADUATE_ONLY" :disabled="!optionAllowed(selectedRoom,'building_education_level_scope','UNDERGRADUATE_ONLY')">本科生</option><option value="GRADUATE_ONLY" :disabled="!optionAllowed(selectedRoom,'building_education_level_scope','GRADUATE_ONLY')">研究生</option><option value="MIXED" :disabled="!optionAllowed(selectedRoom,'building_education_level_scope','MIXED')">混合</option></select></label><label><span>学生类别</span><select v-model="editForm.residentScope" class="input"><option value="DOMESTIC_ONLY" :disabled="!optionAllowed(selectedRoom,'building_resident_scope','DOMESTIC_ONLY')">国内生</option><option value="INTERNATIONAL_ONLY" :disabled="!optionAllowed(selectedRoom,'building_resident_scope','INTERNATIONAL_ONLY')">国际生</option><option value="MIXED" :disabled="!optionAllowed(selectedRoom,'building_resident_scope','MIXED')">混合</option></select></label><label><span>运行状态</span><select v-model="editForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select></label><label><span>备注</span><input v-model="editForm.remark" class="input" maxlength="500" /></label><label class="span-2"><span>修改原因</span><input v-model="editForm.reason" class="input" required maxlength="500" /></label></form>
      <template #footer><button class="button ghost" @click="closeRoomEditor">取消</button><button class="button primary" form="room-edit-form" :disabled="saving">{{ saving ? '保存中…' : '保存修改' }}</button></template>
    </AppModal>

    <RoomLayoutEditor v-if="layoutRoom" :room-id="Number(layoutRoom.id)" :room-label="roomLabel(layoutRoom)" @close="layoutRoom = null" @saved="loadRooms" />
  </div>
</template>

<style scoped>
.dormitory-title{display:flex;justify-content:space-between;align-items:center;gap:16px}.dormitory-title h2{margin:0}.compact-section-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}.compact-section-head h3{margin:0}.compact-count{color:var(--muted);font-size:13px}.building-compact-table table{min-width:720px}.building-compact-table td{padding-top:10px;padding-bottom:10px}.tag-row{display:flex;flex-wrap:wrap;gap:5px}.inline-form{display:flex;gap:8px;flex-wrap:wrap}.inline-form .input{min-width:150px}.span-2{grid-column:1/-1}@media(max-width:760px){.dormitory-title{align-items:flex-start;flex-direction:column}.compact-section-head{align-items:flex-start;flex-direction:column}.form-grid.two-column{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
