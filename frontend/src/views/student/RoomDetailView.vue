<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, subscribeRoomEvents } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const roomId = Number(route.params.roomId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const memberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const room = ref<DataObject>({})
const beds = ref<DataObject[]>([])
const selectedBedIds = ref<number[]>([])
const holdToken = ref('')
const expiresAt = ref<number | null>(null)
const now = ref(Date.now())
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const abortController = new AbortController()
let timer: number | undefined

const remainingSeconds = computed(() =>
  expiresAt.value ? Math.max(0, Math.ceil((expiresAt.value - now.value) / 1000)) : 0,
)

const selectionReady = computed(() =>
  isTeamMode.value ? selectedBedIds.value.length === memberCount : selectedBedIds.value.length === 1,
)

onMounted(async () => {
  await load()
  timer = window.setInterval(() => {
    now.value = Date.now()
    if (expiresAt.value && remainingSeconds.value === 0) resetHold(true)
  }, 1000)
  void subscribeRoomEvents(batchId, roomId, abortController.signal, (event) => {
    if (event.event !== 'HEARTBEAT' && event.event !== 'CONNECTED') void load(false)
  }).catch((reason) => {
    if (!abortController.signal.aborted) {
      error.value = reason instanceof Error ? reason.message : '实时连接中断'
    }
  })
})

onBeforeUnmount(() => {
  abortController.abort()
  if (timer) window.clearInterval(timer)
})

async function load(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/student/batches/${batchId}/rooms/${roomId}`,
    )
    const data = (response.data.data ?? {}) as DataObject
    room.value = (data.room ?? {}) as DataObject
    beds.value = (data.beds ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间加载失败'
  } finally {
    loading.value = false
  }
}

async function selectBed(bed: DataObject) {
  if (bed.status !== 'AVAILABLE' || holdToken.value) return
  const bedId = Number(bed.id)
  if (isTeamMode.value) {
    if (selectedBedIds.value.includes(bedId)) {
      selectedBedIds.value = selectedBedIds.value.filter((id) => id !== bedId)
    } else if (selectedBedIds.value.length < memberCount) {
      selectedBedIds.value = [...selectedBedIds.value, bedId]
    }
    return
  }
  selectedBedIds.value = [bedId]
  await createHold()
}

async function createHold() {
  if (!selectionReady.value) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const response = isTeamMode.value
      ? await api.post<ObjectSuccessResponse>(
          `/api/v1/student/batches/${batchId}/teams/${teamId}/hold`,
          { bedIds: selectedBedIds.value },
        )
      : await api.post<ObjectSuccessResponse>(
          `/api/v1/student/batches/${batchId}/beds/${selectedBedIds.value[0]}/hold`,
        )
    const data = (response.data.data ?? {}) as DataObject
    holdToken.value = String(data.token)
    expiresAt.value = new Date(String(data.expiresAt)).getTime()
    message.value = isTeamMode.value
      ? `${memberCount}个床位已整体临时保留，请在倒计时内确认。`
      : '床位已临时保留，请在倒计时结束前确认。'
    await load(false)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '占用失败'
  } finally {
    submitting.value = false
  }
}

async function releaseHold() {
  if (!holdToken.value || !selectedBedIds.value.length) return
  try {
    if (isTeamMode.value) {
      await api.post(`/api/v1/student/batches/${batchId}/teams/${teamId}/release`, {
        bedIds: selectedBedIds.value,
        token: holdToken.value,
      })
    } else {
      await api.post(
        `/api/v1/student/batches/${batchId}/beds/${selectedBedIds.value[0]}/release`,
        { token: holdToken.value },
      )
    }
  } finally {
    resetHold(true)
  }
}

async function confirmSelection() {
  if (!holdToken.value || !selectedBedIds.value.length) return
  submitting.value = true
  error.value = ''
  try {
    if (isTeamMode.value) {
      await api.post(`/api/v1/student/batches/${batchId}/teams/${teamId}/confirm`, {
        bedIds: selectedBedIds.value,
        token: holdToken.value,
      })
      await router.replace('/student/teams')
    } else {
      await api.post(
        `/api/v1/student/batches/${batchId}/beds/${selectedBedIds.value[0]}/confirm`,
        { token: holdToken.value },
      )
      await router.replace(`/student/batches/${batchId}/assignment`)
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '最终确认失败'
  } finally {
    submitting.value = false
  }
}

function resetHold(refresh: boolean) {
  holdToken.value = ''
  selectedBedIds.value = []
  expiresAt.value = null
  if (refresh) void load(false)
}

function statusText(status: unknown) {
  return {
    AVAILABLE: '可选择',
    HELD: '临时占用',
    HELD_BY_ME: '已为你保留',
    ASSIGNED: '已分配',
    DISABLED: '不可用',
  }[String(status)] ?? String(status)
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">LIVE ROOM STATUS</span>
        <h2>{{ room.building_name }} · {{ room.room_number }} 室</h2>
        <p v-if="isTeamMode">队伍模式：请选择 {{ memberCount }} 个床位，系统将整体占用和提交。</p>
        <p v-else>{{ room.floor_number }} 层 · {{ room.capacity }}个床位 · 状态版本 {{ room.state_version }}</p>
      </div>
      <button class="button ghost" @click="router.back()">返回房间列表</button>
    </div>

    <p v-if="loading" class="panel empty-state">正在同步房间床位…</p>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="!loading" class="panel room-layout-panel">
      <div class="live-indicator"><i /> 房间状态实时更新</div>
      <div v-if="isTeamMode && !holdToken" class="selection-hint">
        已选择 {{ selectedBedIds.length }}/{{ memberCount }} 个床位
      </div>
      <div class="bed-layout" :class="`capacity-${room.capacity}`">
        <button
          v-for="bed in beds"
          :key="String(bed.id)"
          class="bed-card"
          :class="[
            `status-${String(bed.status).toLowerCase()}`,
            { selected: selectedBedIds.includes(Number(bed.id)) },
          ]"
          :disabled="bed.status !== 'AVAILABLE' || submitting || Boolean(holdToken)"
          @click="selectBed(bed)"
        >
          <span class="bed-code">{{ bed.bed_code }}</span>
          <strong>{{ bed.bed_type === 'LOFT_BED_DESK' ? '上床下桌' : bed.bed_type === 'BUNK_UPPER' ? '上下铺上铺' : '上下铺下铺' }}</strong>
          <small>{{ selectedBedIds.includes(Number(bed.id)) ? '已选中' : statusText(bed.status) }}</small>
        </button>
      </div>
      <div v-if="isTeamMode && !holdToken" class="button-row centered">
        <button class="button primary" :disabled="!selectionReady || submitting" @click="createHold">
          整体临时占用 {{ memberCount }} 个床位
        </button>
      </div>
      <p v-if="room.remark" class="room-remark">{{ room.remark }}</p>
    </section>

    <section v-if="holdToken" class="panel hold-panel">
      <div>
        <span class="eyebrow">TEMPORARY HOLD</span>
        <h3>{{ isTeamMode ? '队伍床位已整体保留' : '床位已临时保留' }}</h3>
        <p>倒计时结束后自动释放，只有最终确认成功才形成正式分配。</p>
      </div>
      <div class="countdown">{{ remainingSeconds }}<small>秒</small></div>
      <div class="button-row">
        <button class="button ghost" :disabled="submitting" @click="releaseHold">主动释放</button>
        <button class="button primary" :disabled="submitting || remainingSeconds <= 0" @click="confirmSelection">
          {{ submitting ? '正在确认…' : isTeamMode ? '确认队伍整体选寝' : '确认选择此床位' }}
        </button>
      </div>
    </section>
  </div>
</template>
