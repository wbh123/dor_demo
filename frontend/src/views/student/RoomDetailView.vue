<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RoomBedScene3D from '../../components/student/RoomBedScene3D.vue'
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
const toastMessage = ref('')
const abortController = new AbortController()
let timer: number | undefined
let toastTimer: number | undefined

const remainingSeconds = computed(() =>
  expiresAt.value ? Math.max(0, Math.ceil((expiresAt.value - now.value) / 1000)) : 0,
)

const selectionReady = computed(() =>
  isTeamMode.value ? selectedBedIds.value.length === memberCount : selectedBedIds.value.length === 1,
)

const selectedBeds = computed(() =>
  beds.value.filter((bed) => selectedBedIds.value.includes(Number(bed.id))),
)

const dropdownValue = computed(() => {
  if (isTeamMode.value) return ''
  return selectedBedIds.value[0] ? String(selectedBedIds.value[0]) : ''
})

const sceneDisabled = computed(() =>
  submitting.value || (isTeamMode.value && holdToken.value.length > 0),
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
  if (toastTimer) window.clearTimeout(toastTimer)
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

function showToast(text: string) {
  toastMessage.value = text
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMessage.value = ''
    toastTimer = undefined
  }, 3000)
}

async function selectFromDropdown(event: Event) {
  const select = event.target as HTMLSelectElement
  const bedId = Number(select.value)
  if (!bedId) return
  const bed = beds.value.find((item) => Number(item.id) === bedId)
  if (bed) await selectBed(bed)
  if (isTeamMode.value) select.value = ''
}

async function selectBed(bed: DataObject) {
  if (submitting.value) return
  const bedId = Number(bed.id)
  const selected = selectedBedIds.value.includes(bedId)

  if (isTeamMode.value) {
    if (holdToken.value || (bed.status !== 'AVAILABLE' && !selected)) return
    if (selected) {
      selectedBedIds.value = selectedBedIds.value.filter((id) => id !== bedId)
    } else if (selectedBedIds.value.length < memberCount) {
      selectedBedIds.value = [...selectedBedIds.value, bedId]
    }
    return
  }

  if (selected && holdToken.value) {
    await releaseHold()
    return
  }
  if (bed.status !== 'AVAILABLE') return

  if (holdToken.value && selectedBedIds.value.length === 1) {
    await switchIndividualBed(bed)
    return
  }

  selectedBedIds.value = [bedId]
  await createHold()
}

async function requestHold(bedIds: number[]) {
  const response = isTeamMode.value
    ? await api.post<ObjectSuccessResponse>(
        `/api/v1/student/batches/${batchId}/teams/${teamId}/hold`,
        { bedIds },
      )
    : await api.post<ObjectSuccessResponse>(
        `/api/v1/student/batches/${batchId}/beds/${bedIds[0]}/hold`,
      )
  const data = (response.data.data ?? {}) as DataObject
  holdToken.value = String(data.token)
  expiresAt.value = new Date(String(data.expiresAt)).getTime()
}

async function createHold() {
  if (!selectionReady.value) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    await requestHold([...selectedBedIds.value])
    message.value = isTeamMode.value
      ? `${memberCount}个床位已整体临时保留，请在倒计时内确认。`
      : '床位已临时保留；点击其他空床位可以直接切换床位。'
    await load(false)
  } catch (reason) {
    selectedBedIds.value = []
    error.value = reason instanceof Error ? reason.message : '床位保留失败'
  } finally {
    submitting.value = false
  }
}

async function releaseIndividualHold(bedId: number, token: string) {
  await api.post(
    `/api/v1/student/batches/${batchId}/beds/${bedId}/release`,
    { token },
  )
}

async function switchIndividualBed(nextBed: DataObject) {
  const previousBedId = selectedBedIds.value[0]
  const previousToken = holdToken.value
  const nextBedId = Number(nextBed.id)
  if (!previousBedId || !previousToken || !nextBedId) return

  submitting.value = true
  error.value = ''
  message.value = ''
  let previousReleased = false

  try {
    await releaseIndividualHold(previousBedId, previousToken)
    previousReleased = true
    holdToken.value = ''
    expiresAt.value = null
    selectedBedIds.value = [nextBedId]

    await requestHold([nextBedId])
    message.value = `已切换到 ${String(nextBed.bed_code)} 床，请在倒计时结束前确认。`
    await load(false)
  } catch (reason) {
    if (previousReleased) {
      holdToken.value = ''
      expiresAt.value = null
      selectedBedIds.value = []
      error.value = reason instanceof Error
        ? `原床位已释放，但新床位保留失败：${reason.message}`
        : '原床位已释放，但新床位保留失败，请重新选择。'
      await load(false)
    } else {
      error.value = reason instanceof Error
        ? `当前床位释放失败，尚未切换：${reason.message}`
        : '当前床位释放失败，尚未切换。'
    }
  } finally {
    submitting.value = false
  }
}

