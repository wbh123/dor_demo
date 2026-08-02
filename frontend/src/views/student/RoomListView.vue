<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const memberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const rooms = ref<DataObject[]>([])
const activePersonalTeam = ref<DataObject | null>(null)
const showPersonalExitConfirm = ref(false)
const loading = ref(true)
const preparingPersonalSelection = ref(false)
const error = ref('')
const randomResult = ref<DataObject | null>(null)
const keyword = ref('')
const floorFilter = ref('')
const minimumAvailableBeds = ref(0)
const { t, subtitle, translateError } = useI18n()

const floorOptions = computed(() =>
  [...new Set(rooms.value.map((room) => Number(room.floor_number)))]
    .filter((floor) => Number.isFinite(floor))
    .sort((left, right) => left - right),
)

const filteredRooms = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rooms.value.filter((room) => {
    if (isTeamMode.value && Number(room.availableCount) < memberCount) return false
    if (floorFilter.value
      && Number(room.floor_number) !== Number(floorFilter.value)) return false
    if (Number(room.availableCount) < minimumAvailableBeds.value) return false
    if (term && !`${room.building_name} ${room.room_number}`.toLowerCase().includes(term)) {
      return false
    }
    return true
  })
})

onMounted(initialize)

async function initialize() {
  if (isTeamMode.value) {
    await load()
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/student/teams')
    const teams = (response.data.data ?? []) as DataObject[]
    activePersonalTeam.value = teams.find((team) => Number(team.batch_id) === batchId) ?? null
    if (activePersonalTeam.value) {
      showPersonalExitConfirm.value = true
      loading.value = false
      return
    }
    await load()
  } catch (reason) {
    error.value = translateError(reason)
    loading.value = false
  }
}

