<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PhoneDialCodeSelect from '../../components/common/PhoneDialCodeSelect.vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { bedTypeLabel } from '../../utils/bedLabels'
import { formatPhoneDisplay, normalizeInternationalPhone, splitInternationalPhone } from '../../utils/phoneCodes'

const profile = ref<DataObject>({})
const currentActivity = ref<DataObject | null>(null)
const questionnaire = ref<DataObject>({})
const assignmentResult = ref<DataObject>({ assigned: false })
const invitations = ref<DataObject[]>([])
const notifications = ref<DataObject[]>([])
const dismissedInvitationTokens = ref<string[]>([])
const loading = ref(true)
const invitationSubmitting = ref(false)
const phoneSaving = ref(false)
const showPhoneDialog = ref(false)
const phoneDialCode = ref('+86')
const phoneLocalNumber = ref('')
const error = ref('')
const phoneError = ref('')
const { hasFeature } = useFeatureAccess()

const {
  locale,
  isChinese,
  t,
  countryName,
  applyNationalityLocale,
  translateError,
} = useI18n()

const questions = computed(() => (questionnaire.value.questions ?? []) as DataObject[])
const profileAnswers = computed(() => (questionnaire.value.answers ?? {}) as Record<string, unknown>)
const profileAnswerEntries = computed(() => questions.value.flatMap((question) => {
  const code = String(question.question_code)
  if (!(code in profileAnswers.value)) return []
  return [{ question_id: question.id, answer_json: profileAnswers.value[code] } as DataObject]
}))
const questionnaireStarted = computed(() => answerSummary.value.length > 0 || Boolean(currentActivity.value?.questionnaire_started))
const canEditQuestionnaire = computed(() => true)
const profileInsightEnabled = computed(() => hasFeature('P2_STUDENT_PROFILE_INSIGHT'))
const canSelectRoom = computed(() => String(currentActivity.value?.batch_status) === 'OPEN')
const assigned = computed(() => Boolean(assignmentResult.value.assigned))
const assignment = computed(() => (assignmentResult.value.assignment ?? {}) as DataObject)
const currentActivityId = computed(() => Number(currentActivity.value?.id ?? 0))
const isForeignStudent = computed(() => String(profile.value.nationality_code ?? 'CN') !== 'CN')
const homeInvitation = computed(() => invitations.value.find((item) =>
  !dismissedInvitationTokens.value.includes(String(item.invitation_token))),
)
const unreadNotifications = computed(() => notifications.value.filter((item) => !item.read_at))

const answerSummary = computed(() => {
  const questionById = new Map(questions.value.map((question) => [Number(question.id), question]))
  return profileAnswerEntries.value
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
  if (!answerSummary.value.length) {
    return isChinese.value
      ? '完成个人偏好后，这里会形成你的宿舍生活画像。'
      : 'Your dormitory lifestyle profile will appear after you complete your preferences.'
  }
  const descriptions: string[] = []
  const sleepMinutes = timeMinutes(rawAnswerByCode.value.get('SLEEP_TIME'))
  if (sleepMinutes !== null) {
    if (sleepMinutes >= 1380 || sleepMinutes < 60) descriptions.push(local('作息偏晚', 'late sleep schedule'))
    else if (sleepMinutes <= 1350) descriptions.push(local('作息偏早', 'early sleep schedule'))
    else descriptions.push(local('作息时间适中', 'balanced sleep schedule'))
  }
  if (numericAnswer('SLEEP_SENSITIVITY') >= 4) descriptions.push(local('睡眠较敏感', 'sensitive sleeper'))
  if (numericAnswer('TIDINESS_REQUIREMENT') >= 4) descriptions.push(local('重视宿舍整洁', 'values tidiness'))
  if (numericAnswer('AFTER_LIGHTS_ACTIVITY') <= 1) descriptions.push(local('熄灯后偏好安静', 'prefers quiet after lights-out'))
  if (numericAnswer('GAMING_VOICE') <= 2) descriptions.push(local('娱乐语音较少', 'uses voice chat infrequently'))
  if (numericAnswer('SUMMER_AC_OVERNIGHT') >= 3) descriptions.push(local('接受夏季整夜空调', 'accepts overnight air conditioning'))
  if (numericAnswer('SUMMER_AC_OVERNIGHT') === 1) descriptions.push(local('不偏好夏季整夜空调', 'avoids overnight air conditioning'))
  const selected = descriptions.slice(0, 6)
  if (!selected.length) {
    return local(
      '你的偏好较为均衡，系统会综合多个维度推荐合适的室友。',
      'Your preferences are balanced, so the system will compare multiple dimensions when recommending roommates.',
    )
  }
  return isChinese.value
    ? `你${selected.join('，')}，系统会优先推荐相处节奏更接近的室友。`
    : `You have a ${selected.join(', ')}. The system will prioritize roommates with a similar daily rhythm.`
})