async function releaseHold() {
  if (!holdToken.value || !selectedBedIds.value.length) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    if (isTeamMode.value) {
      await api.post(`/api/v1/student/batches/${batchId}/teams/${teamId}/release`, {
        bedIds: selectedBedIds.value,
        token: holdToken.value,
      })
    } else {
      await releaseIndividualHold(selectedBedIds.value[0], holdToken.value)
    }
    resetHold(true)
    showToast('已释放当前选择，可以重新选择床位。')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '床位释放失败'
  } finally {
    submitting.value = false
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

function canChooseBed(bed: DataObject) {
  const selected = selectedBedIds.value.includes(Number(bed.id))
  if (selected && !isTeamMode.value) return true
  if (isTeamMode.value && holdToken.value) return false
  return bed.status === 'AVAILABLE'
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
</script>

<template>
  <div class="content-column room-detail-page">
    <Transition name="toast">
      <div v-if="toastMessage" class="selection-toast" role="status" aria-live="polite">
        <span class="selection-toast-icon">✓</span>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <div class="page-title split-title room-detail-heading">
      <div>
        <span class="eyebrow">ROOM LAYOUT</span>
        <h2>{{ room.building_name }} · {{ room.room_number }} 室</h2>
        <p v-if="isTeamMode">请选择 {{ memberCount }} 个床位，全部选好后再整体保留。</p>
        <p v-else>{{ room.floor_number }} 层 · {{ room.capacity }}个床位</p>
      </div>
      <button class="button ghost" @click="router.back()">返回房间列表</button>
    </div>

    <p v-if="loading" class="panel empty-state">正在同步房间床位…</p>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success compact-alert">{{ message }}</p>

    <section v-if="!loading" class="panel room-layout-panel compact-room-layout-panel">
      <div v-if="isTeamMode && !holdToken" class="selection-hint team-selection-count">
        已选择 {{ selectedBedIds.length }}/{{ memberCount }} 个床位
      </div>

      <div class="bed-selection-toolbar compact-bed-selection-toolbar">
        <label class="bed-select-field">
          <span>{{ isTeamMode ? '添加或移除床位' : '床位下拉选择' }}</span>
          <select
            class="bed-select-control"
            :value="dropdownValue"
            :disabled="submitting || (isTeamMode && holdToken.length > 0)"
            @change="selectFromDropdown"
          >
            <option value="">{{ isTeamMode ? '请选择一个床位进行添加或移除' : '请选择床位' }}</option>
            <option
              v-for="bed in beds"
              :key="`option-${String(bed.id)}`"
              :value="String(bed.id)"
              :disabled="!canChooseBed(bed)"
            >
              {{ bed.bed_code }}床 · {{ bedTypeText(bed.bed_type) }} ·
              {{ selectedBedIds.includes(Number(bed.id)) ? '已选中' : statusText(bed.status) }}
            </option>
          </select>
        </label>

        <div class="selected-bed-summary" :class="{ active: selectedBeds.length > 0 }" aria-live="polite">
          <span>{{ selectedBeds.length ? '当前选择' : '尚未选择' }}</span>
          <strong v-if="selectedBeds.length">
            {{ selectedBeds.map((bed) => `${String(bed.bed_code)}床`).join('、') }}
          </strong>
          <small v-if="selectedBeds.length">
            {{ selectedBeds.map((bed) => bedTypeText(bed.bed_type)).join('、') }}
          </small>
        </div>
      </div>

      <RoomBedScene3D
        :beds="beds"
        :selected-bed-ids="selectedBedIds"
        :disabled="sceneDisabled"
        @select="selectBed"
      />

      <div class="scene-legend compact-scene-legend" aria-label="床位状态说明">
        <span class="legend-available">可选择</span>
        <span class="legend-selected">已选中</span>
        <span class="legend-held">暂时保留</span>
        <span class="legend-assigned">已有同学选择</span>
      </div>

      <div v-if="isTeamMode && !holdToken" class="button-row centered">
        <button class="button primary" :disabled="!selectionReady || submitting" @click="createHold">
          整体保留 {{ memberCount }} 个床位
        </button>
      </div>
    </section>

    <section v-if="holdToken" class="panel hold-panel bed-selection-action-bar">
      <div>
        <span class="eyebrow">TEMPORARY HOLD</span>
        <h3>{{ isTeamMode ? '小组床位已整体保留' : '床位已临时保留' }}</h3>
        <p v-if="isTeamMode">请在倒计时结束前确认；超时后床位会重新开放选择。</p>
        <p v-else>可直接点击其他空床位切换，或在倒计时结束前确认当前床位。</p>
      </div>
      <div class="countdown">{{ remainingSeconds }}<small>秒</small></div>
      <div class="button-row">
        <button class="button ghost" :disabled="submitting" @click="releaseHold">主动释放</button>
        <button class="button primary" :disabled="submitting || remainingSeconds <= 0" @click="confirmSelection">
          {{ submitting ? '正在处理…' : isTeamMode ? '确认小组整体选寝' : '确认选择此床位' }}
        </button>
      </div>
    </section>
  </div>
</template>
