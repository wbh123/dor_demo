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
      error.value = reason instanceof Error ? reason.message : '房间信息更新连接已中断，请刷新页面'
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
    error.value = reason instanceof Error ? reason.message : '床位保留失败'
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
      await router.replace('/student')
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
    HELD: '暂时被其他同学保留',
    HELD_BY_ME: '已为你保留',
    ASSIGNED: '已有同学选择',
    DISABLED: '暂不可用',
  }[String(status)] ?? String(status)
}

function bedTypeText(value: unknown) {
  return {
    LOFT_BED_DESK: '上床下桌',
    BUNK_UPPER: '上下铺上铺',
    BUNK_LOWER: '上下铺下铺',
  }[String(value)] ?? String(value)
}

function bedPlacement(bed: DataObject) {
  if (bed.bed_type === 'BUNK_UPPER') return 'bunk-window-upper'
  if (bed.bed_type === 'BUNK_LOWER') return 'bunk-window-lower'
  const position = Number(bed.position_index)
  if (position === 1) return 'loft-left-1'
  if (position === 2) return 'loft-left-2'
  if (position === 3) return 'loft-center-3'
  return 'loft-center-4'
}

function bedVisualClasses(bed: DataObject) {
  return {
    bunk: ['BUNK_UPPER', 'BUNK_LOWER'].includes(String(bed.bed_type)),
    'bunk-upper': bed.bed_type === 'BUNK_UPPER',
    'bunk-lower': bed.bed_type === 'BUNK_LOWER',
    selected: selectedBedIds.value.includes(Number(bed.id)),
  }
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">ROOM LAYOUT</span>
        <h2>{{ room.building_name }} · {{ room.room_number }} 室</h2>
        <p v-if="isTeamMode">请选择 {{ memberCount }} 个床位，全部选好后再整体保留。</p>
        <p v-else>{{ room.floor_number }} 层 · {{ room.capacity }}个床位 · 右侧为窗户</p>
      </div>
      <button class="button ghost" @click="router.back()">返回房间列表</button>
    </div>

    <p v-if="loading" class="panel empty-state">正在同步房间床位…</p>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="!loading" class="panel room-layout-panel">
      <div class="live-indicator"><i /> 床位变化会自动更新</div>
      <div v-if="isTeamMode && !holdToken" class="selection-hint">
        已选择 {{ selectedBedIds.length }}/{{ memberCount }} 个床位
      </div>

      <div class="room-scene" aria-label="房间床位空间布局">
        <div class="room-window" aria-label="窗户"><span /><span /></div>
        <div class="room-entry">入口</div>
        <button
          v-for="bed in beds"
          :key="String(bed.id)"
          class="scene-bed"
          :class="[
            bedPlacement(bed),
            `status-${String(bed.status).toLowerCase()}`,
            bedVisualClasses(bed),
          ]"
          :disabled="bed.status !== 'AVAILABLE' || submitting || Boolean(holdToken)"
          :aria-label="`${bed.bed_code}床，${bedTypeText(bed.bed_type)}，${statusText(bed.status)}`"
          @click="selectBed(bed)"
        >
          <span class="bed-visual" aria-hidden="true">
            <i class="mattress" />
            <i class="desk-block" />
          </span>
          <span class="bed-code">{{ bed.bed_code }} 床</span>
          <strong>{{ bedTypeText(bed.bed_type) }}</strong>
          <small>{{ selectedBedIds.includes(Number(bed.id)) ? '已选中' : statusText(bed.status) }}</small>
        </button>
      </div>

      <div class="scene-legend" aria-label="床位状态说明">
        <span>可选择</span>
        <span>暂时保留</span>
        <span>已有同学选择</span>
        <span>右侧窗边为上下铺</span>
      </div>

      <div v-if="isTeamMode && !holdToken" class="button-row centered">
        <button class="button primary" :disabled="!selectionReady || submitting" @click="createHold">
          整体保留 {{ memberCount }} 个床位
        </button>
      </div>
      <p v-if="room.remark" class="room-remark">{{ room.remark }}</p>
    </section>

    <section v-if="holdToken" class="panel hold-panel">
      <div>
        <span class="eyebrow">TEMPORARY HOLD</span>
        <h3>{{ isTeamMode ? '队伍床位已整体保留' : '床位已临时保留' }}</h3>
        <p>请在倒计时结束前确认；超时后床位会重新开放选择。</p>
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
