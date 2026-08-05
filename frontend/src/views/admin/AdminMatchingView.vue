<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

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

type RecommendationStrategy = 'BEST_MATCH' | 'TRUE_RANDOM' | 'MATCH_WEIGHTED_RANDOM'

const recommendationDefinitions: Array<{ value: RecommendationStrategy; label: string; description: string }> = [
  { value: 'BEST_MATCH', label: '最匹配', description: '在全部合法候选中选择匹配分最高的寝室。' },
  { value: 'TRUE_RANDOM', label: '随机看看', description: '在硬约束合法寝室中等概率随机。' },
  { value: 'MATCH_WEIGHTED_RANDOM', label: '按匹配度随机', description: '全部合法候选保留非零概率，匹配度越高越容易抽中。' },
]

const weightDefinitions: WeightDefinition[] = [
  { key: 'sleepTimeMinutes', label: '入睡时间', description: '入睡时间越接近，匹配分越高' },
  { key: 'wakeTimeMinutes', label: '起床时间', description: '起床时间差异' },
  { key: 'sleepSensitivity', label: '睡眠敏感度', description: '对光线和动静的敏感程度' },
  { key: 'noiseTolerance', label: '噪声容忍度', description: '对室友活动声音的接受程度' },
  { key: 'cleaningFrequency', label: '打扫频率', description: '日常清洁习惯' },
  { key: 'tidinessRequirement', label: '整洁要求', description: '对公共区域整洁程度的要求' },
  { key: 'summerAirConditionerTemperature', label: '夏季制冷温度', description: '夏季空调制冷温度偏好' },
  { key: 'summerOvernightAirConditioner', label: '夏季整夜空调', description: '对整夜开启空调制冷的接受度' },
  { key: 'winterHeatingAcceptance', label: '冬季制热接受度', description: '对冬季空调制热的接受程度' },
  { key: 'winterHeatingTemperature', label: '冬季制热温度', description: '冬季空调制热温度偏好' },
  { key: 'afterLightsActivity', label: '熄灯后活动', description: '熄灯后保持安静或继续活动的习惯' },
  { key: 'alarmSnooze', label: '闹钟重复响铃', description: '早晨闹钟重复响铃频率' },
  { key: 'strongFoodOdorAcceptance', label: '重气味食物接受度', description: '对宿舍内重气味食物的接受程度' },
  { key: 'studyFrequency', label: '宿舍学习频率', description: '在宿舍学习的频率' },
  { key: 'gamingVoiceFrequency', label: '游戏或语音频率', description: '游戏、语音聊天等活动频率' },
  { key: 'socialActivity', label: '社交活动频率', description: '参与宿舍社交的频率' },
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
const policySaving = ref(false)
const selectionPolicy = reactive({ allowWithoutQuestionnaire: false, allowStudentReselect: false, questionnaireBypassFeatureEnabled: false, studentReselectFeatureEnabled: false, version: 0, reason: '' })

const form = reactive({
  schemeCode: '',
  schemeName: '',
  algorithmVersion: 'weighted-distance-v2',
  activate: true,
  reason: '',
})

const recommendationPolicy = reactive({
  allowed: ['BEST_MATCH', 'TRUE_RANDOM', 'MATCH_WEIGHTED_RANDOM'] as RecommendationStrategy[],
  defaultStrategy: 'BEST_MATCH' as RecommendationStrategy,
  baseWeight: 0.05,
  temperature: 0.2,
})

const defaultWeights: Record<string, number> = {
  sleepTimeMinutes: 1.2,
  wakeTimeMinutes: 1.0,
  sleepSensitivity: 1.2,
  noiseTolerance: 1.2,
  cleaningFrequency: 1.0,
  tidinessRequirement: 1.0,
  summerAirConditionerTemperature: 0.8,
  summerOvernightAirConditioner: 1.1,
  winterHeatingAcceptance: 0.8,
  winterHeatingTemperature: 0.6,
  afterLightsActivity: 1.2,
  alarmSnooze: 0.9,
  strongFoodOdorAcceptance: 0.7,
  studyFrequency: 0.8,
  gamingVoiceFrequency: 1.1,
  socialActivity: 0.6,
}

const weights = reactive<Record<string, number>>({ ...defaultWeights })

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
    const [response, policyResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/matching-weight-schemes'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/settings/selection-policy'),
    ])
    schemes.value = (response.data.data ?? []) as DataObject[]
    const policy = (policyResponse.data.data ?? {}) as DataObject
    selectionPolicy.allowWithoutQuestionnaire = Boolean(policy.allowWithoutQuestionnaire)
    selectionPolicy.allowStudentReselect = Boolean(policy.allowStudentReselect)
    selectionPolicy.questionnaireBypassFeatureEnabled = Boolean(policy.questionnaireBypassFeatureEnabled)
    selectionPolicy.studentReselectFeatureEnabled = Boolean(policy.studentReselectFeatureEnabled)
    selectionPolicy.version = Number(policy.version ?? 0)
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
  form.algorithmVersion = String(scheme.algorithm_version ?? 'weighted-distance-v2')
  form.activate = Boolean(scheme.enabled)
  form.reason = ''
  const sourceWeights = { ...((scheme.weights ?? {}) as DataObject) }
  if (sourceWeights.summerAirConditionerTemperature == null
    && sourceWeights.airConditionerTemperature != null) {
    sourceWeights.summerAirConditionerTemperature = sourceWeights.airConditionerTemperature
  }
  assignNumbers(weights, sourceWeights, defaultWeights)
  assignNumbers(conflictRules, (scheme.conflictRules ?? {}) as DataObject, {
    smokingConflictPenalty: 25,
    sleepTimeWarningMinutes: 60,
    cleaningWarningDifference: 1,
    gamingVoiceWarningDifference: 1,
  })
  const allowed = Array.isArray(scheme.allowedRecommendationStrategies)
    ? scheme.allowedRecommendationStrategies.map(String).filter(isRecommendationStrategy)
    : recommendationDefinitions.map((definition) => definition.value)
  recommendationPolicy.allowed = allowed.length ? allowed : ['BEST_MATCH']
  const configuredDefault = String(scheme.defaultRecommendationStrategy ?? 'BEST_MATCH')
  recommendationPolicy.defaultStrategy = isRecommendationStrategy(configuredDefault)
    && recommendationPolicy.allowed.includes(configuredDefault)
    ? configuredDefault
    : recommendationPolicy.allowed[0]
  recommendationPolicy.baseWeight = Number(scheme.weightedRandomBaseWeight ?? 0.05)
  recommendationPolicy.temperature = Number(scheme.weightedRandomTemperature ?? 0.2)
  error.value = ''
  message.value = ''
}

