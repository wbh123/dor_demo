<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

interface PreferenceChoice {
  token: string
  payload: unknown
  label: string
}

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId || 0)
const globalMode = computed(() => !batchId)
const batch = ref<DataObject>({})
const questions = ref<DataObject[]>([])
const answers = reactive<Record<string, unknown>>({})
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')

const visibleQuestions = computed(() => questions.value.filter(isQuestionVisible))
const completed = computed(() =>
  visibleQuestions.value.filter(isRequired).every((question) => {
    const value = answerPayload(question)
    return value !== undefined && value !== null && value !== ''
  }),
)
const answeredCount = computed(() =>
  visibleQuestions.value.filter((question) => {
    const value = answerPayload(question)
    return value !== undefined && value !== null && value !== ''
  }).length,
)
const canSave = computed(() => globalMode.value || ['PUBLISHED', 'OPEN'].includes(String(batch.value.batch_status)))

const questionDetails: Record<string, string> = {
  SLEEP_TIME: '填写平时大多数工作日真正准备入睡的时间，而不是上床刷手机的时间。',
  WAKE_TIME: '填写平时大多数工作日需要起床离开床铺的时间。',
  SLEEP_SENSITIVITY: '考虑室友翻身、开门、键盘声或走动声对你入睡和夜间醒来的影响。',
  NOISE_TOLERANCE: '考虑白天聊天、音乐、视频外放和临时来访带来的声音。',
  CLEANING_FREQUENCY: '按你实际参与扫地、拖地、倒垃圾和清理公共区域的频率选择。',
  TIDINESS_REQUIREMENT: '考虑桌面、地面、衣物、快递箱和公共物品长期堆放时的接受程度。',
  NAP_HABIT: '考虑午间是否需要关灯、降低音量，以及午休通常持续多久。',
  SUMMER_AC_OVERNIGHT: '考虑夏季睡觉时是否接受空调整夜运行，以及对风声和直吹的接受程度。',
  SUMMER_AC_TEMPERATURE: '填写你在夏季夜间较舒适的空调温度。',
  WINTER_HEATING_ACCEPTANCE: '考虑冬季是否愿意使用空调制热，以及由此产生的干燥和电费。',
  WINTER_HEATING_TEMPERATURE: '填写你在冬季制热时较舒适的空调温度。',
  AFTER_LIGHTS_ACTIVITY: '考虑熄灯后使用台灯、键盘、吹风机、视频或语音聊天的频率。',
  ALARM_SNOOZE: '考虑早晨闹钟次数、音量，以及是否会连续贪睡提醒。',
  STRONG_FOOD_ODOR_ACCEPTANCE: '考虑螺蛳粉、榴莲、泡面、外卖和夜宵气味在寝室停留的情况。',
  VENTILATION: '考虑开窗通风时对冷风、热风、灰尘和室外噪声的接受程度。',
  STUDY_FREQUENCY: '考虑在寝室阅读、写作业、线上会议或长时间使用电脑的频率。',
  GAMING_VOICE: '考虑游戏或通话时使用语音、机械键盘和外放设备的频率。',
  SOCIAL_ACTIVITY: '考虑邀请同学到寝室聊天、聚餐或短暂停留的频率。',
  SMOKING_ACCEPTANCE: '请选择你对室友在寝室及门口吸烟行为的真实底线。',
  BED_PREFERENCE: '床位偏好只作为推荐参考，最终仍以实际可选床位为准。',
}

const detailedScaleLabels: Record<string, string[]> = {
  SLEEP_SENSITIVITY: ['轻微声响基本不影响', '偶尔会注意到声响', '一般声响可能影响', '较小声响就容易醒', '极轻微声响也会明显影响'],
  NOISE_TOLERANCE: ['需要长期保持安静', '只能接受短时低声交流', '可接受一般生活声音', '可接受较多聊天或影音声', '对日常噪声基本不介意'],
  CLEANING_FREQUENCY: ['通常每月不到一次', '约每两周一次', '约每周一次', '每周多次主动清理', '几乎每天都会整理清洁'],
  TIDINESS_REQUIREMENT: ['对物品堆放不太在意', '仅要求公共通道畅通', '希望整体基本整齐', '希望桌面地面经常整洁', '无法接受明显杂乱或异味'],
  AFTER_LIGHTS_ACTIVITY: ['熄灯后基本不活动', '偶尔低亮度安静使用设备', '有时使用台灯或键盘', '经常熄灯后继续学习娱乐', '几乎每天深夜仍有明显活动'],
  ALARM_SNOOZE: ['一次低音量闹钟即可', '偶尔加一次提醒', '通常需要两三次提醒', '经常连续多次贪睡', '高音量且长时间反复响铃'],
  STRONG_FOOD_ODOR_ACCEPTANCE: ['完全不能接受寝室内食用', '仅能接受及时通风处理', '偶尔可以接受', '多数情况下可以接受', '对明显食物气味不介意'],
  STUDY_FREQUENCY: ['几乎不在寝室学习', '每周少量时间', '每周数次', '多数晚上会学习', '几乎每天长时间学习'],
  GAMING_VOICE: ['不使用语音且保持安静', '偶尔短时低声语音', '每周有几次语音', '多数晚上使用语音', '经常长时间高频语音'],
  SOCIAL_ACTIVITY: ['基本不邀请同学来寝室', '偶尔短暂停留', '每周有少量来访', '经常聊天或聚餐', '寝室社交活动非常频繁'],
}