const preferenceProfileTags = computed(() => {
  const tags: string[] = []
  const push = (condition: boolean, zh: string, en: string) => {
    const label = local(zh, en)
    if (condition && !tags.includes(label)) tags.push(label)
  }
  push(numericAnswer('SLEEP_SENSITIVITY') >= 4, '睡眠敏感', 'Sensitive sleeper')
  push(numericAnswer('NOISE_TOLERANCE') <= 2, '偏好安静', 'Prefers quiet')
  push(numericAnswer('CLEANING_FREQUENCY') >= 4, '勤于清洁', 'Cleans frequently')
  push(numericAnswer('TIDINESS_REQUIREMENT') >= 4, '重视整洁', 'Values tidiness')
  push(numericAnswer('STUDY_FREQUENCY') >= 4, '常在宿舍学习', 'Studies in room')
  push(numericAnswer('GAMING_VOICE') <= 2, '少语音娱乐', 'Low voice-chat use')
  push(numericAnswer('AFTER_LIGHTS_ACTIVITY') <= 1, '熄灯后安静', 'Quiet after lights-out')
  push(numericAnswer('ALARM_SNOOZE') <= 1, '单次闹钟', 'Single alarm')
  push(numericAnswer('STRONG_FOOD_ODOR_ACCEPTANCE') <= 1, '不接受重气味食物', 'Avoids strong food odors')
  push(String(rawAnswerByCode.value.get('SMOKING_ACCEPTANCE')) === 'REJECT', '不接受吸烟', 'No smoking')
  return tags
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, activityResponse, invitationResponse, notificationResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),
      api.get<ListSuccessResponse>('/api/v1/student/batches'),
      api.get<ListSuccessResponse>('/api/v1/student/team-invitations'),
      api.get<ListSuccessResponse>('/api/v1/student/notifications'),
    ])
    profile.value = (profileResponse.data.data ?? {}) as DataObject
    const phone = splitInternationalPhone(profile.value.phone_number, profile.value.nationality_code)
    phoneDialCode.value = phone.dialCode
    phoneLocalNumber.value = phone.localNumber
    applyNationalityLocale(profile.value.nationality_code)
    invitations.value = (invitationResponse.data.data ?? []) as DataObject[]
    notifications.value = (notificationResponse.data.data ?? []) as DataObject[]
    const activities = (activityResponse.data.data ?? []) as DataObject[]
    currentActivity.value = chooseCurrentActivity(activities)

    const requests: Promise<unknown>[] = [loadGlobalPreferences()]
    if (currentActivity.value) {
      const id = activityId(currentActivity.value)
      requests.push(loadAssignment(id))
    }
    await Promise.all(requests)
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

async function loadGlobalPreferences() {
  const response = await api.get<ObjectSuccessResponse>('/api/v1/student/preferences')
  questionnaire.value = (response.data.data ?? {}) as DataObject
}

