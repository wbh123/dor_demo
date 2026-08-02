<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import RoomLayoutEditor from '../../components/admin/RoomLayoutEditor.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, RoomRequest } from '../../api/types'

interface RoomTypeOption {
  value: RoomRequest['roomType']
  label: string
}

const ROOM_TYPE_LABELS: Record<string, string> = {
  FOUR_PERSON: '四人间',
  FIVE_PERSON: '五人间',
  SIX_PERSON: '六人间',
  OTHER: '其他',
}

const buildings = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const buildingId = ref<number | undefined>()
const gender = ref('')
const selectedRoom = ref<DataObject | null>(null)
const layoutRoom = ref<DataObject | null>(null)
const loadingRooms = ref(false)
const error = ref('')
const message = ref('')
const editForm = reactive<RoomRequest>({
  roomType: 'FOUR_PERSON',
  capacity: 4,
  gender: 'F',
  operationalStatus: 'ENABLED',
  remark: '',
  reason: '',
})

onMounted(async () => {
  await loadBuildings()
  await loadRooms()
})

watch([buildingId, gender], () => {
  void loadRooms()
})

async function loadBuildings() {
  error.value = ''
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
  const options = roomTypeOptions(room)
  const currentType = String(room.room_type) as RoomRequest['roomType']
  editForm.roomType = options.some((option) => option.value === currentType)
    ? currentType
    : 'OTHER'
  editForm.capacity = physicalBedCount(room)
  editForm.gender = String(room.gender_restriction) as RoomRequest['gender']
  editForm.operationalStatus = String(room.operational_status) as RoomRequest['operationalStatus']
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
      capacity: physicalBedCount(selectedRoom.value),
      remark: String(editForm.remark ?? '').trim(),
      reason: String(editForm.reason ?? '').trim(),
    })
    message.value = '房间属性已更新。'
    closeRoomEditor()
    await Promise.all([loadBuildings(), loadRooms()])
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间更新失败'
  }
}

function physicalBedCount(room: DataObject | null) {
  if (!room) return 0
  const value = Number(room.bed_count ?? room.capacity ?? 0)
  return Number.isFinite(value) ? value : 0
}

function enabledBedCount(room: DataObject | null) {
  if (!room) return 0
  const value = Number(room.enabled_bed_count ?? 0)
  return Number.isFinite(value) ? value : 0
}

function unavailableBedCount(room: DataObject | null) {
  if (!room) return 0
  const explicit = Number(room.disabled_bed_count ?? 0) + Number(room.maintenance_bed_count ?? 0)
  if (Number.isFinite(explicit) && explicit > 0) return explicit
  return Math.max(0, physicalBedCount(room) - enabledBedCount(room))
}

function roomTypeOptions(room: DataObject): RoomTypeOption[] {
  const capacity = physicalBedCount(room)
  const standardType = capacity === 4
    ? 'FOUR_PERSON'
    : capacity === 5
      ? 'FIVE_PERSON'
      : capacity === 6
        ? 'SIX_PERSON'
        : null
  const options: RoomTypeOption[] = []
  if (standardType) {
    options.push({
      value: standardType as RoomRequest['roomType'],
      label: ROOM_TYPE_LABELS[standardType],
    })
  }
  options.push({ value: 'OTHER', label: '其他（非标准床位布局）' })
  return options
}

function roomLabel(room: DataObject) {
  return `${String(room.building_name)} ${String(room.room_number)}`
}

function roomType(value: unknown) {
  return ROOM_TYPE_LABELS[String(value)] ?? value
}

