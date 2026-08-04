// @ts-nocheck
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { bedTypeLabel } from '../../utils/bedLabels'
import { formatPhoneDisplay, normalizeInternationalPhone, splitInternationalPhone } from '../../utils/phoneCodes'

export function useStudentHomeContent() {
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
    subtitle,
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
    if (option) return String(option.option_text)
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

  return {
    computed,
    onMounted,
    ref,
    api,
    useI18n,
    useFeatureAccess,
    bedTypeLabel,
    formatPhoneDisplay,
    normalizeInternationalPhone,
    splitInternationalPhone,
    profile,
    currentActivity,
    questionnaire,
    assignmentResult,
    invitations,
    notifications,
    dismissedInvitationTokens,
    loading,
    invitationSubmitting,
    phoneSaving,
    showPhoneDialog,
    phoneDialCode,
    phoneLocalNumber,
    error,
    phoneError,
    hasFeature,
    questions,
    profileAnswers,
    profileAnswerEntries,
    questionnaireStarted,
    canEditQuestionnaire,
    profileInsightEnabled,
    canSelectRoom,
    assigned,
    assignment,
    currentActivityId,
    isForeignStudent,
    homeInvitation,
    unreadNotifications,
    answerSummary,
    rawAnswerByCode,
    preferenceProfileSummary,
    preferenceProfileTags,
    load,
    loadGlobalPreferences,
    loadAssignment,
    dismissHomeInvitation,
    respondHomeInvitation,
    markNotificationRead,
    openPhoneEditor,
    savePhoneNumber,
    notificationParameters,
    notificationTitle,
    notificationMessage,
    chooseCurrentActivity,
    activityId,
    parseAnswer,
    numericAnswer,
    timeMinutes,
    local,
    summaryLabel,
    displayAnswer,
    bedTypeText
  }
}