async function loadAssignment(id: number) {
  const response = await api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${id}/assignment`)
  assignmentResult.value = (response.data.data ?? { assigned: false }) as DataObject
}

function dismissHomeInvitation() {
  if (!homeInvitation.value) return
  dismissedInvitationTokens.value = [
    ...dismissedInvitationTokens.value,
    String(homeInvitation.value.invitation_token),
  ]
}

async function respondHomeInvitation(accepted: boolean) {
  if (!homeInvitation.value || invitationSubmitting.value) return
  invitationSubmitting.value = true
  error.value = ''
  const token = String(homeInvitation.value.invitation_token)
  try {
    await api.post('/api/v1/student/team-invitations/respond', {
      invitationToken: token,
      accepted,
    })
    invitations.value = invitations.value.filter((item) => String(item.invitation_token) !== token)
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    invitationSubmitting.value = false
  }
}

async function markNotificationRead(notification: DataObject) {
  if (notification.read_at) return
  try {
    await api.post(`/api/v1/student/notifications/${Number(notification.id)}/read`)
    notification.read_at = new Date().toISOString()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function openPhoneEditor() {
  const phone = splitInternationalPhone(profile.value.phone_number, profile.value.nationality_code)
  phoneDialCode.value = phone.dialCode
  phoneLocalNumber.value = phone.localNumber
  phoneError.value = ''
  showPhoneDialog.value = true
}

async function savePhoneNumber() {
  phoneError.value = ''
  phoneSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/student/profile', {
      phoneNumber: normalizeInternationalPhone(phoneDialCode.value, phoneLocalNumber.value),
    })
    profile.value = (response.data.data ?? profile.value) as DataObject
    showPhoneDialog.value = false
  } catch (reason) {
    phoneError.value = translateError(reason)
  } finally {
    phoneSaving.value = false
  }
}

function notificationParameters(notification: DataObject) {
  try {
    return typeof notification.parameters_json === 'string'
      ? JSON.parse(notification.parameters_json) as Record<string, unknown>
      : (notification.parameters_json ?? {}) as Record<string, unknown>
  } catch {
    return {}
  }
}

function notificationTitle(notification: DataObject) {
  return t(String(notification.title_key), notificationParameters(notification))
}

function notificationMessage(notification: DataObject) {
  return t(String(notification.message_key), notificationParameters(notification))
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

function local(zh: string, en: string) {
  return locale.value === 'zh-CN' ? zh : en
}

function summaryLabel(question: DataObject) {
  const labels: Record<string, [string, string]> = {
    SLEEP_TIME: ['入睡时间', 'Sleep time'],
    WAKE_TIME: ['起床时间', 'Wake time'],
    NAP_HABIT: ['午休习惯', 'Nap habit'],
    SLEEP_SENSITIVITY: ['睡眠敏感度', 'Sleep sensitivity'],
    NOISE_TOLERANCE: ['噪声接受度', 'Noise tolerance'],
    CLEANING_FREQUENCY: ['清洁频率', 'Cleaning frequency'],
    TIDINESS_REQUIREMENT: ['整洁要求', 'Tidiness requirement'],
    SUMMER_AC_OVERNIGHT: ['夏季整夜空调', 'Overnight summer A/C'],
    SUMMER_AC_TEMPERATURE: ['夏季制冷温度', 'Summer A/C temperature'],
    AC_TEMPERATURE: ['夏季制冷温度', 'Summer A/C temperature'],
    WINTER_HEATING_ACCEPTANCE: ['冬季空调制热', 'Winter heating'],
    WINTER_HEATING_TEMPERATURE: ['冬季制热温度', 'Winter heating temperature'],
    AFTER_LIGHTS_ACTIVITY: ['熄灯后活动', 'After-lights activity'],
    ALARM_SNOOZE: ['闹钟习惯', 'Alarm habit'],
    STRONG_FOOD_ODOR_ACCEPTANCE: ['重气味食物', 'Strong food odors'],
    VENTILATION: ['通风偏好', 'Ventilation'],
    STUDY_FREQUENCY: ['宿舍学习', 'Studying in room'],
    GAMING_VOICE: ['游戏语音', 'Gaming voice chat'],
    SOCIAL_ACTIVITY: ['社交活跃度', 'Social activity'],
    SMOKING_ACCEPTANCE: ['室友吸烟', 'Roommate smoking'],
    BED_PREFERENCE: ['床位偏好', 'Bed preference'],
  }
  const pair = labels[String(question.question_code)]
  return pair ? local(pair[0], pair[1]) : String(question.question_text)
}

function displayAnswer(question: DataObject, value: unknown) {
  const configured = (question.options ?? []) as DataObject[]
  const option = configured.find((item) =>
    String(item.option_code) === String(value)
    || (item.feature_value !== null
      && item.feature_value !== undefined
      && Number(item.feature_value) === Number(value)),
  )
  if (option) return t(String(option.option_text))
  const code = String(question.question_code)
  if (code === 'SMOKING_ACCEPTANCE') {
    return { ACCEPT: local('接受', 'Accept'), REJECT: local('不接受', 'Reject'), ANY: local('均可', 'Any') }[String(value)] ?? local('未填写', 'Not provided')
  }
  if (code === 'BED_PREFERENCE') {
    return {
      ANY: local('无特别偏好', 'No preference'),
      LOFT_BED_DESK: local('上床下桌', 'Loft bed with desk'),
      BUNK_UPPER: local('上下铺上铺', 'Upper bunk'),
      BUNK_LOWER: local('上下铺下铺', 'Lower bunk'),
    }[String(value)] ?? String(value)
  }
  if (code === 'NAP_HABIT') {
    const labels = isChinese.value
      ? ['基本不午休', '偶尔午休', '经常午休']
      : ['Rarely naps', 'Sometimes naps', 'Often naps']
    return labels[Number(value)] ?? String(value)
  }
  if (question.question_type === 'TIME') return String(value)
  if (code.includes('TEMPERATURE')) return `${value}℃`
  if (typeof value === 'number') {
    const levels = isChinese.value
      ? ['非常低', '较低', '适中', '较高', '非常高']
      : ['Very low', 'Low', 'Moderate', 'High', 'Very high']
    return levels[value - 1] ?? String(value)
  }
  return String(value ?? local('未填写', 'Not provided'))
}

function bedTypeText(value: unknown) { return bedTypeLabel(value) }
</script>

<template>
  <div class="student-home-grid">
    <section class="welcome-card panel gradient-panel compact-home-top-card">
      <span class="home-corner-badge">{{ profile.gender === 'M' ? '男寝' : '女寝' }}</span>
      <div>
        <h2>{{ profile.student_name || local('同学', 'Student') }}</h2>
        <p>{{ profile.student_number }} · {{ profile.major_code }} {{ profile.major_name }}</p>
        <div class="student-profile-inline-meta">
          <span v-if="isForeignStudent" class="nationality-chip">
            {{ t('profile.nationality') }}：{{ countryName(profile.nationality_code) }}
          </span>
          <span class="profile-phone-line">
            <span>{{ t('profile.phone') }}：{{ formatPhoneDisplay(profile.phone_number, profile.nationality_code) || t('profile.phoneEmpty') }}</span>
            <button class="text-button light-text-button" type="button" @click="openPhoneEditor">{{ t('profile.phoneEdit') }}</button>
          </span>
        </div>
        <div v-if="currentActivity && !assigned" class="profile-primary-actions">
          <RouterLink v-if="currentActivity.allow_team" class="button accent" to="/student/teams">组队选寝</RouterLink>
          <RouterLink v-if="canSelectRoom" class="button primary" :to="`/student/batches/${currentActivityId}/rooms`">选择宿舍和床位</RouterLink>
          <button v-else class="button primary" type="button" disabled>选寝尚未开放</button>
        </div>
      </div>
    </section>

    <section class="panel assignment-summary compact-home-top-card">
      <div>
        <h2>{{ local('我的住宿结果', 'My accommodation') }}</h2>
      </div>
      <template v-if="assigned">
        <div class="assignment-place">
          <strong>{{ assignment.building_name }}</strong>
          <span>{{ assignment.room_number }} 室 · {{ assignment.bed_code }} 床位</span>
        </div>
        <p>{{ assignment.floor_number }}层 · {{ bedTypeText(assignment.bed_type) }}</p>
        <RouterLink v-if="currentActivityId" class="button secondary compact-home-card-action" :to="`/student/batches/${currentActivityId}/assignment`">查看完整结果</RouterLink>
      </template>
      <template v-else>
        <p>{{ local('尚未确定宿舍和床位。完善个人偏好后，可在开放选寝期间选择合适的房间。', 'No room or bed has been confirmed yet. Complete your preferences and choose during an open selection period.') }}</p>
      </template>
    </section>

    <p v-if="loading" class="panel empty-state home-span-2">正在读取个人选寝信息…</p>
    <p v-else-if="error" class="alert error home-span-2">{{ error }}</p>

    <section v-if="!loading && unreadNotifications.length" class="panel home-span-2 student-notification-panel">
      <div class="section-head">
        <div>
          <h3>{{ local('系统通知', 'Notifications') }}</h3>
        </div>
      </div>
      <div class="student-notification-list">
        <article v-for="notification in unreadNotifications" :key="String(notification.id)" class="student-notification-item">
          <div>
            <strong>{{ notificationTitle(notification) }}</strong>
            <p>{{ notificationMessage(notification) }}</p>
          </div>
          <button class="button ghost small" @click="markNotificationRead(notification)">{{ t('common.confirm') }}</button>
        </article>
      </div>
    </section>

    <section v-if="!loading" class="panel home-span-2 personal-preference-card">
      <div class="section-head split-title">
        <div>
          <h2>{{ local('我的个人偏好', 'My preferences') }}</h2>
        </div>
        <RouterLink
          v-if="canEditQuestionnaire"
          class="button secondary"
          to="/student/preferences"
        >{{ questionnaireStarted ? local('修改个人偏好', 'Edit preferences') : local('填写个人偏好', 'Complete preferences') }}</RouterLink>
      </div>

      <p v-if="answerSummary.length === 0" class="empty-state">{{ local('尚未填写个人偏好，可通过右上角按钮开始填写。', 'No preferences have been saved yet. Use the button above to get started.') }}</p>
      <div v-else class="personal-preference-content">
        <div class="personal-preference-table-column">
          <dl class="personal-preference-list">
            <div v-for="item in answerSummary" :key="item.code" class="personal-preference-row">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </div>

        <aside class="personal-preference-side-column">
          <div v-if="profileInsightEnabled" class="preference-profile-overview personal-preference-profile-panel">
            <div>
              <span class="profile-caption">{{ local('用户画像概述', 'Profile overview') }}</span>
              <h3>{{ preferenceProfileSummary }}</h3>
            </div>
            <div v-if="preferenceProfileTags.length" class="profile-tag-row">
              <span v-for="tag in preferenceProfileTags" :key="tag" class="profile-tag">{{ tag }}</span>
            </div>
          </div>


        </aside>
      </div>
    </section>

    <section class="panel info-card home-span-2 compact-rule-card">
      <h3>{{ local('请在规定时间内完成选择', 'Complete selection within the stated period') }}</h3>
      <p>{{ local('床位在确认前只会短暂保留，最终确认后会立即显示住宿结果。', 'A bed is held only temporarily until confirmation. Your accommodation result appears immediately after confirmation.') }}</p>
    </section>

    <div v-if="homeInvitation && !assigned" class="modal-overlay home-invitation-overlay" role="presentation">
      <section class="modal-card home-invitation-dialog" role="dialog" aria-modal="true">
        <h2>{{ t('team.invitation.title') }}</h2>
        <p>{{ t('team.invitation.message', { name: homeInvitation.inviter_name }) }}</p>
        <div class="invitation-student-summary">
          <strong>{{ homeInvitation.inviter_name }}</strong>
          <span>{{ homeInvitation.inviter_student_number }}</span>
        </div>
        <div class="button-row invitation-dialog-actions">
          <button class="button ghost" :disabled="invitationSubmitting" @click="dismissHomeInvitation">{{ t('common.dismiss') }}</button>
          <button class="button secondary" :disabled="invitationSubmitting" @click="respondHomeInvitation(false)">{{ t('common.reject') }}</button>
          <button class="button primary" :disabled="invitationSubmitting" @click="respondHomeInvitation(true)">{{ t('common.accept') }}</button>
        </div>
      </section>
    </div>

    <AppModal :open="showPhoneDialog" :title="t('profile.phoneEdit')" size="compact" @close="showPhoneDialog = false">
      <label class="form-stack">
        <span>{{ t('profile.phone') }}</span>
        <span class="profile-phone-input"><PhoneDialCodeSelect v-model="phoneDialCode" /><input v-model.trim="phoneLocalNumber" class="input" maxlength="24" inputmode="tel" :placeholder="local('本地手机号码', 'Local mobile number')" /></span>
      </label>
      <p v-if="phoneError" class="alert error">{{ phoneError }}</p>
      <template #footer>
        <button class="button ghost" type="button" @click="showPhoneDialog = false">{{ t('common.cancel') }}</button>
        <button class="button primary" :disabled="phoneSaving" @click="savePhoneNumber">{{ t('profile.phoneSave') }}</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.compact-home-top-card { min-height: 150px; padding-top: 18px; padding-bottom: 18px; }.profile-phone-line{display:inline-flex;align-items:center;gap:7px;flex-wrap:wrap}.profile-primary-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:14px}.profile-primary-actions .button{text-decoration:none}.profile-phone-input{display:grid;grid-template-columns:94px minmax(0,1fr);gap:8px}.personal-preference-card { margin-top: -2px; }
</style>