function questionDetail(question: DataObject) {
  return questionDetails[String(question.question_code)] ?? '请按照最近一段时间真实、稳定的生活习惯选择。'
}

function refinedChoiceLabel(question: DataObject, payload: unknown, original: string) {
  const labels = detailedScaleLabels[String(question.question_code)]
  const index = Number(payload) - 1
  return labels && Number.isInteger(index) && labels[index] ? labels[index] : original
}

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      globalMode.value ? '/api/v1/student/preferences' : `/api/v1/student/batches/${batchId}/questionnaire`,
    )
    const data = (response.data.data ?? {}) as DataObject
    batch.value = (data.batch ?? {}) as DataObject
    questions.value = (data.questions ?? []) as DataObject[]
    const directAnswers = (data.profileAnswers ?? (globalMode.value ? data.answers : {})) as Record<string, unknown>
    for (const question of questions.value) {
      const code = String(question.question_code)
      if (directAnswers[code] !== undefined) answers[code] = normalizeSavedAnswer(question, directAnswers[code])
    }
    const saved = Array.isArray(data.answers) ? data.answers as DataObject[] : []
    const questionById = new Map(questions.value.map((question) => [Number(question.id), question]))
    for (const answer of saved) {
      const question = questionById.get(Number(answer.question_id))
      if (!question) continue
      let parsed: unknown
      try { parsed = typeof answer.answer_json === 'string' ? JSON.parse(answer.answer_json) : answer.answer_json }
      catch { parsed = answer.answer_json }
      answers[String(question.question_code)] = normalizeSavedAnswer(question, parsed)
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '个人偏好加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSave.value) {
    error.value = '当前活动暂不允许修改个人偏好。'
    return
  }
  if (!completed.value) {
    error.value = '请完成所有必填的个人偏好。'
    return
  }
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    const payload: Record<string, unknown> = {}
    for (const question of visibleQuestions.value) {
      const code = String(question.question_code)
      const value = answerPayload(question)
      if (value !== undefined && value !== null && value !== '') payload[code] = value
    }
    if (globalMode.value) await api.put('/api/v1/student/preferences', payload)
    else await api.post(`/api/v1/student/batches/${batchId}/questionnaire`, payload)
    message.value = '个人偏好已保存，后续批次会自动使用这份偏好。'
    window.setTimeout(() => {
      void router.push(!globalMode.value && String(batch.value.batch_status) === 'OPEN'
        ? `/student/batches/${batchId}/rooms` : '/student')
    }, 600)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '提交失败'
  } finally {
    saving.value = false
  }
}

function isQuestionVisible(question: DataObject) {
  const code = String(question.question_code)
  if (code === 'WINTER_HEATING_TEMPERATURE') {
    const acceptanceQuestion = questions.value.find(
      (item) => String(item.question_code) === 'WINTER_HEATING_ACCEPTANCE',
    )
    const acceptance = Number(acceptanceQuestion ? answerPayload(acceptanceQuestion) : undefined)
    return Number.isFinite(acceptance) && acceptance > 1
  }
  return true
}

function isRequired(question: DataObject) {
  return Boolean(question.required_flag)
    || String(question.question_code) === 'WINTER_HEATING_TEMPERATURE'
}

function choiceToken(question: DataObject, optionCode: string, index: number) {
  return `${String(question.question_code)}:${optionCode}:${index}`
}

function choices(question: DataObject): PreferenceChoice[] {
  const configured = (question.options ?? []) as DataObject[]
  if (configured.length > 0) {
    const keepCode = ['SMOKING_ACCEPTANCE', 'BED_PREFERENCE']
      .includes(String(question.question_code))
    return configured.map((option, index) => {
      const optionCode = String(option.option_code)
      const payload = keepCode || option.feature_value === null || option.feature_value === undefined
        ? optionCode
        : Number(option.feature_value)
      return {
        token: choiceToken(question, optionCode, index),
        payload,
        label: refinedChoiceLabel(question, payload, String(option.option_text)),
      }
    })
  }

  const feature = String(question.feature_key)
  if (feature === 'smokingAcceptance') {
    return fallbackChoices(question, [
      ['ACCEPT', '接受'],
      ['REJECT', '不接受'],
      ['ANY', '均可'],
    ])
  }
  if (feature === 'napHabit') {
    return fallbackChoices(question, [
      [0, '基本不午休'],
      [1, '偶尔午休'],
      [2, '经常午休'],
    ])
  }
  if (feature === 'bedPreference') {
    return fallbackChoices(question, [
      ['ANY', '无特别偏好'],
      ['LOFT_BED_DESK', '上床下桌'],
      ['BUNK_UPPER', '上下铺上铺'],
      ['BUNK_LOWER', '上下铺下铺'],
    ])
  }
  return fallbackChoices(
    question,
    [1, 2, 3, 4, 5].map((value) => [
      value,
      ['几乎从不/完全不接受', '较少/较不敏感', '一般', '较多/比较在意', '几乎每天/非常在意'][value - 1],
    ]),
  )
}

