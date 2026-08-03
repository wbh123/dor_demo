<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

type Row = Record<string, unknown>

const plans = ref<Row[]>([])
const features = ref<Row[]>([])
const quotaSummary = ref<Row>({})
const error = ref('')
const success = ref('')
const loading = ref(false)
const saving = ref(false)
const mode = ref<'CREATE' | 'REVISE'>('CREATE')
const sourceRevisionId = ref<number | null>(null)
const form = ref({ planCode: '', planName: '', revisionName: '', description: '', reason: '' })
const selectedFeatures = ref<string[]>([])
const quotaValues = ref<Record<string, number>>({})

const featureGroups = computed(() => {
  const result = new Map<string, Row[]>()
  features.value.filter((item) => Boolean(item.enabled_in_program ?? item.enabledInProgram)).forEach((item) => {
    const key = permissionClass(item.phase)
    result.set(key, [...(result.get(key) ?? []), item])
  })
  return [...result.entries()]
})
const quotas = computed(() => Array.isArray(quotaSummary.value.catalog) ? quotaSummary.value.catalog as Row[] : [])

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    ;[plans.value, features.value, quotaSummary.value] = await Promise.all([
      platformApi.plans(), platformApi.features(), platformApi.quotas(),
    ])
    ensureQuotaValues()
  } catch (cause) { showError(cause, '套餐信息加载失败') }
  finally { loading.value = false }
}

function ensureQuotaValues() {
  const next = { ...quotaValues.value }
  quotas.value.forEach((item) => {
    const code = quotaCode(item)
    if (next[code] === undefined) next[code] = 0
  })
  quotaValues.value = next
}

function showError(cause: unknown, fallback: string) {
  error.value = cause instanceof Error ? cause.message : fallback
  success.value = ''
}

function resetForm() {
  form.value = { planCode: '', planName: '', revisionName: '', description: '', reason: '' }
  selectedFeatures.value = []
  quotaValues.value = Object.fromEntries(quotas.value.map((item) => [quotaCode(item), 0]))
  sourceRevisionId.value = null
}

async function selectRevision() {
  if (!sourceRevisionId.value) return
  error.value = ''
  try {
    const revision = await platformApi.planRevision(sourceRevisionId.value)
    form.value.planCode = String(revision.plan_code ?? '')
    form.value.planName = String(revision.plan_name ?? '')
    form.value.description = String(revision.description ?? '')
    selectedFeatures.value = Array.isArray(revision.features) ? revision.features.map(String) : []
    const values: Record<string, number> = Object.fromEntries(quotas.value.map((item) => [quotaCode(item), 0]))
    ;((revision.quotas ?? []) as Row[]).forEach((item) => { values[String(item.quota_code)] = Number(item.quota_value ?? 0) })
    quotaValues.value = values
  } catch (cause) { showError(cause, '套餐内容加载失败') }
}

async function submit() {
  if (saving.value) return
  saving.value = true; error.value = ''; success.value = ''
  const quotasPayload = Object.fromEntries(Object.entries(quotaValues.value).map(([code, value]) => [code, Number(value || 0)]))
  try {
    if (mode.value === 'CREATE') {
      await platformApi.createPlan({ ...form.value, features: selectedFeatures.value, quotas: quotasPayload })
      success.value = '新套餐及首个版本已创建。'
    } else {
      if (!sourceRevisionId.value) throw new Error('请选择需要修订的套餐')
      await platformApi.revisePlan(sourceRevisionId.value, {
        revisionName: form.value.revisionName,
        description: form.value.description,
        reason: form.value.reason,
        features: selectedFeatures.value,
        quotas: quotasPayload,
      })
      success.value = '套餐新版本已创建，原版本保持不变。'
    }
    resetForm(); await load()
  } catch (cause) { showError(cause, '套餐保存失败') }
  finally { saving.value = false }
}

