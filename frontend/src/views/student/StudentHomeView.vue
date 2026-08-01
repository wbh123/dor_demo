<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const profile = ref<DataObject>({})
const currentActivity = ref<DataObject | null>(null)
const questionnaire = ref<DataObject>({})
const assignmentResult = ref<DataObject>({ assigned: false })
const loading = ref(true)
const error = ref('')

const questions = computed(() => (questionnaire.value.questions ?? []) as DataObject[])
const savedAnswers = computed(() => (questionnaire.value.answers ?? []) as DataObject[])
const questionnaireStarted = computed(() => Boolean(currentActivity.value?.questionnaire_started))
const canEditQuestionnaire = computed(() =>
  ['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(currentActivity.value?.batch_status)),
)
const canSelectRoom = computed(() => String(currentActivity.value?.batch_status) === 'OPEN')
const assigned = computed(() => Boolean(assignmentResult.value.assigned))
const assignment = computed(() => (assignmentResult.value.assignment ?? {}) as DataObject)
const currentActivityId = computed(() => Number(currentActivity.value?.id ?? 0))

const answerSummary = computed(() => {
  const questionById = new Map(questions.value.map((question) => [Number(question.id), question]))
  return savedAnswers.value
    .map((answer) => {
      const question = questionById.get(Number(answer.question_id))
      if (!question) return null
      let value: unknown = answer.answer_json
      try {
        value = typeof value === 'string' ? JSON.parse(value) : value
      } catch {
        // 保留原始值用于展示。
      }
      return {
        code: String(question.question_code),
        label: summaryLabel(question),
        value: displayAnswer(question, value),
      }
    })
    .filter((item): item is { code: string; label: string; value: string } => Boolean(item))
    .slice(0, 8)
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, activityResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),
      api.get<ListSuccessResponse>('/api/v1/student/batches'),
    ])
    profile.value = (profileResponse.data.data ?? {}) as DataObject
    const activities = (activityResponse.data.data ?? []) as DataObject[]
    currentActivity.value = chooseCurrentActivity(activities)

    if (currentActivity.value) {
      const id = activityId(currentActivity.value)
      const requests: Promise<unknown>[] = [loadAssignment(id)]
      if (['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(currentActivity.value.batch_status))) {
        requests.push(loadQuestionnaire(id))
      }
      await Promise.all(requests)
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadQuestionnaire(id: number) {
  const response = await api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${id}/questionnaire`)
  questionnaire.value = (response.data.data ?? {}) as DataObject
}

async function loadAssignment(id: number) {
  const response = await api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${id}/assignment`)
  assignmentResult.value = (response.data.data ?? { assigned: false }) as DataObject
}

function chooseCurrentActivity(activities: DataObject[]) {
  return activities.find((item) => ['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(item.batch_status)))
    ?? activities.find((item) => item.assigned)
    ?? activities.find((item) => ['CLOSED', 'ALLOCATING', 'FINISHED'].includes(String(item.batch_status)))
    ?? null
}

function activityId(activity: DataObject) {
  return Number(activity.id)
}

function summaryLabel(question: DataObject) {
  const labels: Record<string, string> = {
    SLEEP_TIME: '入睡时间',
    WAKE_TIME: '起床时间',
    NAP_HABIT: '午休习惯',
    SLEEP_SENSITIVITY: '睡眠敏感度',
    NOISE_TOLERANCE: '噪声接受度',
    CLEANING_FREQUENCY: '清洁频率',
    TIDINESS_REQUIREMENT: '整洁要求',
    AC_TEMPERATURE: '空调温度',
    VENTILATION: '通风偏好',
    STUDY_FREQUENCY: '宿舍学习',
    GAMING_VOICE: '游戏语音',
    SOCIAL_ACTIVITY: '社交活跃度',
    SMOKING_ACCEPTANCE: '室友吸烟',
    BED_PREFERENCE: '床位偏好',
  }
  return labels[String(question.question_code)] ?? String(question.question_text)
}

function displayAnswer(question: DataObject, value: unknown) {
  const code = String(question.question_code)
  if (code === 'SMOKING_ACCEPTANCE') {
    return { ACCEPT: '接受', REJECT: '不接受', ANY: '均可' }[String(value)] ?? '未填写'
  }
  if (code === 'BED_PREFERENCE') {
    return {
      ANY: '无特别偏好',
      LOFT_BED_DESK: '上床下桌',
      BUNK_UPPER: '上下铺上铺',
      BUNK_LOWER: '上下铺下铺',
    }[String(value)] ?? String(value)
  }
  if (code === 'NAP_HABIT') {
    return ['基本不午休', '偶尔午休', '经常午休'][Number(value)] ?? String(value)
  }
  if (question.question_type === 'TIME') return String(value)
  if (code === 'AC_TEMPERATURE') return `${value}℃`
  if (typeof value === 'number') return ['非常低', '较低', '适中', '较高', '非常高'][value - 1] ?? String(value)
  return String(value ?? '未填写')
}

function bedTypeText(value: unknown) {
  return {
    LOFT_BED_DESK: '上床下桌',
    BUNK_UPPER: '上下铺上铺',
    BUNK_LOWER: '上下铺下铺',
  }[String(value)] ?? String(value ?? '')
}
</script>

<template>
  <div class="student-home-grid">
    <section class="welcome-card panel gradient-panel">
      <div>
        <span class="eyebrow light">欢迎回来</span>
        <h2>{{ profile.student_name || '同学' }}</h2>
        <p>{{ profile.student_number }} · {{ profile.major_code }} {{ profile.major_name }}</p>
      </div>
      <div class="welcome-mark">{{ profile.gender === 'M' ? '男寝' : '女寝' }}</div>
    </section>

    <section class="panel assignment-summary">
      <div>
        <span class="eyebrow">MY DORMITORY</span>
        <h2>我的住宿结果</h2>
      </div>
      <template v-if="assigned">
        <div class="assignment-place">
          <strong>{{ assignment.building_name }}</strong>
          <span>{{ assignment.room_number }} 室 · {{ assignment.bed_code }} 床位</span>
        </div>
        <p>{{ assignment.floor_number }}层 · {{ bedTypeText(assignment.bed_type) }}</p>
        <RouterLink v-if="currentActivityId" class="button secondary" :to="`/student/batches/${currentActivityId}/assignment`">查看完整结果</RouterLink>
      </template>
      <template v-else>
        <p>尚未确定宿舍和床位。完成问卷后，可在开放选寝期间选择合适的房间。</p>
        <RouterLink
          v-if="canSelectRoom && currentActivityId"
          class="button primary"
          :to="`/student/batches/${currentActivityId}/rooms`"
        >进入选寝</RouterLink>
      </template>
    </section>

    <p v-if="loading" class="panel empty-state home-span-2">正在读取个人选寝信息…</p>
    <p v-else-if="error" class="alert error home-span-2">{{ error }}</p>

    <section v-else class="panel home-span-2">
      <div class="section-head split-title">
        <div>
          <span class="eyebrow">LIFESTYLE PREFERENCES</span>
          <h2>我的生活习惯偏好</h2>
          <p>你可以随时查看已填写内容，并在允许修改时重新保存。</p>
        </div>
        <RouterLink
          v-if="currentActivityId && canEditQuestionnaire"
          class="button secondary"
          :to="`/student/batches/${currentActivityId}/questionnaire`"
        >{{ questionnaireStarted ? '修改问卷' : '填写问卷' }}</RouterLink>
      </div>

      <p v-if="!currentActivity" class="empty-state">当前没有需要参与的选寝活动。</p>
      <p v-else-if="answerSummary.length === 0" class="empty-state">尚未填写生活习惯问卷。</p>
      <div v-else class="preference-summary-grid">
        <article v-for="item in answerSummary" :key="item.code" class="preference-summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <div v-if="currentActivity && !assigned" class="button-row">
        <RouterLink
          v-if="canSelectRoom"
          class="button primary"
          :to="`/student/batches/${currentActivityId}/rooms`"
        >选择宿舍和床位</RouterLink>
        <RouterLink v-if="canSelectRoom && currentActivity.allow_team" class="button ghost" to="/student/teams">组队选寝</RouterLink>
      </div>
    </section>

    <section class="panel info-card home-span-2">
      <span class="eyebrow">规则说明</span>
      <h3>请在规定时间内完成选择</h3>
      <p>床位在确认前只会短暂保留。确认成功后，首页会立即显示你的宿舍和床位；如需调整，请联系管理人员。</p>
    </section>
  </div>
</template>
