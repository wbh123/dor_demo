<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const memberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const rooms = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const randomResult = ref<DataObject | null>(null)
const keyword = ref('')

const filteredRooms = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  const enoughBeds = rooms.value.filter((room) =>
    !isTeamMode.value || Number(room.availableCount) >= memberCount,
  )
  if (!term) return enoughBeds
  return enoughBeds.filter((room) =>
    `${room.building_name} ${room.room_number}`.toLowerCase().includes(term),
  )
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>(
      `/api/v1/student/batches/${batchId}/rooms`,
    )
    rooms.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '宿舍加载失败'
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
    error.value = reason instanceof Error ? reason.message : '随机推荐失败'
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

function roommateTags(room: DataObject) {
  const positives = ((room.matches ?? []) as string[]).slice(0, 3)
  const warnings = ((room.warnings ?? []) as string[]).slice(0, 2)
  return [...positives, ...warnings].slice(0, 3)
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">ROOM MATCHING</span>
        <h2>{{ isTeamMode ? `为${memberCount}人队伍选择房间` : '选择宿舍房间' }}</h2>
        <p v-if="isTeamMode">只展示能够容纳全部成员的房间，队伍需要在同一房间完成选择。</p>
        <p v-else>房间按生活习惯接近程度排序。已有室友时，会显示匿名偏好提示。</p>
      </div>
      <button v-if="!isTeamMode" class="button accent" @click="randomRecommend">帮我推荐一个</button>
    </div>

    <section v-if="randomResult" class="panel recommendation-card">
      <div>
        <span class="eyebrow">RECOMMENDATION</span>
        <h3>已找到一个当前可选床位</h3>
        <p>{{ randomResult.explanation }}</p>
      </div>
      <button class="button primary" @click="openRoom((randomResult.room as DataObject)?.id)">查看推荐房间</button>
    </section>

    <section class="panel filter-bar">
      <label class="search-field">
        <span>搜索房间</span>
        <input v-model="keyword" class="input" placeholder="输入楼栋或房间号" />
      </label>
      <div class="filter-summary">
        <strong>{{ filteredRooms.length }}</strong>
        <span>个可选房间</span>
      </div>
    </section>

    <p v-if="loading" class="panel empty-state">正在计算候选宿舍…</p>
    <p v-else-if="error" class="alert error">{{ error }}</p>
    <p v-else-if="filteredRooms.length === 0" class="panel empty-state">当前没有符合条件的房间。</p>

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
          <div v-if="roommateCount(room) > 0" class="tag-row">
            <span v-for="tag in roommateTags(room)" :key="tag" class="tag positive">{{ tag }}</span>
          </div>
          <p v-else class="roommate-empty">暂无室友偏好信息，可优先选择喜欢的床位。</p>
        </div>

        <button class="button primary full" @click="openRoom(room.id)">
          {{ isTeamMode ? '选择队伍床位' : '查看床位布局' }}
        </button>
      </article>
    </div>
  </div>
</template>