function planName(item: Row) { return String(item.plan_name ?? item.planName ?? '未命名套餐') }
function featureCode(item: Row) { return String(item.feature_code ?? item.featureCode ?? '') }
function featureName(item: Row) { return String(item.feature_name ?? item.featureName ?? '业务功能') }
function quotaCode(item: Row) { return String(item.quota_code ?? item.quotaCode ?? '') }
function quotaName(item: Row) { return String(item.quota_name ?? item.quotaName ?? '资源项目') }
function quotaUnit(item: Row) { return String(item.unit_name ?? item.unitName ?? '项') }
function permissionClass(value: unknown) { return ({ PHASE1: 'A类权限', PHASE2: 'B类权限', PHASE3: 'C类权限' } as Record<string,string>)[String(value)] ?? 'C类权限' }
</script>

<template>
  <section class="plans-page">
    <header class="page-heading"><div><p class="eyebrow">服务方案</p><h1>套餐管理</h1><p>通过勾选功能和填写容量创建套餐版本，无需手工编辑代码或原始数据。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新数据' }}</button></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>

    <section class="panel">
      <div class="section-title"><div><h2>现有套餐</h2><p>每次修订都会创建新版本，已经生效的旧版本不会被覆盖。</p></div></div>
      <div class="plan-grid"><article v-for="plan in plans" :key="String(plan.id)"><div><strong>{{ planName(plan) }}</strong><span>{{ plan.enabled ? '可用' : '已停用' }}</span></div><p>当前最新版本：{{ plan.revision_name || '未命名版本' }}</p><small>版本号 {{ plan.latest_revision || '-' }}</small></article><p v-if="!plans.length" class="empty">尚未创建套餐。</p></div>
    </section>

    <section class="panel editor-panel">
      <div class="section-title split"><div><h2>{{ mode === 'CREATE' ? '创建新套餐' : '创建套餐新版本' }}</h2><p>{{ mode === 'CREATE' ? '适合新增一种完整的服务方案。' : '基于已有版本调整功能或容量。' }}</p></div><div class="mode-switch"><button :class="{ active: mode === 'CREATE' }" @click="mode = 'CREATE'; resetForm()">新建套餐</button><button :class="{ active: mode === 'REVISE' }" @click="mode = 'REVISE'; resetForm()">修订套餐</button></div></div>

      <form class="plan-form" @submit.prevent="submit">
        <label v-if="mode === 'REVISE'" class="span-2"><span>选择已有版本</span><select v-model.number="sourceRevisionId" required @change="selectRevision"><option :value="null" disabled>请选择</option><option v-for="plan in plans" :key="String(plan.latest_revision_id)" :value="Number(plan.latest_revision_id)">{{ planName(plan) }} · {{ plan.revision_name }}</option></select></label>
        <label v-if="mode === 'CREATE'"><span>套餐名称</span><input v-model.trim="form.planName" required maxlength="128" placeholder="例如：校园选寝标准服务" /></label>
        <label v-if="mode === 'CREATE'"><span>套餐简称</span><input v-model.trim="form.planCode" required maxlength="64" placeholder="仅用于内部识别，例如 STANDARD" /></label>
        <label><span>版本名称</span><input v-model.trim="form.revisionName" required maxlength="128" placeholder="例如：2026秋季版" /></label>
        <label><span>版本说明</span><input v-model.trim="form.description" maxlength="500" placeholder="说明本版本面向的使用场景" /></label>

        <fieldset class="span-2"><legend>选择功能权限</legend><section v-for="([group, items]) in featureGroups" :key="group" class="permission-group"><header><strong>{{ group }}</strong><span>{{ group === 'A类权限' ? '基础业务功能' : group === 'B类权限' ? '增强业务功能' : '可选业务功能' }}</span></header><div><label v-for="feature in items" :key="featureCode(feature)" class="check-card"><input v-model="selectedFeatures" type="checkbox" :value="featureCode(feature)" /><span><strong>{{ featureName(feature) }}</strong><small>{{ feature.scope === 'STUDENT' ? '学生端' : feature.scope === 'ADMIN' ? '管理端' : '管理端与学生端' }}</small></span></label></div></section></fieldset>

        <fieldset class="span-2"><legend>设置资源容量</legend><div class="quota-grid"><label v-for="quota in quotas" :key="quotaCode(quota)"><span>{{ quotaName(quota) }}</span><div><input v-model.number="quotaValues[quotaCode(quota)]" type="number" min="0" required /><small>{{ quotaUnit(quota) }}</small></div></label></div></fieldset>

        <label class="span-2"><span>创建或修订原因</span><textarea v-model.trim="form.reason" required maxlength="500" rows="3" placeholder="说明为什么需要这个套餐版本" /></label>
        <div class="form-actions span-2"><span>功能和容量均可在后续新版本中继续调整。</span><button :disabled="saving">{{ saving ? '正在保存…' : mode === 'CREATE' ? '创建套餐' : '创建新版本' }}</button></div>
      </form>
    </section>
  </section>
