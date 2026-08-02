<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse } from '../../api/types'

interface WeightDefinition {
  key: string
  label: string
  description: string
}

interface RuleDefinition {
  key: string
  label: string
  min: number
  max: number
  step: number
  unit: string
}

const weightDefinitions: WeightDefinition[] = [
  { key: 'sleepTimeMinutes', label: '入睡时间', description: '入睡时间越接近，匹配分越高' },
  { key: 'wakeTimeMinutes', label: '起床时间', description: '起床时间差异' },
  { key: 'sleepSensitivity', label: '睡眠敏感度', description: '对光线和动静的敏感程度' },
  { key: 'noiseTolerance', label: '噪声容忍度', description: '对室友活动声音的接受程度' },
  { key: 'cleaningFrequency', label: '打扫频率', description: '日常清洁习惯' },
  { key: 'tidinessRequirement', label: '整洁要求', description: '对公共区域整洁程度的要求' },
  { key: 'airConditionerTemperature', label: '空调温度偏好', description: '常用空调温度习惯' },
  { key: 'studyFrequency', label: '宿舍学习频率', description: '在宿舍学习的频率' },
  { key: 'gamingVoiceFrequency', label: '游戏或语音频率', description: '游戏、语音聊天等活动频率' },
  { key: 'socialActivity', label: '社交活动频率', description: '邀请同学或参与宿舍社交的频率' },
]

const ruleDefinitions: RuleDefinition[] = [
  { key: 'smokingConflictPenalty', label: '吸烟偏好冲突扣分', min: 0, max: 100, step: 1, unit: '分' },
  { key: 'sleepTimeWarningMinutes', label: '入睡时间差异提示阈值', min: 0, max: 720, step: 15, unit: '分钟' },
  { key: 'cleaningWarningDifference', label: '打扫频率差异提示阈值', min: 0, max: 5, step: 0.25, unit: '级' },
  { key: 'gamingVoiceWarningDifference', label: '游戏或语音差异提示阈值', min: 0, max: 5, step: 0.25, unit: '级' },
]

const schemes = ref<DataObject[]>([])
const selected = ref<DataObject | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const creating = ref(false)

const form = reactive({
  schemeCode: '',
  schemeName: '',
  algorithmVersion: 'weighted-v2',
  activate: true,
  reason: '',
})

const weights = reactive<Record<string, number>>({
  sleepTimeMinutes: 1.2,
  wakeTimeMinutes: 1.0,
  sleepSensitivity: 1.2,
  noiseTolerance: 1.2,
  cleaningFrequency: 1.0,
  tidinessRequirement: 1.0,
  airConditionerTemperature: 0.8,
  studyFrequency: 0.8,
  gamingVoiceFrequency: 1.1,
  socialActivity: 0.6,
})

const conflictRules = reactive<Record<string, number>>({
  smokingConflictPenalty: 25,
  sleepTimeWarningMinutes: 60,
  cleaningWarningDifference: 1,
  gamingVoiceWarningDifference: 1,
})

const totalWeight = computed(() =>
  weightDefinitions.reduce((sum, definition) => sum + Number(weights[definition.key] ?? 0), 0),
)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/matching-weight-schemes')
    schemes.value = (response.data.data ?? []) as DataObject[]
    if (!selected.value && schemes.value.length > 0 && !creating.value) {
      selectScheme(schemes.value[0])
    } else if (selected.value) {
      const refreshed = schemes.value.find((scheme) => Number(scheme.id) === Number(selected.value?.id))
      if (refreshed) selectScheme(refreshed)
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '匹配方案加载失败'
  } finally {
    loading.value = false
  }
}

function selectScheme(scheme: DataObject) {
  selected.value = scheme
  creating.value = false
  form.schemeCode = String(scheme.scheme_code ?? '')
  form.schemeName = String(scheme.scheme_name ?? '')
  form.algorithmVersion = String(scheme.algorithm_version ?? 'weighted-v2')
  form.activate = Boolean(scheme.enabled)
  form.reason = ''
  assignNumbers(weights, (scheme.weights ?? {}) as DataObject)
  assignNumbers(conflictRules, (scheme.conflictRules ?? {}) as DataObject)
  error.value = ''
  message.value = ''
}

function startCreate() {
  selected.value = null
  creating.value = true
  form.schemeCode = ''
  form.schemeName = ''
  form.algorithmVersion = 'weighted-v2'
  form.activate = false
  form.reason = ''
  assignNumbers(weights, {
    sleepTimeMinutes: 1.2,
    wakeTimeMinutes: 1,
    sleepSensitivity: 1.2,
    noiseTolerance: 1.2,
    cleaningFrequency: 1,
    tidinessRequirement: 1,
    airConditionerTemperature: 0.8,
    studyFrequency: 0.8,
    gamingVoiceFrequency: 1.1,
    socialActivity: 0.6,
  })
  assignNumbers(conflictRules, {
    smokingConflictPenalty: 25,
    sleepTimeWarningMinutes: 60,
    cleaningWarningDifference: 1,
    gamingVoiceWarningDifference: 1,
  })
}

function assignNumbers(target: Record<string, number>, source: DataObject) {
  for (const key of Object.keys(target)) {
    if (source[key] != null) target[key] = Number(source[key])
  }
}

