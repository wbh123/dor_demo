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
      const value = parseAnswer(answer.answer_json)
      return {
        code: String(question.question_code),
        label: summaryLabel(question),
        value: displayAnswer(question, value),
        rawValue: value,
      }
    })
    .filter((item): item is {
      code: string
      label: string
      value: string
      rawValue: unknown
    } => Boolean(item))
})

const rawAnswerByCode = computed(() =>
  new Map(answerSummary.value.map((item) => [item.code, item.rawValue])),
)

const preferenceProfileSummary = computed(() => {
  if (!answerSummary.value.length) return '完成个人偏好后，这里会形成你的宿舍生活画像。'
  const descriptions: string[] = []
  const sleepMinutes = timeMinutes(rawAnswerByCode.value.get('SLEEP_TIME'))
  if (sleepMinutes !== null) {
    if (sleepMinutes >= 0 && sleepMinutes < 60) descriptions.push('作息偏晚')
    else if (sleepMinutes >= 1380) descriptions.push('作息偏晚')
    else if (sleepMinutes <= 1350) descriptions.push('作息偏早')
    else descriptions.push('作息时间适中')
  }
  if (numericAnswer('SLEEP_SENSITIVITY') >= 4) descriptions.push('睡眠较敏感')
  if (numericAnswer('TIDINESS_REQUIREMENT') >= 4) descriptions.push('重视宿舍整洁')
  if (numericAnswer('AFTER_LIGHTS_ACTIVITY') <= 1) descriptions.push('熄灯后偏好安静')
  if (numericAnswer('GAMING_VOICE') <= 2) descriptions.push('娱乐语音较少')
  if (numericAnswer('SUMMER_AC_OVERNIGHT') >= 3) descriptions.push('接受夏季整夜空调')
  if (numericAnswer('SUMMER_AC_OVERNIGHT') === 1) descriptions.push('不偏好夏季整夜空调')
  const selected = descriptions.slice(0, 5)
  return selected.length
    ? `你${selected.join('，')}，系统会优先推荐相处节奏更接近的室友。`
    : '你的偏好较为均衡，系统会综合多个维度推荐合适的室友。'
})

const preferenceProfileTags = computed(() => {
  const tags: string[] = []
  const push = (condition: boolean, label: string) => {
    if (condition && !tags.includes(label)) tags.push(label)
  }
  push(numericAnswer('SLEEP_SENSITIVITY') >= 4, '睡眠敏感')
  push(numericAnswer('NOISE_TOLERANCE') <= 2, '偏好安静')
  push(numericAnswer('CLEANING_FREQUENCY') >= 4, '勤于清洁')
  push(numericAnswer('TIDINESS_REQUIREMENT') >= 4, '重视整洁')
  push(numericAnswer('STUDY_FREQUENCY') >= 4, '常在宿舍学习')
  push(numericAnswer('GAMING_VOICE') <= 2, '少语音娱乐')
  push(numericAnswer('AFTER_LIGHTS_ACTIVITY') <= 1, '熄灯后安静')
  push(numericAnswer('ALARM_SNOOZE') <= 1, '单次闹钟')
  push(numericAnswer('STRONG_FOOD_ODOR_ACCEPTANCE') <= 1, '不接受重气味食物')
  push(String(rawAnswerByCode.value.get('SMOKING_ACCEPTANCE')) === 'REJECT', '不接受吸烟')
  return tags.slice(0, 6)
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

function parseAnswer(value: unknown) {
  try {
    return typeof value === 'string' ? JSON.parse(value) : value
  } catch {
    return value
  }
}

function numericAnswer(code: string) {
  const value = Number(rawAnswerByCode.value.get(code))
  return Number.isFinite(value) ? value : 0
}

function timeMinutes(value: unknown) {
  const match = /^(\d{2}):(\d{2})$/.exec(String(value ?? ''))
  if (!match) return null
  return Number(match[1]) * 60 + Number(match[2])
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
    SUMMER_AC_OVERNIGHT: '夏季整夜空调',
    SUMMER_AC_TEMPERATURE: '夏季制冷温度',
    AC_TEMPERATURE: '夏季制冷温度',
    WINTER_HEATING_ACCEPTANCE: '冬季空调制热',
    WINTER_HEATING_TEMPERATURE: '冬季制热温度',
    AFTER_LIGHTS_ACTIVITY: '熄灯后活动',
    ALARM_SNOOZE: '闹钟习惯',
    STRONG_FOOD_ODOR_ACCEPTANCE: '重气味食物',
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
  const configured = (question.options ?? []) as DataObject[]
  const option = configured.find((item) =>
    String(item.option_code) === String(value)
    || (item.feature_value !== null
      && item.feature_value !== undefined
      && Number(item.feature_value) === Number(value)),
  )
  if (option) return String(option.option_text)

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
  if (code.includes('TEMPERATURE')) return `${value}℃`
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
        <p>尚未确定宿舍和床位。完善个人偏好后，可在开放选寝期间选择合适的房间。</p>
      </template>
    </section>

    <p v-if="loading" class="panel empty-state home-span-2">正在读取个人选寝信息…</p>
    <p v-else-if="error" class="alert error home-span-2">{{ error }}</p>

    <section v-else class="panel home-span-2 personal-preference-card">
      <div class="section-head split-title">
        <div>
          <span class="eyebrow">PERSONAL PREFERENCES</span>
          <h2>我的个人偏好</h2>
          <p>完整展示你的已保存偏好，并形成便于理解的宿舍生活画像。</p>
        </div>
        <RouterLink
          v-if="currentActivityId && canEditQuestionnaire"
          class="button secondary"
          :to="`/student/batches/${currentActivityId}/questionnaire`"
        >{{ questionnaireStarted ? '修改个人偏好' : '填写个人偏好' }}</RouterLink>
      </div>

      <p v-if="!currentActivity" class="empty-state">当前没有需要参与的选寝活动。</p>
      <p v-else-if="answerSummary.length === 0" class="empty-state">尚未填写个人偏好。</p>
      <template v-else>
        <div class="preference-profile-overview">
          <div>
            <span class="profile-caption">用户画像概述</span>
            <h3>{{ preferenceProfileSummary }}</h3>
          </div>
          <div v-if="preferenceProfileTags.length" class="profile-tag-row">
            <span v-for="tag in preferenceProfileTags" :key="tag" class="profile-tag">{{ tag }}</span>
          </div>
        </div>

        <dl class="personal-preference-list">
          <div v-for="item in answerSummary" :key="item.code" class="personal-preference-row">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </template>

      <div v-if="currentActivity && !assigned" class="student-primary-actions">
        <RouterLink
          v-if="canSelectRoom"
          class="button primary student-action-button"
          :to="`/student/batches/${currentActivityId}/rooms`"
        >选择宿舍和床位</RouterLink>
        <RouterLink
          v-if="canSelectRoom && currentActivity.allow_team"
          class="button accent student-action-button"
          to="/student/teams"
        >组队选寝</RouterLink>
      </div>
    </section>

    <section class="panel info-card home-span-2 compact-rule-card">
      <span class="eyebrow">规则说明</span>
      <h3>请在规定时间内完成选择</h3>
      <p>床位在确认前只会短暂保留，最终确认后会立即显示住宿结果。</p>
    </section>
  </div>
</template>