function fallbackChoices(
  question: DataObject,
  values: Array<[unknown, string]>,
): PreferenceChoice[] {
  return values.map(([payload, label], index) => ({
    token: choiceToken(question, `VALUE_${String(payload)}`, index),
    payload,
    label,
  }))
}

function choicePayload(question: DataObject, token: unknown) {
  return choices(question).find((choice) => choice.token === token)?.payload
}

function answerPayload(question: DataObject) {
  const code = String(question.question_code)
  const value = answers[code]
  if (question.question_type === 'SINGLE_CHOICE') {
    return choicePayload(question, value)
  }
  return value
}

function normalizeSavedAnswer(question: DataObject, value: unknown) {
  if (question.question_type !== 'SINGLE_CHOICE') return value
  const matched = choices(question).find((choice) =>
    choice.payload === value || String(choice.payload) === String(value),
  )
  return matched?.token ?? ''
}

function questionMin(_question: DataObject) { return 16 }
function questionMax(_question: DataObject) { return 30 }
</script>

<template>
  <div class="content-column questionnaire-wide">
    <div class="page-title">
      <div>
        <span class="eyebrow">PERSONAL PREFERENCES</span>
        <h2>个人偏好</h2>
        <p>{{ globalMode ? '即使当前没有开放批次，也可以提前设置；后续选寝将自动使用。' : '这些信息只用于寻找相处习惯更接近的室友，不用于评价个人品质。' }}</p>
      </div>
      <span class="progress-pill">{{ answeredCount }}/{{ visibleQuestions.length }}</span>
    </div>

    <p v-if="loading" class="panel empty-state">正在加载个人偏好…</p>
    <p v-else-if="error && questions.length === 0" class="alert error">{{ error }}</p>
    <p v-else-if="!canSave" class="alert">当前活动暂时不能修改个人偏好，你仍可查看已经填写的内容。</p>

    <form v-if="!loading" class="question-list" @submit.prevent="submit">
      <article
        v-for="(question, index) in visibleQuestions"
        :key="String(question.id)"
        class="panel question-card"
      >
        <div class="question-number">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="question-body">
          <h3>{{ question.question_text }}</h3>
          <p class="question-detail">{{ questionDetail(question) }}</p><p class="question-required">{{ isRequired(question) ? '必填' : '选填' }}</p>

          <input
            v-if="question.question_type === 'TIME'"
            v-model="answers[String(question.question_code)]"
            class="input"
            type="time"
            :disabled="!canSave"
            :required="isRequired(question)"
          />
          <small v-if="question.question_type === 'TIME'" class="question-hint">请使用24小时制，例如23:30。</small>
          <label v-else-if="question.question_type === 'INTEGER'" class="temperature-input">
            <input
              v-model.number="answers[String(question.question_code)]"
              class="input"
              type="number"
              :min="questionMin(question)"
              :max="questionMax(question)"
              :disabled="!canSave"
              :required="isRequired(question)"
            />
            <span>℃</span>
          </label>
          <div v-else class="choice-grid choice-row">
            <label v-for="choice in choices(question)" :key="choice.token" class="choice-item">
              <input
                v-model="answers[String(question.question_code)]"
                type="radio"
                :name="String(question.question_code)"
                :value="choice.token"
                :disabled="!canSave"
                :required="isRequired(question)"
              />
              <span>{{ choice.label }}</span>
            </label>
          </div>
        </div>
      </article>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="message" class="alert success">{{ message }}</p>
      <div class="sticky-actions">
        <button type="button" class="button ghost" @click="router.push('/student')">返回首页</button>
        <button class="button primary" :disabled="saving || !completed || !canSave">
          {{ saving ? '正在保存…' : '保存个人偏好' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.questionnaire-wide{width:min(1240px,100%);max-width:1240px;margin:0 auto}.question-card{grid-template-columns:52px minmax(0,1fr)}.question-body{min-width:0}.question-body h3{margin-bottom:6px}.question-detail{margin:0 0 6px;color:var(--muted);line-height:1.65}.question-required{margin:0 0 12px;color:var(--primary);font-size:12px;font-weight:700}.choice-row{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(168px,1fr);grid-template-columns:none;gap:9px;overflow-x:auto;padding:2px 2px 7px;scrollbar-width:thin}.choice-row .choice-item{min-height:72px;align-items:center}.choice-row .choice-item span{line-height:1.45}@media(max-width:760px){.question-card{grid-template-columns:1fr}.choice-row{grid-auto-columns:minmax(155px,76vw)}}
</style>