function startCreate() {
  selected.value = null
  creating.value = true
  form.schemeCode = ''
  form.schemeName = ''
  form.algorithmVersion = 'weighted-distance-v2'
  form.activate = false
  form.reason = ''
  recommendationPolicy.allowed = ['BEST_MATCH', 'TRUE_RANDOM', 'MATCH_WEIGHTED_RANDOM']
  recommendationPolicy.defaultStrategy = 'BEST_MATCH'
  recommendationPolicy.baseWeight = 0.05
  recommendationPolicy.temperature = 0.2
  assignNumbers(weights, defaultWeights, defaultWeights)
  assignNumbers(conflictRules, {
    smokingConflictPenalty: 25,
    sleepTimeWarningMinutes: 60,
    cleaningWarningDifference: 1,
    gamingVoiceWarningDifference: 1,
  }, {})
}

function isRecommendationStrategy(value: string): value is RecommendationStrategy {
  return recommendationDefinitions.some((definition) => definition.value === value)
}

function toggleRecommendationStrategy(strategy: RecommendationStrategy, checked: boolean) {
  if (checked) {
    if (!recommendationPolicy.allowed.includes(strategy)) recommendationPolicy.allowed.push(strategy)
    return
  }
  if (recommendationPolicy.allowed.length === 1) {
    error.value = '至少必须保留一种推荐方式。'
    return
  }
  recommendationPolicy.allowed = recommendationPolicy.allowed.filter((value) => value !== strategy)
  if (recommendationPolicy.defaultStrategy === strategy) {
    recommendationPolicy.defaultStrategy = recommendationPolicy.allowed[0]
  }
}

