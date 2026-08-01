<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const batch = ref<DataObject>({})
const questions = ref<DataObject[]>([])
const answers = reactive<Record<string, unknown>>({})
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')

const completed = computed(() =>
  questions.value.filter((question) => question.required_flag).every((question) => {
    const value = answers[String(question.question_code)]
    return value !== undefined && value !== null && value !== ''
  }),
)
const canSave = computed(() => ['PUBLISHED', 'OPEN'].includes(String(batch.value.batch_status)))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/student/batches/${batchId}/questionnaire`,
    )
    const data = (response.data.data ?? {}) as DataObject
    batch.value = (data.batch ?? {}) as DataObject
    questions.value = (data.questions ?? []) as DataObject[]
    const saved = (data.answers ?? []) as DataObject[]
    const questionById = new Map(questions.value.map((q) => [Number(q.id), String(q.question_code)]))
    for (const answer of saved) {
      const code = questionById.get(Number(answer.question_id))
      if (!code) continue
      try {
        answers[code] =
          typeof answer.answer_json === 'string'
            ? JSON.parse(answer.answer_json)
            : answer.answer_json
      } catch {
        answers[code] = answer.answer_json
      }
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '问卷加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSave.value) {
    error.value = '当前活动暂不允许修改问卷。'
    return
  }
  if (!completed.value) {
    error.value = '请完成所有必填问题。'
    return
  }
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    await api.post(`/api/v1/student/batches/${batchId}/questionnaire`, { ...answers })
    message.value = '问卷已保存，生活习惯匹配结果已更新。'
    window.setTimeout(() => {
      void router.push(String(batch.value.batch_status) === 'OPEN'
        ? `/student/batches/${batchId}/rooms`
        : '/student')
    }, 600)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '提交失败'
  } finally {
    saving.value = false
  }
}

function choices(question: DataObject) {
  const configured = (question.options ?? []) as DataObject[]
  if (configured.length > 0) {
    return configured.map((option) => ({
      value: String(option.option_code),
      label: String(option.option_text),
    }))
  }

  const feature = String(question.feature_key)
  if (feature === 'smokingAcceptance') {
    return [
      { value: 'ACCEPT', label: '接受' },
      { value: 'REJECT', label: '不接受' },
      { value: 'ANY', label: '均可' },
    ]
  }
  if (feature === 'napHabit') {
    return [
      { value: 0, label: '基本不午休' },
      { value: 1, label: '偶尔午休' },
      { value: 2, label: '经常午休' },
    ]
  }
  if (feature === 'bedPreference') {
    return [
      { value: 'ANY', label: '无特别偏好' },
      { value: 'LOFT_BED_DESK', label: '上床下桌' },
      { value: 'BUNK_UPPER', label: '上下铺上铺' },
      { value: 'BUNK_LOWER', label: '上下铺下铺' },
    ]
  }
  return [1, 2, 3, 4, 5].map((value) => ({
    value,
    label: ['非常低', '较低', '适中', '较高', '非常高'][value - 1],
  }))
}
</script>

<template>
  <div class="content-column narrow">
    <div class="page-title">
      <div>
        <span class="eyebrow">LIFESTYLE QUESTIONNAIRE</span>
        <h2>生活习惯问卷</h2>
        <p>答案只用于寻找生活习惯更接近的室友，不用于评价个人品质。</p>
      </div>
      <span class="progress-pill">{{ Object.keys(answers).length }}/{{ questions.length }}</span>
    </div>

    <p v-if="loading" class="panel empty-state">正在加载问卷…</p>
    <p v-else-if="error && questions.length === 0" class="alert error">{{ error }}</p>
    <p v-else-if="!canSave" class="alert">当前活动暂时不能修改问卷，你仍可查看已经填写的内容。</p>

    <form v-if="!loading" class="question-list" @submit.prevent="submit">
      <article v-for="(question, index) in questions" :key="String(question.id)" class="panel question-card">
        <div class="question-number">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="question-body">
          <h3>{{ question.question_text }}</h3>
          <p>{{ question.required_flag ? '必填' : '选填' }}</p>

          <input
            v-if="question.question_type === 'TIME'"
            v-model="answers[String(question.question_code)]"
            class="input"
            type="time"
            :disabled="!canSave"
            :required="Boolean(question.required_flag)"
          />
          <input
            v-else-if="question.question_type === 'INTEGER'"
            v-model.number="answers[String(question.question_code)]"
            class="input"
            type="number"
            min="16"
            max="32"
            :disabled="!canSave"
            :required="Boolean(question.required_flag)"
          />
          <div v-else class="choice-grid">
            <label v-for="choice in choices(question)" :key="String(choice.value)" class="choice-item">
              <input
                v-model="answers[String(question.question_code)]"
                type="radio"
                :name="String(question.question_code)"
                :value="choice.value"
                :disabled="!canSave"
                :required="Boolean(question.required_flag)"
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
          {{ saving ? '正在保存…' : '保存问卷' }}
        </button>
      </div>
    </form>
  </div>
</template>