</template>

<style scoped>
.plans-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading>button,.form-actions button{padding:10px 15px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.panel{padding:22px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.section-title{margin-bottom:18px}.section-title.split{display:flex;justify-content:space-between;gap:20px}.section-title h2{margin:0 0 5px;font-size:1.05rem}.section-title p{margin:0;color:#69758b;font-size:.78rem}.plan-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:13px}.plan-grid article{padding:16px;border:1px solid #e2e8f1;border-radius:14px;background:#fbfcfe}.plan-grid article>div{display:flex;justify-content:space-between;gap:10px}.plan-grid span{padding:4px 8px;border-radius:999px;color:#17664f;background:#e8f8f2;font-size:.67rem}.plan-grid p,.plan-grid small{display:block;margin:8px 0 0;color:#69758b;font-size:.74rem}.mode-switch{display:grid;grid-template-columns:1fr 1fr;padding:4px;border-radius:11px;background:#eef2f8}.mode-switch button{padding:8px 12px;border:0;border-radius:8px;color:#69758b;background:transparent;cursor:pointer}.mode-switch button.active{color:#1d5dd8;background:#fff;box-shadow:0 3px 10px rgba(22,43,82,.09);font-weight:700}.plan-form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.plan-form>label{display:grid;gap:7px}.plan-form>label>span{color:#526078;font-size:.78rem;font-weight:700}.plan-form input,.plan-form select,.plan-form textarea{width:100%;padding:11px 12px;border:1px solid #d7dfeb;border-radius:10px;background:#fff}.span-2{grid-column:1/-1}fieldset{min-width:0;margin:4px 0;padding:18px;border:1px solid #dfe6f0;border-radius:14px}legend{padding:0 8px;color:#315c9e;font-size:.82rem;font-weight:700}.permission-group+ .permission-group{margin-top:18px}.permission-group header{display:flex;align-items:center;gap:10px;margin-bottom:10px}.permission-group header span{color:#69758b;font-size:.72rem}.permission-group>div{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:9px}.check-card{display:flex;align-items:flex-start;gap:9px;padding:11px;border:1px solid #e2e8f1;border-radius:11px;cursor:pointer}.check-card input{width:17px;height:17px;margin-top:2px}.check-card span{display:grid;gap:3px}.check-card small{color:#69758b}.quota-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:11px}.quota-grid label{display:grid;gap:7px}.quota-grid label>span{color:#526078;font-size:.76rem;font-weight:700}.quota-grid label>div{display:flex;align-items:center;gap:8px}.quota-grid small{color:#69758b;white-space:nowrap}.form-actions{display:flex;justify-content:space-between;align-items:center;gap:16px;color:#69758b;font-size:.76rem}.notice{margin:0;padding:12px 14px;border-radius:10px}.notice.error{color:#9b2838;background:#fff0f2}.notice.success{color:#17664f;background:#edf9f5}.empty{text-align:center;color:#69758b}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}@media(max-width:760px){.page-heading,.section-title.split,.form-actions{display:grid}.plan-form{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