async function confirmPersonalSelection() {
  preparingPersonalSelection.value = true
  error.value = ''
  try {
    await api.post(`/api/v1/student/batches/${batchId}/personal-selection/prepare`)
    showPersonalExitConfirm.value = false
    activePersonalTeam.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    preparingPersonalSelection.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>(
      `/api/v1/student/batches/${batchId}/rooms`,
    )
    rooms.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

async function randomRecommend() {
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/student/batches/${batchId}/random-recommendation`,
    )
    randomResult.value = (response.data.data ?? {}) as DataObject
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function openRoom(roomId: unknown) {
  router.push({
    path: `/student/batches/${batchId}/rooms/${Number(roomId)}`,
    query: isTeamMode.value
      ? { teamId: String(teamId), memberCount: String(memberCount) }
      : undefined,
  })
}

function roomType(value: unknown) {
  return {
    FOUR_PERSON: '四人间',
    FIVE_PERSON: '五人间',
    SIX_PERSON: '六人间',
    OTHER: '其他房型',
  }[String(value)] ?? String(value)
}

function roommateCount(room: DataObject) {
  return Number(room.assigned_count ?? 0)
}

function recommendationReasons(room: DataObject) {
  return ((room.recommendationReasons ?? room.matches ?? []) as string[]).slice(0, 3)
}

function conflictReasons(room: DataObject) {
  return ((room.conflictReasons ?? room.warnings ?? []) as string[]).slice(0, 2)
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">{{ subtitle('宿舍匹配', 'ROOM MATCHING') }}</span>
        <h2>{{ isTeamMode ? `为${memberCount}人队伍选择房间` : '选择宿舍房间' }}</h2>
        <p v-if="isTeamMode">只展示能够容纳全部已确认成员的房间，队伍需要在同一房间完成选择。</p>
        <p v-else>房间按个人偏好接近程度排序，可结合楼层和剩余铺位快速筛选。</p>
      </div>
      <button v-if="!isTeamMode" class="button accent" @click="randomRecommend">帮我推荐一个</button>
    </div>

    <section v-if="randomResult" class="panel recommendation-card">
      <div>
        <span class="eyebrow">{{ subtitle('推荐结果', 'RECOMMENDATION') }}</span>
        <h3>已找到一个当前可选床位</h3>
        <p>{{ randomResult.explanation }}</p>
      </div>
      <button class="button primary" @click="openRoom((randomResult.room as DataObject)?.id)">查看推荐房间</button>
    </section>

    <section class="panel filter-bar room-filter-bar">
      <label class="search-field room-filter-search">
        <span>搜索房间</span>
        <input v-model="keyword" class="input" placeholder="输入楼栋或房间号" />
      </label>
      <label class="room-filter-field">
        <span>筛选楼层</span>
        <select v-model="floorFilter" class="input">
          <option value="">全部楼层</option>
          <option v-for="floor in floorOptions" :key="floor" :value="String(floor)">
            {{ floor }} 层
          </option>
        </select>
      </label>
      <label class="room-filter-field">
        <span>最少剩余铺位</span>
        <select v-model.number="minimumAvailableBeds" class="input">
          <option :value="0">不限</option>
          <option :value="1">至少 1 个</option>
          <option :value="2">至少 2 个</option>
          <option :value="3">至少 3 个</option>
          <option :value="4">至少 4 个</option>
          <option :value="5">至少 5 个</option>
        </select>
      </label>
      <div class="filter-summary">
        <strong>{{ filteredRooms.length }}</strong>
        <span>个可选房间</span>
      </div>
    </section>

    <p v-if="loading" class="panel empty-state">正在计算候选宿舍…</p>
    <p v-else-if="error" class="alert error">{{ error }}</p>
    <p v-else-if="filteredRooms.length === 0" class="panel empty-state">当前没有符合筛选条件的房间。</p>

    <div v-else class="room-grid compact-room-grid">
      <article v-for="room in filteredRooms" :key="String(room.id)" class="panel room-card">
        <div class="room-card-head">
          <div>
            <span class="eyebrow">{{ room.building_name }}</span>
            <h3>{{ room.room_number }} 室</h3>
          </div>
          <span class="score-ring" :title="`匹配度 ${Number(room.matchScore).toFixed(0)}`">{{ Number(room.matchScore).toFixed(0) }}</span>
        </div>
        <div class="room-facts">
          <span>{{ roomType(room.room_type) }}</span>
          <span>剩余 {{ room.availableCount }} 床</span>
          <span>{{ room.floor_number }} 层</span>
        </div>

        <div class="roommate-summary">
          <div class="roommate-summary-head">
            <strong>室友偏好</strong>
            <span>{{ roommateCount(room) > 0 ? `已有 ${roommateCount(room)} 人` : '当前空房' }}</span>
          </div>
          <template v-if="roommateCount(room) > 0">
            <div v-if="recommendationReasons(room).length" class="tag-row recommendation-reasons">
              <span v-for="tag in recommendationReasons(room)" :key="`positive-${tag}`" class="tag positive">{{ tag }}</span>
            </div>
            <div v-if="conflictReasons(room).length" class="tag-row conflict-reasons">
              <span v-for="tag in conflictReasons(room)" :key="`warning-${tag}`" class="tag warning">{{ tag }}</span>
            </div>
          </template>
          <p v-else class="roommate-empty">暂无室友偏好信息，可优先选择喜欢的床位。</p>
        </div>

        <button class="button primary full" @click="openRoom(room.id)">
          {{ isTeamMode ? '选择队伍床位' : '查看床位布局' }}
        </button>
      </article>
    </div>

    <div v-if="showPersonalExitConfirm" class="modal-overlay" role="presentation">
      <section class="modal-card confirmation-dialog" role="dialog" aria-modal="true">
        <h3>{{ t('team.personalExit.title') }}</h3>
        <p>{{ t('team.personalExit.message') }}</p>
        <div class="button-row">
          <button class="button ghost" type="button" @click="router.back()">{{ t('common.cancel') }}</button>
          <button class="button primary" :disabled="preparingPersonalSelection" @click="confirmPersonalSelection">
            {{ t('common.confirm') }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
