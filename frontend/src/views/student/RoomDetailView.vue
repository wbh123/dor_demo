<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import RoomBedScene3D from '../../components/student/RoomBedScene3D.vue'
import TeamBedAssignmentPanel, { type TeamMemberAssignment } from '../../components/student/TeamBedAssignmentPanel.vue'
import { api, subscribeRoomEvents } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const roomId = Number(route.params.roomId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const queryMemberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const room = ref<DataObject>({})
const beds = ref<DataObject[]>([])
const teamMembers = ref<DataObject[]>([])
const memberAssignments = ref<TeamMemberAssignment[]>([])
const selectedBedIds = ref<number[]>([])
const holdToken = ref('')
const expiresAt = ref<number | null>(null)
const now = ref(Date.now())
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const toastMessage = ref('')
const teamNoticeOpen = ref(Boolean(teamId))
const teamNoticeAccepted = ref(!teamId)
const abortController = new AbortController()
let timer: number | undefined
let toastTimer: number | undefined

const memberCount = computed(() => teamMembers.value.length || queryMemberCount)
const orderedTeamBedIds = computed(() => teamMembers.value.map(member =>
  Number(memberAssignments.value.find(item => Number(item.studentId) === Number(member.studentId))?.bedId ?? 0),
).filter(Boolean))
const remainingSeconds = computed(() => expiresAt.value ? Math.max(0, Math.ceil((expiresAt.value - now.value) / 1000)) : 0)
const selectionReady = computed(() => isTeamMode.value
  ? teamMembers.value.length > 0 && orderedTeamBedIds.value.length === teamMembers.value.length && new Set(orderedTeamBedIds.value).size === teamMembers.value.length
  : selectedBedIds.value.length === 1)
const selectedBeds = computed(() => beds.value.filter(bed => selectedBedIds.value.includes(Number(bed.id))))
const dropdownValue = computed(() => isTeamMode.value ? '' : (selectedBedIds.value[0] ? String(selectedBedIds.value[0]) : ''))
const sceneDisabled = computed(() => submitting.value || !teamNoticeAccepted.value || (isTeamMode.value && holdToken.value.length > 0))

onMounted(async () => {
  await loadTeamMembers()
  await load()
  timer = window.setInterval(() => {
    now.value = Date.now()
    if (expiresAt.value && remainingSeconds.value === 0) resetHold(true)
  }, 1000)
  void subscribeRoomEvents(batchId, roomId, abortController.signal, event => {
    if (event.event !== 'HEARTBEAT' && event.event !== 'CONNECTED') void load(false)
  }).catch(reason => {
    if (!abortController.signal.aborted) error.value = reason instanceof Error ? reason.message : '房间信息更新连接已中断，请刷新页面'
  })
})

onBeforeUnmount(() => {
  abortController.abort()
  if (timer) window.clearInterval(timer)
  if (toastTimer) window.clearTimeout(toastTimer)
})

async function loadTeamMembers() {
  if (!teamId) return
  try {
    const response = await api.get<ListSuccessResponse>(`/api/v1/student/teams/${teamId}/selection-members`)
    teamMembers.value = (response.data.data ?? []) as DataObject[]
    memberAssignments.value = teamMembers.value.map(member => ({ studentId: Number(member.studentId), bedId: 0 }))
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '队伍成员加载失败'
  }
}

async function load(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/rooms/${roomId}`)
    const data = (response.data.data ?? {}) as DataObject
    room.value = (data.room ?? {}) as DataObject
    beds.value = (data.beds ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '房间加载失败'
  } finally {
    loading.value = false
  }
}

function acceptTeamNotice() {
  teamNoticeAccepted.value = true
  teamNoticeOpen.value = false
}
function cancelTeamNotice() {
  teamNoticeOpen.value = false
  void router.back()
}
function updateMemberAssignments(value: TeamMemberAssignment[]) {
  if (holdToken.value) return
  memberAssignments.value = value
  selectedBedIds.value = value.map(item => Number(item.bedId)).filter(Boolean)
}
function showToast(text: string) {
  toastMessage.value = text
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastMessage.value = ''; toastTimer = undefined }, 3000)
}
async function selectFromDropdown(event: Event) {
  const bedId = Number((event.target as HTMLSelectElement).value)
  if (!bedId) return
  const bed = beds.value.find(item => Number(item.id) === bedId)
  if (bed) await selectBed(bed)
}
async function selectBed(bed: DataObject) {
  if (submitting.value || !teamNoticeAccepted.value) return
  const bedId = Number(bed.id)
  if (isTeamMode.value) {
    if (holdToken.value || bed.status !== 'AVAILABLE') return
    const already = memberAssignments.value.find(item => item.bedId === bedId)
    if (already) {
      updateMemberAssignments(memberAssignments.value.map(item => item.studentId === already.studentId ? { ...item, bedId: 0 } : item))
      return
    }
    const target = memberAssignments.value.find(item => !item.bedId)
    if (target) updateMemberAssignments(memberAssignments.value.map(item => item.studentId === target.studentId ? { ...item, bedId } : item))
    return
  }
  const selected = selectedBedIds.value.includes(bedId)
  if (selected && holdToken.value) { await releaseHold(); return }
  if (bed.status !== 'AVAILABLE') return
  if (holdToken.value && selectedBedIds.value.length === 1) { await switchIndividualBed(bed); return }
  selectedBedIds.value = [bedId]
  await createHold()
}
async function requestHold(bedIds: number[]) {
  const response = isTeamMode.value
    ? await api.post<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/teams/${teamId}/hold`, { bedIds })
    : await api.post<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/beds/${bedIds[0]}/hold`)
  const data = (response.data.data ?? {}) as DataObject
  holdToken.value = String(data.token)
  expiresAt.value = new Date(String(data.expiresAt)).getTime()
}
async function createHold() {
  if (!selectionReady.value) return
  submitting.value = true; error.value = ''; message.value = ''
  try {
    const bedIds = isTeamMode.value ? orderedTeamBedIds.value : [...selectedBedIds.value]
    await requestHold(bedIds)
    if (isTeamMode.value) message.value = `${memberCount.value}个成员床位已整体临时保留，请在倒计时内确认。`
    await load(false)
  } catch (reason) {
    if (!isTeamMode.value) selectedBedIds.value = []
    error.value = reason instanceof Error ? reason.message : '床位保留失败'
  } finally { submitting.value = false }
}
async function releaseIndividualHold(bedId: number, token: string) { await api.post(`/api/v1/student/batches/${batchId}/beds/${bedId}/release`, { token }) }
async function switchIndividualBed(nextBed: DataObject) {
  const previousBedId = selectedBedIds.value[0]; const previousToken = holdToken.value; const nextBedId = Number(nextBed.id)
  if (!previousBedId || !previousToken || !nextBedId) return
  submitting.value = true; error.value = ''; message.value = ''; let previousReleased = false
  try {
    await releaseIndividualHold(previousBedId, previousToken); previousReleased = true; holdToken.value = ''; expiresAt.value = null; selectedBedIds.value = [nextBedId]
    await requestHold([nextBedId]); await load(false)
  } catch (reason) {
    if (previousReleased) { holdToken.value = ''; expiresAt.value = null; selectedBedIds.value = []; error.value = reason instanceof Error ? `原床位已释放，但新床位保留失败：${reason.message}` : '原床位已释放，但新床位保留失败，请重新选择。'; await load(false) }
    else error.value = reason instanceof Error ? `当前床位释放失败，尚未切换：${reason.message}` : '当前床位释放失败，尚未切换。'
  } finally { submitting.value = false }
}
async function releaseHold() {
  const bedIds = isTeamMode.value ? orderedTeamBedIds.value : selectedBedIds.value
  if (!holdToken.value || !bedIds.length) return
  submitting.value = true; error.value = ''; message.value = ''
  try {
    if (isTeamMode.value) await api.post(`/api/v1/student/batches/${batchId}/teams/${teamId}/release`, { bedIds, token: holdToken.value })
    else await releaseIndividualHold(bedIds[0], holdToken.value)
    resetHold(true); showToast('已释放当前选择，可以重新选择床位。')
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '床位释放失败' }
  finally { submitting.value = false }
}
async function confirmSelection() {
  const bedIds = isTeamMode.value ? orderedTeamBedIds.value : selectedBedIds.value
  if (!holdToken.value || !bedIds.length) return
  submitting.value = true; error.value = ''
  try {
    if (isTeamMode.value) {
      await api.post(`/api/v1/student/batches/${batchId}/teams/${teamId}/confirm`, { bedIds, token: holdToken.value })
      await router.replace('/student/teams')
    } else {
      await api.post(`/api/v1/student/batches/${batchId}/beds/${bedIds[0]}/confirm`, { token: holdToken.value })
      await router.replace('/student')
    }
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '最终确认失败' }
  finally { submitting.value = false }
}
function resetHold(refresh: boolean) { holdToken.value = ''; expiresAt.value = null; if (!isTeamMode.value) selectedBedIds.value = []; if (refresh) void load(false) }
function canChooseBed(bed: DataObject) { const selected = selectedBedIds.value.includes(Number(bed.id)); if (selected && !isTeamMode.value) return true; if (isTeamMode.value && holdToken.value) return false; return bed.status === 'AVAILABLE' }
function statusText(status: unknown) { return ({ AVAILABLE:'可选择', HELD:'暂时被其他同学保留', HELD_BY_ME:'已为你保留', ASSIGNED:'已有同学选择', DISABLED:'暂不可用' } as Record<string,string>)[String(status)] ?? String(status) }
function bedTypeText(value: unknown) { return bedTypeLabel(value) }
</script>

<template>
  <div class="content-column room-detail-page">
    <Transition name="toast"><div v-if="toastMessage" class="selection-toast" role="status" aria-live="polite"><span class="selection-toast-icon">✓</span><span>{{ toastMessage }}</span></div></Transition>
    <div class="page-title split-title room-detail-heading"><div><span class="eyebrow">ROOM LAYOUT</span><h2>{{ room.building_name }} · {{ room.room_number }} 室</h2><p v-if="isTeamMode">队长需要为每名已确认队友分别确定床位，所有床位必须位于当前寝室。</p><p v-else>{{ room.floor_number }} 层 · {{ room.capacity }}个床位。可以直接点击三维图像中的床位进行选择。</p></div><button class="button ghost" @click="router.back()">返回房间列表</button></div>
    <p v-if="loading" class="panel empty-state">正在同步房间床位…</p><p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success compact-alert">{{ message }}</p>

    <section v-if="!loading" class="panel room-layout-panel compact-room-layout-panel">
      <TeamBedAssignmentPanel v-if="isTeamMode" :members="teamMembers" :beds="beds.filter(bed=>bed.status==='AVAILABLE'||selectedBedIds.includes(Number(bed.id)))" :member-assignments="memberAssignments" :disabled="sceneDisabled" @update:member-assignments="updateMemberAssignments" />

      <div class="bed-selection-toolbar compact-bed-selection-toolbar">
        <label v-if="!isTeamMode" class="bed-select-field"><span>床位下拉选择</span><select class="bed-select-control" :value="dropdownValue" :disabled="submitting" @change="selectFromDropdown"><option value="">请选择床位</option><option v-for="bed in beds" :key="`option-${String(bed.id)}`" :value="String(bed.id)" :disabled="!canChooseBed(bed)">{{ bed.bed_code }}床 · {{ bedTypeText(bed.bed_type) }} · {{ selectedBedIds.includes(Number(bed.id)) ? '已选中' : statusText(bed.status) }}</option></select></label>
        <div class="selection-overview-grid"><div class="selected-bed-summary" :class="{active:selectedBeds.length>0}" aria-live="polite"><span>{{ selectedBeds.length ? '当前选择' : '尚未选择' }}</span><strong v-if="selectedBeds.length">{{ selectedBeds.map(bed=>`${String(bed.bed_code)}床`).join('、') }}</strong><small v-if="selectedBeds.length">{{ selectedBeds.map(bed=>bedTypeText(bed.bed_type)).join('、') }}</small><small v-else>{{ isTeamMode ? '请在队友卡片中逐人确定床位' : '请从下拉框或三维床位中选择' }}</small></div><div class="selection-hold-card" :class="{active:Boolean(holdToken)}" aria-live="polite"><div class="selection-hold-status"><span>临时保留</span><strong>{{ holdToken ? '床位已临时预留，请在倒计时结束前确认' : '完成床位安排后将整体临时预留' }}</strong></div><div v-if="holdToken" class="countdown compact-countdown enlarged-countdown">{{ remainingSeconds }}<small>秒</small></div><div class="selection-hold-actions"><button class="button ghost" :disabled="!holdToken||submitting" @click="releaseHold">主动释放</button><button class="button primary" :disabled="!holdToken||submitting||remainingSeconds<=0" @click="confirmSelection">{{ submitting?'正在处理…':isTeamMode?'确认小组选寝':'确认当前床位' }}</button></div></div></div>
      </div>

      <RoomBedScene3D :beds="beds" :selected-bed-ids="selectedBedIds" :disabled="sceneDisabled" @select="selectBed" />
      <div class="scene-legend compact-scene-legend" aria-label="床位状态说明"><span class="legend-available">可选择</span><span class="legend-selected">已选中</span><span class="legend-held">暂时保留</span><span class="legend-assigned">已有同学选择</span></div>
      <div v-if="isTeamMode&&!holdToken" class="button-row centered"><button class="button primary" :disabled="!selectionReady||submitting||!teamNoticeAccepted" @click="createHold">整体保留 {{ memberCount }} 个成员床位</button></div>
    </section>

    <ActionConfirmDialog :open="teamNoticeOpen" title="组队选床说明" message="所有床位由队长统一确定，请先与队友确认沟通好寝室和床位安排后再进行操作。" detail="系统只保留已经确认加入的队友；未确认邀请会在锁定队伍时自动取消并立即失效。" confirm-text="已沟通，开始选床" cancel-text="返回" @confirm="acceptTeamNotice" @cancel="cancelTeamNotice" />
  </div>
</template>

<style scoped>
.enlarged-countdown{min-width:116px;font-size:44px!important;font-weight:850}.selection-hold-status strong{max-width:360px;font-size:16px;line-height:1.45}.room-layout-panel{display:grid;gap:16px}
</style>