async function save() {
  error.value = ''
  message.value = ''
  if (!form.reason.trim()) {
    error.value = '请填写修改原因后再保存。'
    return
  }
  if (totalWeight.value <= 0) {
    error.value = '至少一个匹配权重必须大于0。'
    return
  }

  saving.value = true
  try {
    const payload = {
      schemeName: form.schemeName.trim(),
      algorithmVersion: form.algorithmVersion.trim(),
      weights: { ...weights },
      conflictRules: { ...conflictRules },
      activate: form.activate,
      reason: form.reason.trim(),
    }
    if (creating.value) {
      await api.post('/api/v1/admin/matching-weight-schemes', {
        ...payload,
        schemeCode: form.schemeCode.trim().toUpperCase(),
      })
      message.value = '匹配权重方案已创建。'
    } else if (selected.value) {
      await api.post(`/api/v1/admin/matching-weight-schemes/${Number(selected.value.id)}/revisions`, {
        ...payload,
        expectedVersion: Number(selected.value.version),
      })
      message.value = '新修订已创建，已有批次不会受影响。'
    }
    selected.value = null
    creating.value = false
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '匹配方案保存失败'
  } finally {
    saving.value = false
  }
}

function revisionLabel(scheme: DataObject) {
  return `${String(scheme.scheme_name)} · 第${Number(scheme.revision)}版`
}
</script>

<template>
  <div class="content-column matching-operations-page">
    <div class="page-title">
      <span class="eyebrow">MATCHING OPERATIONS</span>
      <h2>匹配规则</h2>
      <p>管理生活习惯匹配权重和冲突提示。每次修改都会创建不可变修订，已有批次不会受影响。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="matching-layout">
      <aside class="panel matching-scheme-list">
        <div class="section-head compact-head">
          <div>
            <span class="eyebrow">SCHEME REVISIONS</span>
            <h3>权重方案修订</h3>
          </div>
          <button class="button secondary small" type="button" @click="startCreate">新建方案</button>
        </div>
        <p v-if="loading" class="empty-state">正在加载…</p>
        <button
          v-for="scheme in schemes"
          :key="Number(scheme.id)"
          class="matching-scheme-item"
          :class="{ active: Number(selected?.id) === Number(scheme.id), enabled: Boolean(scheme.enabled) }"
          type="button"
          @click="selectScheme(scheme)"
        >
          <span>
            <strong>{{ revisionLabel(scheme) }}</strong>
            <small>{{ scheme.algorithm_version }}</small>
          </span>
          <span class="scheme-meta">
            <b v-if="scheme.enabled">当前启用</b>
            <small>使用批次 {{ scheme.batch_count }}</small>
          </span>
        </button>
        <p v-if="!loading && schemes.length === 0" class="empty-state">暂无匹配方案，请创建首个方案。</p>
      </aside>

      <section class="panel matching-editor">
        <div class="section-head">
          <div>
            <span class="eyebrow">RULE EDITOR</span>
            <h3>{{ creating ? '创建匹配方案' : '创建新修订' }}</h3>
            <p v-if="selected">基于“{{ revisionLabel(selected) }}”创建下一修订，原修订和已有批次保持不变。</p>
          </div>
          <span class="weight-total">权重合计 {{ totalWeight.toFixed(2) }}</span>
        </div>

        <form class="matching-form" @submit.prevent="save">
          <div class="form-grid three-column">
            <label>
              <span>方案编码</span>
              <input v-model.trim="form.schemeCode" class="input" :disabled="!creating" required maxlength="32" placeholder="例如 DEFAULT" />
            </label>
            <label>
              <span>方案名称</span>
              <input v-model.trim="form.schemeName" class="input" required maxlength="128" />
            </label>
            <label>
              <span>算法版本</span>
              <input v-model.trim="form.algorithmVersion" class="input" required maxlength="32" />
            </label>
          </div>

          <div class="matching-section-title">
            <div><h4>生活习惯权重</h4><p>范围0～5，数值越大表示该维度对排序影响越明显。</p></div>
          </div>
          <div class="weight-grid">
            <label v-for="definition in weightDefinitions" :key="definition.key" class="weight-field">
              <span><strong>{{ definition.label }}</strong><small>{{ definition.description }}</small></span>
              <input v-model.number="weights[definition.key]" class="input" type="number" min="0" max="5" step="0.1" required />
            </label>
          </div>

          <div class="matching-section-title">
            <div><h4>冲突解释规则</h4><p>规则仅用于分数惩罚和公开提示，不会评价学生人格。</p></div>
          </div>
          <div class="rule-grid">
            <label v-for="definition in ruleDefinitions" :key="definition.key" class="rule-field">
              <span>{{ definition.label }}</span>
              <div><input v-model.number="conflictRules[definition.key]" class="input" type="number" :min="definition.min" :max="definition.max" :step="definition.step" required /><small>{{ definition.unit }}</small></div>
            </label>
          </div>

          <label class="checkbox-line matching-activate">
            <input v-model="form.activate" type="checkbox" />
            保存后设为新批次默认使用的启用修订
          </label>

          <label class="matching-reason">
            <span>修改原因</span>
            <textarea v-model.trim="form.reason" class="input" rows="3" maxlength="500" required placeholder="说明调整目的和依据" />
          </label>

          <div class="revision-notice">
            <strong>修订规则</strong>
            <p>点击“{{ creating ? '创建方案' : '创建新修订' }}”后不会覆盖旧数据；已经发布或进行中的批次继续使用原匹配规则。</p>
          </div>

          <div class="button-row matching-actions">
            <button v-if="creating && schemes.length" class="button ghost" type="button" @click="selectScheme(schemes[0])">取消新建</button>
            <button class="button primary" type="submit" :disabled="saving">
              {{ saving ? '正在保存…' : creating ? '创建方案' : '创建新修订' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