function assignNumbers(
  target: Record<string, number>,
  source: DataObject,
  fallback: Record<string, number>,
) {
  for (const key of Object.keys(target)) {
    target[key] = Number(source[key] ?? fallback[key] ?? 0)
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
  if (recommendationPolicy.allowed.length === 0) {
    error.value = '至少必须保留一种推荐方式。'
    return
  }
  if (!recommendationPolicy.allowed.includes(recommendationPolicy.defaultStrategy)) {
    error.value = '默认推荐方式必须属于允许方式。'
    return
  }
  if (recommendationPolicy.baseWeight <= 0 || recommendationPolicy.temperature <= 0) {
    error.value = '加权随机的基础权重和温度参数必须大于0。'
    return
  }

  saving.value = true
  try {
    const payload = {
      schemeName: form.schemeName.trim(),
      algorithmVersion: form.algorithmVersion.trim(),
      weights: { ...weights },
      conflictRules: { ...conflictRules },
      allowedRecommendationStrategies: [...recommendationPolicy.allowed],
      defaultRecommendationStrategy: recommendationPolicy.defaultStrategy,
      weightedRandomBaseWeight: recommendationPolicy.baseWeight,
      weightedRandomTemperature: recommendationPolicy.temperature,
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

async function saveSelectionPolicy() {
  if (!selectionPolicy.reason.trim()) {
    error.value = '请填写选寝策略修改原因。'
    return
  }
  policySaving.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/selection-policy', {
      allowWithoutQuestionnaire: selectionPolicy.allowWithoutQuestionnaire,
      allowStudentReselect: selectionPolicy.allowStudentReselect,
      expectedVersion: selectionPolicy.version,
      reason: selectionPolicy.reason.trim(),
    })
    const policy = (response.data.data ?? {}) as DataObject
    selectionPolicy.version = Number(policy.version ?? selectionPolicy.version + 1)
    selectionPolicy.reason = ''
    message.value = '选寝策略已保存。'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '选寝策略保存失败'
  } finally {
    policySaving.value = false
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
      <p>管理个人偏好匹配权重、推荐方式和冲突提示。每次修改都会创建不可变修订，已有批次不会受影响。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel selection-policy-card"><div class="section-head"><div><span class="eyebrow">SELECTION POLICY</span><h3>选寝行为策略</h3><p>策略受系统管理员功能授权控制，学校管理员只配置当前学校的具体行为。</p></div></div><div class="policy-switch-grid"><label :class="{ disabled: !selectionPolicy.questionnaireBypassFeatureEnabled }"><input v-model="selectionPolicy.allowWithoutQuestionnaire" type="checkbox" :disabled="!selectionPolicy.questionnaireBypassFeatureEnabled" /><span><strong>允许未填写问卷直接选寝</strong><small>学生进入选择前仍会收到提醒，房间卡片会标记未填写偏好的室友。</small></span></label><label :class="{ disabled: !selectionPolicy.studentReselectFeatureEnabled }"><input v-model="selectionPolicy.allowStudentReselect" type="checkbox" :disabled="!selectionPolicy.studentReselectFeatureEnabled" /><span><strong>允许学生取消已确定结果并重选</strong><small>管理员始终可以人工调整；学生只有在此策略和系统权限同时开启时才能自主取消。</small></span></label></div><label class="matching-reason"><span>策略修改原因</span><textarea v-model.trim="selectionPolicy.reason" class="input" rows="2" maxlength="500" placeholder="必填，将写入审计" /></label><div class="button-row"><button class="button primary" :disabled="policySaving" @click="saveSelectionPolicy">{{ policySaving ? '保存中…' : '保存选寝策略' }}</button></div></section>

    <section class="panel weight-manual"><span class="eyebrow">WEIGHT GUIDE</span><h3>权重控制说明</h3><div class="weight-manual-grid"><article><strong>0</strong><p>完全忽略该维度，不参与排序和差异分析。</p></article><article><strong>0.1～1.0</strong><p>弱影响，仅在其他维度相近时起辅助作用。</p></article><article><strong>1.1～2.5</strong><p>中等影响，适合睡眠、噪声、卫生等常用维度。</p></article><article><strong>2.6～5.0</strong><p>强影响，应谨慎使用，避免单一偏好压过整体兼容性。</p></article></div><p>匹配分是各维度归一化差异的加权结果，再叠加吸烟等明确冲突扣分。提示标签只解释差异，不评价学生人格；已有批次固定使用创建时的权重修订。</p></section>

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
            <div><h4>学生推荐方式</h4><p>至少保留一种方式，默认方式必须属于允许方式；设置会随修订绑定到批次。</p></div>
          </div>
          <div class="recommendation-policy-grid">
            <label v-for="definition in recommendationDefinitions" :key="definition.value" class="recommendation-option">
              <input
                type="checkbox"
                :checked="recommendationPolicy.allowed.includes(definition.value)"
                @change="toggleRecommendationStrategy(definition.value, ($event.target as HTMLInputElement).checked)"
              />
              <span><strong>{{ definition.label }}</strong><small>{{ definition.description }}</small></span>
            </label>
          </div>
          <div class="form-grid three-column recommendation-parameters">
            <label><span>默认推荐方式</span><select v-model="recommendationPolicy.defaultStrategy" class="input"><option v-for="strategy in recommendationPolicy.allowed" :key="strategy" :value="strategy">{{ recommendationDefinitions.find((item) => item.value === strategy)?.label }}</option></select></label>
            <label><span>加权随机基础权重</span><input v-model.number="recommendationPolicy.baseWeight" class="input" type="number" min="0.0001" max="10" step="0.01" required /></label>
            <label><span>加权随机温度</span><input v-model.number="recommendationPolicy.temperature" class="input" type="number" min="0.0001" max="10" step="0.01" required /></label>
          </div>
          <p class="parameter-hint">基础权重保证低匹配候选仍有非零概率；温度越低越偏向高分候选，越高越接近均匀随机。</p>

          <div class="matching-section-title">
            <div><h4>个人偏好权重</h4><p>范围0～5，数值越大表示该维度对排序影响越明显。</p></div>
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

<style scoped>
.policy-switch-grid,.weight-manual-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.policy-switch-grid label{display:flex;gap:12px;padding:15px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.policy-switch-grid label.disabled{opacity:.55}.policy-switch-grid span,.policy-switch-grid small{display:block}.policy-switch-grid small{margin-top:5px;color:var(--text-muted)}.weight-manual{display:grid;gap:14px}.weight-manual-grid article{padding:14px;border:1px solid var(--border);border-radius:13px}.weight-manual-grid strong{font-size:20px}.weight-manual-grid p{margin:5px 0 0;color:var(--text-muted)}.recommendation-policy-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.recommendation-option{display:flex;align-items:flex-start;gap:10px;padding:14px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.recommendation-option span,.recommendation-option small{display:block}.recommendation-option small{margin-top:5px;color:var(--text-muted);line-height:1.45}.recommendation-parameters{margin-top:14px}.parameter-hint{margin:8px 0 0;color:var(--text-muted);font-size:13px}@media(max-width:900px){.recommendation-policy-grid{grid-template-columns:1fr}}@media(max-width:720px){.policy-switch-grid,.weight-manual-grid{grid-template-columns:1fr}}
</style>