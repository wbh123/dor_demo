<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { useI18n } from '../../i18n'

type RecommendationStrategy = 'BEST_MATCH' | 'TRUE_RANDOM' | 'MATCH_WEIGHTED_RANDOM'
interface RecommendationOption { value: RecommendationStrategy; label: string }

const route = useRoute()
const router = useRouter()
const { hasFeature } = useFeatureAccess()
const batchId = Number(route.params.batchId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const memberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const rooms = ref<DataObject[]>([])
const activePersonalTeam = ref<DataObject | null>(null)
const showPersonalExitConfirm = ref(false)
const roomSelectionTarget = ref<DataObject | null>(null)
const selectionSuccess = ref<DataObject | null>(null)
const selectionReadiness = ref<DataObject>({ preferenceCompleted: false, allowWithoutQuestionnaire: false })
const preferencePromptVisible = ref(false)
const pendingPreferenceAction = ref<null | (() => void)>(null)
const loading = ref(true)
const preparingPersonalSelection = ref(false)
const selectingRoomId = ref<number | null>(null)
const recommending = ref(false)
const error = ref('')
const message = ref('')
const recommendationResult = ref<DataObject | null>(null)
const recommendationStrategy = ref<RecommendationStrategy>('BEST_MATCH')
const recommendationRequestId = ref('')
const keyword = ref('')
const floorFilter = ref('')
const minimumAvailableBeds = ref(0)
const { t, subtitle, translateError } = useI18n()

const selectionMode = computed(() => String(rooms.value[0]?.selectionMode ?? 'BED'))
const isRoomMode = computed(() => selectionMode.value === 'ROOM')
const batchAllowedRecommendationStrategies = computed<RecommendationStrategy[]>(() => {
  const configured = rooms.value[0]?.allowedRecommendationStrategies
  if (!Array.isArray(configured) || configured.length === 0) {
    return ['BEST_MATCH', 'TRUE_RANDOM', 'MATCH_WEIGHTED_RANDOM']
  }
  return configured
    .map(String)
    .filter((value): value is RecommendationStrategy =>
      ['BEST_MATCH', 'TRUE_RANDOM', 'MATCH_WEIGHTED_RANDOM'].includes(value))
})
const recommendationOptions = computed<RecommendationOption[]>(() => {
  const allowed = new Set(batchAllowedRecommendationStrategies.value)
  const options: RecommendationOption[] = []
  if (allowed.has('BEST_MATCH') && hasFeature('P2_ROOM_RECOMMENDATION')) {
    options.push({ value: 'BEST_MATCH', label: '最匹配' })
  }
  if (allowed.has('TRUE_RANDOM') && hasFeature('P1_RANDOM_RECOMMENDATION')) {
    options.push({ value: 'TRUE_RANDOM', label: '随机看看' })
  }
  if (allowed.has('MATCH_WEIGHTED_RANDOM') && hasFeature('P2_ROOM_RECOMMENDATION')) {
    options.push({ value: 'MATCH_WEIGHTED_RANDOM', label: '按匹配度随机' })
  }
  return options
})
const recommendationEnabled = computed(() => recommendationOptions.value.length > 0)
const recommendationButtonLabel = computed(() => {
  const selected = recommendationOptions.value.find((item) => item.value === recommendationStrategy.value)
  return selected?.label ?? '帮我推荐一个'
})
const floorOptions = computed(() => [...new Set(rooms.value.map((room) => Number(room.floor_number)))].filter(Number.isFinite).sort((a,b)=>a-b))
const filteredRooms = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rooms.value.filter((room) => {
    if (isTeamMode.value && Number(room.availableCount) < memberCount) return false
    if (floorFilter.value && Number(room.floor_number) !== Number(floorFilter.value)) return false
    if (Number(room.availableCount) < minimumAvailableBeds.value) return false
    return !term || `${room.building_name} ${room.room_number}`.toLowerCase().includes(term)
  })
})

watch(recommendationStrategy, () => {
  recommendationRequestId.value = ''
  recommendationResult.value = null
})
onMounted(initialize)

