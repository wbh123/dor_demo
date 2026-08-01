<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, RoomRequest } from '../../api/types'

const buildings = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const buildingId = ref<number | undefined>()
const gender = ref('')
const selectedRoom = ref<DataObject | null>(null)
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

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [buildingResponse, roomResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/buildings'),
      api.get<ListSuccessResponse>('/api/v1/admin/rooms', {
        params: { buildingId: buildingId.value, gender: gender.value || undefined },
      }),
    ])
    buildings.value = (buildingResponse.data.data ?? []) as DataObject[]
    rooms.value = (roomResponse.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '宿舍资源加载失败'
  }
}

function selectRoom(room: DataObject) {
  selectedRoom.value = room
  editForm.roomType = String(room.room_type) as RoomRequest['roomType']
  editForm.capacity = Number(room.capacity)
  editForm.gender = String(room.gender_restriction) as RoomRequest['gender']
  editForm.operationalStatus = String(room.operational_status) as RoomRequest['operationalStatus']
  editForm.remark = String(room.remark ?? '')
  editForm.reason = ''
}

async function saveRoom() {
  if (!selectedRoom.value) return
  error.value = ''
  message.value = ''
  try {
    await api.put(`/api/v1/admin/rooms/${selectedRoom.value.id}`, editForm)
    message.value = '房间属性已更新并写入审计日志。'
    selectedRoom.value = null
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间更新失败'
  }
}

function roomType(value: unknown) {
  return { FOUR_PERSON: '四人间', FIVE_PERSON: '五人间', SIX_PERSON: '六人间', OTHER: '其他' }[String(value)] ?? value
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">DORMITORY RESOURCES</span>
      <h2>宿舍、房间与床位</h2>
      <p>房型和性别属性分别维护。每间房固定为男寝或女寝，当前男五人间、女四人间只是现阶段配置。</p>
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
        <form class="inline-form" @submit.prevent="load">
          <select v-model="buildingId" class="input">
            <option :value="undefined">全部楼栋</option>
            <option v-for="building in buildings" :key="String(building.id)" :value="Number(building.id)">{{ building.building_name }}</option>
          </select>
          <select v-model="gender" class="input">
            <option value="">全部性别</option><option value="M">男寝</option><option value="F">女寝</option>
          </select>
          <button class="button secondary">筛选</button>
        </form>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>楼栋</th><th>房间</th><th>房型</th><th>性别</th><th>床位</th><th>状态</th><th /></tr></thead>
          <tbody>
            <tr v-for="room in rooms" :key="String(room.id)">
              <td>{{ room.building_name }}</td>
              <td><strong>{{ room.room_number }}</strong><small>{{ room.floor_number }}层</small></td>
              <td>{{ roomType(room.room_type) }}</td>
              <td>{{ room.gender_restriction === 'M' ? '男寝' : '女寝' }}</td>
              <td>{{ room.enabled_bed_count }}/{{ room.capacity }}</td>
              <td><span class="status-chip compact">{{ room.operational_status }}</span></td>
              <td><button class="button ghost small" @click="selectRoom(room)">编辑</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="selectedRoom" class="panel drawer-panel">
      <div class="section-head"><div><span class="eyebrow">EDIT ROOM</span><h3>{{ selectedRoom.building_name }} {{ selectedRoom.room_number }}</h3></div></div>
      <form class="form-grid two-column" @submit.prevent="saveRoom">
        <label><span>房型</span><select v-model="editForm.roomType" class="input"><option value="FOUR_PERSON">四人间</option><option value="FIVE_PERSON">五人间</option><option value="SIX_PERSON">六人间</option><option value="OTHER">其他</option></select></label>
        <label><span>容量</span><input v-model.number="editForm.capacity" class="input" type="number" min="1" max="20" required /></label>
        <label><span>房间性别</span><select v-model="editForm.gender" class="input"><option value="M">男寝</option><option value="F">女寝</option></select></label>
        <label><span>运行状态</span><select v-model="editForm.operationalStatus" class="input"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="MAINTENANCE">维护</option></select></label>
        <label class="span-2"><span>备注</span><input v-model="editForm.remark" class="input" maxlength="500" /></label>
        <label class="span-2"><span>修改原因</span><input v-model="editForm.reason" class="input" required maxlength="500" placeholder="必填，将写入审计记录" /></label>
        <div class="button-row span-2"><button type="button" class="button ghost" @click="selectedRoom = null">取消</button><button class="button primary">保存修改</button></div>
      </form>
    </section>
  </div>
</template>