function statusText(value: unknown) {
  return { ENABLED: '启用', DISABLED: '禁用', MAINTENANCE: '维护' }[String(value)] ?? value
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">DORMITORY RESOURCES</span>
      <h2>宿舍、房间与床位</h2>
      <p>房型和性别属性分别维护。每间房固定为男寝或女寝，并可按实际家具位置单独调整床位布局。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="stat-grid compact-grid">
      <article v-for="building in buildings" :key="String(building.id)" class="panel building-card">
        <span class="eyebrow">{{ building.building_code }}</span>
        <h3>{{ building.building_name }}</h3>
        <p>{{ building.gender_restriction === 'M' ? '男生宿舍' : building.gender_restriction === 'F' ? '女生宿舍' : '按房间设置性别' }}</p>
        <div class="room-facts"><span>{{ building.room_count }}间</span><span>{{ building.bed_count }}床</span></div>
      </article>
    </div>

    <section class="panel">
      <div class="section-head split-title">
        <div><span class="eyebrow">ROOMS</span><h3>房间列表</h3></div>
        <div class="inline-form" aria-label="房间筛选">
          <select v-model="buildingId" class="input" aria-label="按楼栋筛选">
            <option :value="undefined">全部楼栋</option>
            <option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option>
          </select>
          <select v-model="gender" class="input" aria-label="按宿舍性别筛选">
            <option value="">全部性别</option>
            <option value="M">男寝</option>
            <option value="F">女寝</option>
          </select>
        </div>
      </div>
      <p v-if="loadingRooms" class="empty-state">正在刷新房间列表…</p>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>楼栋</th><th>房间</th><th>房型</th><th>性别</th><th>床位</th><th>状态</th><th /></tr></thead>
          <tbody>
            <tr v-for="room in rooms" :key="String(room.id)">
              <td>{{ room.building_name }}</td>
              <td><strong>{{ room.room_number }}</strong><small>{{ room.floor_number }}层</small></td>
              <td>{{ roomType(room.room_type) }}</td>
              <td>{{ room.gender_restriction === 'M' ? '男寝' : '女寝' }}</td>
              <td>
                <strong>{{ enabledBedCount(room) }}/{{ physicalBedCount(room) }}</strong>
                <small v-if="unavailableBedCount(room)">{{ unavailableBedCount(room) }}个暂停使用</small>
              </td>
              <td><span class="status-chip compact">{{ statusText(room.operational_status) }}</span></td>
              <td>
                <div class="button-row compact-actions">
                  <button class="button ghost small" type="button" @click="openRoomEditor(room)">编辑</button>
                  <button class="button ghost small" type="button" @click="openLayoutEditor(room)">布局</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="selectedRoom" class="modal-overlay" @click.self="closeRoomEditor">
      <section class="modal-dialog room-editor-dialog" role="dialog" aria-modal="true" aria-labelledby="room-editor-title">
        <div class="modal-head">
          <div>
            <span class="eyebrow">EDIT ROOM</span>
            <h3 id="room-editor-title">{{ selectedRoom.building_name }} {{ selectedRoom.room_number }}</h3>
            <p>修改房间属性不会增删床位；物理床位和家具位置请通过布局功能维护。</p>
          </div>
          <button class="modal-close" type="button" aria-label="关闭编辑窗口" @click="closeRoomEditor">×</button>
        </div>

        <div class="room-editor-summary" aria-label="房间床位摘要">
          <article>
            <span>物理床位</span>
            <strong>{{ physicalBedCount(selectedRoom) }}</strong>
            <small>决定房间规划容量</small>
          </article>
          <article>
            <span>当前启用</span>
            <strong>{{ enabledBedCount(selectedRoom) }}</strong>
            <small>学生当前可选择</small>
          </article>
          <article>
            <span>暂停使用</span>
            <strong>{{ unavailableBedCount(selectedRoom) }}</strong>
            <small>停用或维护床位</small>
          </article>
        </div>

        <form class="form-grid two-column room-editor-form" @submit.prevent="saveRoom">
          <div class="room-editor-section span-2">
            <strong>房间基本属性</strong>
            <span>标准房型必须和物理床位总数一致。</span>
          </div>
          <label>
            <span>房型</span>
            <select v-model="editForm.roomType" class="input">
              <option v-for="option in roomTypeOptions(selectedRoom)" :key="String(option.value)" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="room-capacity-field">
            <span>规划容量</span>
            <input v-model.number="editForm.capacity" class="input" type="number" readonly />
            <small>容量由物理床位总数决定，不能在房间属性中单独修改。</small>
          </label>
          <label>
            <span>房间性别</span>
            <select v-model="editForm.gender" class="input"><option value="M">男寝</option><option value="F">女寝</option></select>
          </label>
          <label>
            <span>运行状态</span>
            <select v-model="editForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select>
          </label>

          <div class="room-editor-section span-2">
            <strong>说明与审计</strong>
            <span>修改原因会写入操作审计记录。</span>
          </div>
          <label class="span-2"><span>备注</span><input v-model="editForm.remark" class="input" maxlength="500" placeholder="可选，记录房间实际情况" /></label>
          <label class="span-2"><span>修改原因</span><input v-model="editForm.reason" class="input" required maxlength="500" placeholder="必填，例如：调整房间运行状态" /></label>
          <div class="button-row span-2 room-editor-actions"><button type="button" class="button ghost" @click="closeRoomEditor">取消</button><button class="button primary">保存修改</button></div>
        </form>
      </section>
    </div>

    <RoomLayoutEditor
      v-if="layoutRoom"
      :room-id="Number(layoutRoom.id)"
      :room-label="roomLabel(layoutRoom)"
      @close="layoutRoom = null"
      @saved="loadRooms"
    />
  </div>
</template>