async function initialize() {
  if (isTeamMode.value) return load()
  loading.value = true
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/student/teams')
    const teams = (response.data.data ?? []) as DataObject[]
    activePersonalTeam.value = teams.find((team) => Number(team.batch_id) === batchId) ?? null
    if (activePersonalTeam.value) { showPersonalExitConfirm.value = true; loading.value = false; return }
    await load()
  } catch (reason) { error.value = translateError(reason); loading.value = false }
}

async function confirmPersonalSelection() {
  preparingPersonalSelection.value = true; error.value = ''
  try {
    await api.post(`/api/v1/student/batches/${batchId}/personal-selection/prepare`)
    showPersonalExitConfirm.value = false; activePersonalTeam.value = null; await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { preparingPersonalSelection.value = false }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const [roomResponse, readinessResponse] = await Promise.all([
      api.get<ListSuccessResponse>(`/api/v1/student/batches/${batchId}/rooms`),
      api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/selection-readiness`),
    ])
    rooms.value = (roomResponse.data.data ?? []) as DataObject[]
    selectionReadiness.value = (readinessResponse.data.data ?? {}) as DataObject
    synchronizeRecommendationStrategy()
  } catch (reason) { error.value = translateError(reason) }
  finally { loading.value = false }
}

function synchronizeRecommendationStrategy() {
  const options = recommendationOptions.value
  if (options.length === 0) return
  const configuredDefault = String(rooms.value[0]?.defaultRecommendationStrategy ?? '') as RecommendationStrategy
  if (options.some((item) => item.value === configuredDefault)) {
    recommendationStrategy.value = configuredDefault
    return
  }
  if (!options.some((item) => item.value === recommendationStrategy.value)) {
    recommendationStrategy.value = options[0].value
  }
}

async function requestRecommendation() {
  if (!recommendationEnabled.value || recommending.value) return
  error.value = ''
  recommending.value = true
  const clientRequestId = recommendationRequestId.value || crypto.randomUUID()
  recommendationRequestId.value = clientRequestId
  try {
    const response = await api.post<ObjectSuccessResponse>(
      `/api/v1/student/batches/${batchId}/recommendations`,
      { strategy: recommendationStrategy.value, clientRequestId },
    )
    recommendationResult.value = (response.data.data ?? {}) as DataObject
    recommendationRequestId.value = ''
  } catch (reason) { error.value = translateError(reason) }
  finally { recommending.value = false }
}

function withPreferenceCheck(action: () => void) {
  if (Boolean(selectionReadiness.value.preferenceCompleted)) { action(); return }
  pendingPreferenceAction.value = action
  preferencePromptVisible.value = true
}

function continueWithoutPreference() {
  if (!Boolean(selectionReadiness.value.allowWithoutQuestionnaire)) {
    void router.push('/student/preferences')
    return
  }
  const action = pendingPreferenceAction.value
  preferencePromptVisible.value = false
  pendingPreferenceAction.value = null
  action?.()
}

function requestRoomSelection(room: DataObject) {
  withPreferenceCheck(() => {
    roomSelectionTarget.value = room
    selectionSuccess.value = null
    error.value = ''
    message.value = ''
  })
}

function closeRoomSelectionConfirm() {
  if (selectingRoomId.value !== null) return
  roomSelectionTarget.value = null
}

async function confirmRoomSelection() {
  const room = roomSelectionTarget.value
  if (!room) return
  selectingRoomId.value = Number(room.id); error.value = ''; message.value = ''
  try {
    const path = isTeamMode.value
      ? `/api/v1/student/batches/${batchId}/teams/${teamId}/rooms/${room.id}/select`
      : `/api/v1/student/batches/${batchId}/rooms/${room.id}/select`
    const response = await api.post<ObjectSuccessResponse>(path)
    selectionSuccess.value = { ...room, ...((response.data.data ?? {}) as DataObject) }
    roomSelectionTarget.value = null
  } catch (reason) { error.value = translateError(reason) }
  finally { selectingRoomId.value = null }
}

function viewSelectionResult() {
  if (isTeamMode.value) void router.replace('/student/teams')
  else void router.replace(`/student/batches/${batchId}/assignment`)
}

function returnStudentHome() {
  void router.replace(isTeamMode.value ? '/student/teams' : '/student')
}

function openRoom(room: DataObject) {
  if (isRoomMode.value) { requestRoomSelection(room); return }
  withPreferenceCheck(() => {
    void router.push({ path: `/student/batches/${batchId}/rooms/${Number(room.id)}`, query: isTeamMode.value ? { teamId: String(teamId), memberCount: String(memberCount) } : undefined })
  })
}
function roomType(value: unknown) { return ({ FOUR_PERSON:'四人间',FIVE_PERSON:'五人间',SIX_PERSON:'六人间',OTHER:'其他房型' } as Record<string,string>)[String(value)] ?? String(value) }
function roommateCount(room: DataObject) { return Number(room.activeResidentCount ?? room.assigned_count ?? 0) }
function recommendationReasons(room: DataObject) { return ((room.recommendationReasons ?? room.matches ?? []) as string[]).slice(0,6) }
function conflictReasons(room: DataObject) { return ((room.conflictReasons ?? room.warnings ?? []) as string[]).slice(0,7) }
function missingPreferenceCount(room: DataObject) { return Number(room.missingPreferenceCount ?? 0) }
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('宿舍匹配', 'ROOM MATCHING') }}</span><h2>{{ isTeamMode ? `为${memberCount}人队伍${isRoomMode?'选择寝室':'选择床位'}` : isRoomMode ? '选择寝室' : '选择宿舍床位' }}</h2><p v-if="isRoomMode">选择成功后只确定寝室归属，不显示或分配具体床位；入住成员自行协商实际床位。</p><p v-else>房间按偏好接近程度排序，进入房间后可直接点击三维图像中的床位进行选择。</p></div>
      <div v-if="!isTeamMode && recommendationEnabled" class="recommendation-actions">
        <select v-if="recommendationOptions.length > 1" v-model="recommendationStrategy" class="input recommendation-strategy-select" aria-label="推荐方式">
          <option v-for="option in recommendationOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <button class="button accent" :disabled="recommending" @click="requestRecommendation">{{ recommending ? '正在推荐…' : recommendationButtonLabel }}</button>
      </div>
    </div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="recommendationResult" class="panel recommendation-card"><div><span class="eyebrow">{{ recommendationResult.strategyName || '推荐结果' }}</span><h3>{{ recommendationResult.selectionMode === 'ROOM' ? '已找到推荐寝室' : '已找到推荐床位' }}</h3><p v-if="recommendationResult.explanation">{{ recommendationResult.explanation }}</p><p v-else>推荐结果已按照当前批次允许的方式生成。</p></div><button class="button primary" @click="openRoom((recommendationResult.room as DataObject))">{{ recommendationResult.selectionMode === 'ROOM' ? '选择推荐寝室' : '查看推荐房间' }}</button></section>

    <section class="panel filter-bar room-filter-bar">
      <label class="search-field room-filter-search"><span>搜索房间</span><input v-model="keyword" class="input" placeholder="输入楼栋或房间号" /></label>
      <label class="room-filter-field"><span>筛选楼层</span><select v-model="floorFilter" class="input"><option value="">全部楼层</option><option v-for="floor in floorOptions" :key="floor" :value="String(floor)">{{ floor }}层</option></select></label>
      <label class="room-filter-field"><span>最少剩余名额</span><select v-model.number="minimumAvailableBeds" class="input"><option :value="0">不限</option><option v-for="count in 5" :key="count" :value="count">至少{{ count }}个</option></select></label>
      <div class="filter-summary"><strong>{{ filteredRooms.length }}</strong><span>个可选房间</span></div>
    </section>

    <p v-if="loading" class="panel empty-state">正在计算候选宿舍…</p><p v-else-if="filteredRooms.length===0" class="panel empty-state">当前没有符合筛选条件的房间。</p>
    <div v-else class="room-grid compact-room-grid" :class="{ 'room-grid-room-mode': isRoomMode }">
      <article v-for="room in filteredRooms" :key="String(room.id)" class="panel room-card" :class="{ 'room-card-compact': isRoomMode }">
        <div class="room-card-head"><div><span class="eyebrow">{{ room.building_name }}</span><h3>{{ room.room_number }}室</h3></div><span v-if="hasFeature('P2_ROOM_RECOMMENDATION')" class="score-ring score-ring-with-label"><small>匹配度</small><strong>{{ Number(room.matchScore).toFixed(0) }}分</strong></span></div>
        <div class="room-facts"><span>{{ roomType(room.room_type) }}</span><span>剩余{{ room.availableCount }}{{ isRoomMode?'个名额':'张床位' }}</span><span>{{ room.floor_number }}层</span></div>
        <div class="roommate-summary"><div class="roommate-summary-head"><strong>当前在住与偏好</strong><span>{{ roommateCount(room)>0?`已有${roommateCount(room)}人`:'当前空房' }}</span></div><div v-if="missingPreferenceCount(room)>0" class="tag-row"><span class="tag warning">{{ missingPreferenceCount(room) }}名同学未填写偏好</span></div><div v-if="hasFeature('P2_ROOM_RECOMMENDATION') && recommendationReasons(room).length" class="tag-row"><span v-for="tag in recommendationReasons(room)" :key="tag" class="tag positive">{{ tag }}</span></div><div v-if="conflictReasons(room).length" class="tag-row"><span v-for="tag in conflictReasons(room)" :key="tag" class="tag warning">{{ tag }}</span></div></div>
        <button class="button primary full" :disabled="selectingRoomId===Number(room.id)" @click="openRoom(room)">{{ selectingRoomId===Number(room.id)?'正在确认…':isRoomMode?(isTeamMode?'队伍选择此寝室':'选择此寝室'):(isTeamMode?'选择队伍床位':'查看床位布局') }}</button>
      </article>
    </div>

    <div v-if="showPersonalExitConfirm" class="modal-overlay"><section class="modal-card confirmation-dialog"><h3>{{ t('team.personalExit.title') }}</h3><p>{{ t('team.personalExit.message') }}</p><div class="button-row"><button class="button ghost" @click="router.back()">{{ t('common.cancel') }}</button><button class="button primary" :disabled="preparingPersonalSelection" @click="confirmPersonalSelection">{{ t('common.confirm') }}</button></div></section></div>

    <div v-if="preferencePromptVisible" class="modal-overlay" @click.self="preferencePromptVisible = false"><section class="modal-card preference-warning-dialog" role="dialog" aria-modal="true"><span class="eyebrow">偏好检查</span><h3>尚未填写个人偏好</h3><p>填写偏好后才能获得更准确的室友匹配、冲突提醒和床位类型提示。</p><div class="button-row"><button class="button secondary" @click="router.push('/student/preferences')">先填写偏好</button><button v-if="selectionReadiness.allowWithoutQuestionnaire" class="button primary" @click="continueWithoutPreference">仍然继续选寝</button></div></section></div>

    <div v-if="roomSelectionTarget" class="modal-overlay room-selection-overlay" @click.self="closeRoomSelectionConfirm"><section class="modal-card room-selection-dialog" role="dialog" aria-modal="true" aria-labelledby="room-selection-title"><div class="room-selection-head"><div><span class="eyebrow">确认寝室</span><h3 id="room-selection-title">确认选择 {{ roomSelectionTarget.building_name }} {{ roomSelectionTarget.room_number }}</h3><p>{{ isTeamMode ? `本次将为${memberCount}名队员确认同一寝室归属。` : '本次只确认你的寝室归属。' }}</p></div><button class="modal-close" type="button" :disabled="selectingRoomId !== null" @click="closeRoomSelectionConfirm">×</button></div><div class="room-selection-summary"><article><span>房型</span><strong>{{ roomType(roomSelectionTarget.room_type) }}</strong></article><article><span>楼层</span><strong>{{ roomSelectionTarget.floor_number }}层</strong></article><article><span>剩余名额</span><strong>{{ roomSelectionTarget.availableCount }}</strong></article></div><div class="room-selection-notice"><strong>不会自动分配具体床位</strong><p>系统只记录寝室归属，入住后由寝室成员自行协商实际床位。</p></div><div class="button-row room-selection-actions"><button class="button ghost" type="button" :disabled="selectingRoomId !== null" @click="closeRoomSelectionConfirm">取消</button><button class="button primary" type="button" :disabled="selectingRoomId !== null" @click="confirmRoomSelection">{{ selectingRoomId !== null ? '正在确认…' : isTeamMode ? '确认队伍选择' : '确认选择寝室' }}</button></div></section></div>

    <div v-if="selectionSuccess" class="modal-overlay room-selection-overlay selection-success-overlay"><section class="modal-card room-selection-dialog selection-success-dialog" role="dialog" aria-modal="true"><div class="success-symbol">✓</div><span class="eyebrow">选择已完成</span><h3>寝室选择成功</h3><p>你已成功选择 <strong>{{ selectionSuccess.building_name }} {{ selectionSuccess.room_number }}室</strong>。</p><div class="room-selection-notice"><strong>当前状态已更新</strong><p>系统已经保存寝室归属，首页和分配结果页会显示该寝室。具体床位由寝室成员入住后协商。</p></div><div class="button-row room-selection-actions"><button class="button ghost" type="button" @click="returnStudentHome">返回首页</button><button class="button primary" type="button" @click="viewSelectionResult">{{ isTeamMode ? '查看我的队伍' : '查看分配结果' }}</button></div></section></div>
  </div>
</template>

<style scoped>
.recommendation-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
.recommendation-strategy-select { width: auto; min-width: 150px; }
.score-ring-with-label { display: grid; place-items: center; min-width: 78px; min-height: 78px; padding: 7px; text-align: center; line-height: 1.1; }
.score-ring-with-label small { display: block; font-size: 11px; font-weight: 600; opacity: .76; }
.score-ring-with-label strong { display: block; margin-top: 3px; font-size: 17px; }
.room-grid-room-mode { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.room-card-compact { padding: 16px; gap: 12px; }
.room-card-compact .score-ring-with-label { min-width: 66px; min-height: 66px; }
.room-card-compact .roommate-summary { padding: 10px; }
.preference-warning-dialog { width: min(520px, calc(100vw - 32px)); padding: 24px; }
.room-selection-overlay { z-index: 1250; padding: 30px; background: rgba(9, 23, 48, 0.78) !important; backdrop-filter: blur(7px) !important; }
.room-selection-dialog { width: min(680px, calc(100vw - 60px)); padding: 24px; border: 1px solid var(--line); border-radius: 26px; background: var(--panel, #fff); }
.room-selection-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 18px; }
.room-selection-head h3 { margin: 6px 0; }.room-selection-head p { margin: 0; color: var(--muted); }
.room-selection-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0; }
.room-selection-summary article { padding: 14px; border: 1px solid var(--line); border-radius: 14px; background: var(--soft); }
.room-selection-summary span,.room-selection-summary strong { display: block; }.room-selection-summary span { color: var(--muted); font-size: 12px; }.room-selection-summary strong { margin-top: 5px; }
.room-selection-notice { padding: 15px; border: 1px solid #d7e4f8; border-radius: 14px; background: #f3f8ff; color: #36577f; }.room-selection-notice p { margin: 6px 0 0; line-height: 1.55; }
.room-selection-actions { justify-content: flex-end; margin-top: 20px; }
.selection-success-dialog { text-align: center; width: min(560px, calc(100vw - 60px)); }
.selection-success-dialog .room-selection-notice { margin-top: 20px; text-align: left; }
.success-symbol { width: 64px; height: 64px; display: grid; place-items: center; margin: 0 auto 14px; border-radius: 50%; color: #fff; background: #19a278; font-size: 30px; font-weight: 800; box-shadow: 0 12px 28px rgba(25,162,120,.28); }
@media (max-width: 640px) { .recommendation-actions { width: 100%; justify-content: stretch; }.recommendation-strategy-select,.recommendation-actions .button { width: 100%; }.room-selection-overlay { padding: 10px; }.room-selection-dialog { width: 100%; padding: 18px; border-radius: 22px; }.room-selection-summary { grid-template-columns: 1fr; } }
</style>
