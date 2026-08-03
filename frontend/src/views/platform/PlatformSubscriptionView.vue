<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

type Row = Record<string, unknown>

const current = ref<Row>({})
const history = ref<Row[]>([])
const plans = ref<Row[]>([])
const features = ref<Row[]>([])
const quotaSummary = ref<Row>({})
const error = ref('')
const success = ref('')
const loading = ref(false)
const changing = ref(false)
const planRevisionId = ref<number | null>(null)
const direction = ref<'UPGRADE' | 'DOWNGRADE'>('UPGRADE')
const reason = ref('')
const contractNumber = ref('')
const preview = ref<Row | null>(null)
const statusAction = ref('')
const statusReason = ref('')

const selectedPlan = computed(() => plans.value.find((item) => Number(item.latest_revision_id ?? item.latestRevisionId) === planRevisionId.value) ?? null)
const featureNames = computed(() => new Map(features.value.map((item) => [String(item.feature_code ?? item.featureCode), String(item.feature_name ?? item.featureName ?? '业务功能')])))
const quotaCatalog = computed(() => new Map(((quotaSummary.value.catalog ?? []) as Row[]).map((item) => [String(item.quota_code ?? item.quotaCode), String(item.quota_name ?? item.quotaName ?? '资源项目')])))

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    ;[current.value, history.value, plans.value, features.value, quotaSummary.value] = await Promise.all([
      platformApi.subscription(), platformApi.subscriptionHistory(), platformApi.plans(), platformApi.features(), platformApi.quotas(),
    ])
    const currentRevision = Number(current.value.planRevisionId ?? 0)
    if (!planRevisionId.value) planRevisionId.value = Number(plans.value.find((item) => Number(item.latest_revision_id ?? 0) !== currentRevision)?.latest_revision_id ?? 0) || null
  } catch (cause) { showError(cause, '服务订阅加载失败') }
  finally { loading.value = false }
}

function showError(cause: unknown, fallback: string) {
  error.value = cause instanceof Error ? cause.message : fallback
  success.value = ''
}

async function loadPreview() {
  if (!planRevisionId.value) return
  error.value = ''; preview.value = null
  try { preview.value = await platformApi.previewChange(planRevisionId.value) }
  catch (cause) { showError(cause, '套餐影响预览失败') }
}

async function changePlan() {
  if (!planRevisionId.value || !reason.value.trim() || changing.value) return
  changing.value = true; error.value = ''; success.value = ''
  try {
    await platformApi.changePlan({ planRevisionId: planRevisionId.value, direction: direction.value, contractNumber: contractNumber.value.trim() || null, reason: reason.value.trim() })
    success.value = `当前学校已切换到${planName(selectedPlan.value)}。`
    preview.value = null; reason.value = ''; contractNumber.value = ''
    await load()
  } catch (cause) { showError(cause, '套餐变更失败') }
  finally { changing.value = false }
}

function openStatus(action: string) {
  statusAction.value = action
  statusReason.value = ''
  error.value = ''; success.value = ''
}

async function confirmStatus() {
  if (!statusAction.value || !statusReason.value.trim() || changing.value) return
  changing.value = true
  try {
    await platformApi.changeStatus(statusAction.value, statusReason.value.trim())
    success.value = `${statusActionName(statusAction.value)}操作已完成。`
    statusAction.value = ''; statusReason.value = ''
    await load()
  } catch (cause) { showError(cause, '服务状态调整失败') }
  finally { changing.value = false }
}

function planName(row: Row | null) { return String(row?.plan_name ?? row?.planName ?? '所选套餐') }
function statusText(value: unknown) { return ({ ACTIVE: '运行中', TRIAL: '试用中', SUSPENDED: '已暂停', EXPIRED: '已到期', TERMINATED: '已终止' } as Record<string,string>)[String(value)] ?? '状态未知' }
function typeText(value: unknown) { return ({ LONG_TERM: '长期服务', TRIAL: '试用服务', FIXED_TERM: '固定期限服务' } as Record<string,string>)[String(value)] ?? '学校服务' }
function statusActionName(value: string) { return ({ SUSPEND: '暂停服务', RESUME: '恢复服务', TERMINATE: '终止服务', EMERGENCY_STOP: '紧急停止', EMERGENCY_RESUME: '解除紧急停止' } as Record<string,string>)[value] ?? '调整服务' }
function featureName(code: unknown) { return featureNames.value.get(String(code)) ?? '业务功能' }
function quotaName(code: string) { return quotaCatalog.value.get(code) ?? '资源项目' }
function quotaEntries(value: unknown) { return value && typeof value === 'object' ? Object.entries(value as Record<string, unknown>) : [] }
</script>

<template>
  <section class="subscription-page">
    <header class="page-heading"><div><p class="eyebrow">学校服务</p><h1>服务订阅</h1><p>查看当前服务，预览套餐变化，并通过表单完成升级、降级或状态调整。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新数据' }}</button></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>

    <section class="current-card" :class="{ stopped: current.emergencyStopped }">
      <div><p class="eyebrow">当前服务</p><h2>{{ current.planName || '未配置套餐' }}</h2><p>{{ typeText(current.subscriptionType) }} · 套餐版本 {{ current.planRevision || '-' }}</p></div>
      <div class="current-status"><strong>{{ current.emergencyStopped ? '紧急停止中' : statusText(current.serviceStatus) }}</strong><span>合同编号：{{ current.contractNumber || '未填写' }}</span></div>
      <dl><div><dt>开始时间</dt><dd>{{ current.startAt || '-' }}</dd></div><div><dt>结束时间</dt><dd>{{ current.endAt || '长期有效' }}</dd></div><div><dt>订阅修订</dt><dd>第 {{ current.revision || '-' }} 版</dd></div></dl>
      <div class="status-actions"><button v-if="current.serviceStatus === 'ACTIVE'" @click="openStatus('SUSPEND')">暂停服务</button><button v-else-if="current.serviceStatus === 'SUSPENDED'" @click="openStatus('RESUME')">恢复服务</button><button v-if="!current.emergencyStopped" class="warning" @click="openStatus('EMERGENCY_STOP')">紧急停止</button><button v-else @click="openStatus('EMERGENCY_RESUME')">解除紧急停止</button><button class="danger" @click="openStatus('TERMINATE')">终止服务</button></div>
    </section>

    <div class="subscription-grid">
      <section class="panel">
        <div class="section-title"><div><h2>变更服务套餐</h2><p>先选择套餐并查看影响，再确认生效。</p></div></div>
        <form class="change-form" @submit.prevent="changePlan">
          <label><span>目标套餐</span><select v-model.number="planRevisionId" required @change="preview = null"><option :value="null" disabled>请选择套餐</option><option v-for="plan in plans" :key="String(plan.id)" :value="Number(plan.latest_revision_id)">{{ planName(plan) }} · 版本{{ plan.latest_revision }}</option></select></label>
          <label><span>变更方式</span><select v-model="direction"><option value="UPGRADE">升级或扩展服务</option><option value="DOWNGRADE">降级或精简服务</option></select></label>
          <label><span>合同编号（可选）</span><input v-model.trim="contractNumber" maxlength="128" placeholder="沿用原合同可留空" /></label>
          <label><span>变更原因</span><textarea v-model.trim="reason" required maxlength="500" rows="3" placeholder="说明本次调整的业务原因" /></label>
          <div class="form-actions"><button type="button" class="secondary" :disabled="!planRevisionId" @click="loadPreview">查看影响</button><button :disabled="changing || !preview">{{ changing ? '正在生效…' : '确认变更' }}</button></div>
        </form>
      </section>

      <section class="panel preview-panel">
        <div class="section-title"><div><h2>变更影响</h2><p>以业务名称展示新增、移除功能和容量变化。</p></div></div>
        <div v-if="preview" class="preview-content">
          <div><h3>新增功能</h3><div class="tag-list"><span v-for="code in (preview.addedFeatures as unknown[] || [])" :key="String(code)" class="positive">{{ featureName(code) }}</span><small v-if="!(preview.addedFeatures as unknown[] || []).length">没有新增功能</small></div></div>
          <div><h3>不再包含</h3><div class="tag-list"><span v-for="code in (preview.removedFeatures as unknown[] || [])" :key="String(code)" class="negative">{{ featureName(code) }}</span><small v-if="!(preview.removedFeatures as unknown[] || []).length">没有移除功能</small></div></div>
          <div><h3>资源上限变化</h3><div class="quota-diff"><article v-for="([code, value]) in quotaEntries(preview.targetQuotas)" :key="code"><span>{{ quotaName(code) }}</span><strong>{{ value }}</strong></article></div></div>
        </div>
        <p v-else class="empty">选择目标套餐后，点击“查看影响”。</p>
      </section>
    </div>

    <section class="panel">
      <div class="section-title"><div><h2>订阅变更记录</h2><p>每次套餐或状态变化都会形成一条不可覆盖的记录。</p></div></div>
      <div class="history-list"><article v-for="item in history" :key="String(item.id)"><div><strong>{{ item.plan_name }}</strong><p>{{ statusText(item.service_status) }} · 套餐版本 {{ item.plan_revision }}</p></div><div><strong>第 {{ item.revision }} 版</strong><small>{{ item.created_at }}</small></div></article><p v-if="!history.length" class="empty">暂无变更记录。</p></div>
    </section>

    <div v-if="statusAction" class="modal-backdrop" @click.self="statusAction = ''"><section class="dialog"><h2>{{ statusActionName(statusAction) }}</h2><p>该操作会立即影响当前学校服务，请填写明确原因。</p><textarea v-model.trim="statusReason" rows="4" maxlength="500" placeholder="请输入操作原因" /><div><button type="button" class="secondary" @click="statusAction = ''">取消</button><button type="button" :class="{ danger: statusAction === 'TERMINATE' || statusAction === 'EMERGENCY_STOP' }" :disabled="changing || !statusReason.trim()" @click="confirmStatus">确认操作</button></div></section></div>
  </section>
</template>

<style scoped>
.subscription-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading>button,.form-actions button,.dialog button{padding:10px 15px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.current-card,.panel{padding:22px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.current-card{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:18px;background:linear-gradient(135deg,#123f9c,#477be5);color:#fff}.current-card p,.current-card dt{color:#dce7ff}.current-card h2{margin:0 0 6px}.current-status{text-align:right}.current-status strong,.current-status span{display:block}.current-status strong{font-size:1.35rem}.current-status span{margin-top:6px;color:#dce7ff;font-size:.74rem}.current-card dl{grid-column:1/-1;display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:0}.current-card dl div{padding:12px;border-radius:12px;background:rgba(255,255,255,.1)}.current-card dd{margin:5px 0 0;font-weight:700}.status-actions{grid-column:1/-1;display:flex;gap:9px;flex-wrap:wrap}.status-actions button{padding:9px 13px;border:1px solid rgba(255,255,255,.25);border-radius:9px;color:#fff;background:rgba(255,255,255,.12);cursor:pointer}.status-actions .warning{background:#a85b12}.status-actions .danger,.dialog .danger{background:#b91c1c}.subscription-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.section-title{margin-bottom:18px}.section-title h2{margin:0 0 5px;font-size:1.05rem}.section-title p{margin:0;color:#69758b;font-size:.78rem}.change-form{display:grid;gap:13px}.change-form label{display:grid;gap:7px}.change-form label span{color:#526078;font-size:.78rem;font-weight:700}.change-form input,.change-form select,.change-form textarea,.dialog textarea{width:100%;padding:11px 12px;border:1px solid #d7dfeb;border-radius:10px;background:#fff}.form-actions{display:flex;justify-content:flex-end;gap:10px}.form-actions .secondary,.dialog .secondary{color:#315c9e;background:#edf3ff}.preview-content{display:grid;gap:18px}.preview-content h3{margin:0 0 9px;font-size:.84rem}.tag-list{display:flex;gap:7px;flex-wrap:wrap}.tag-list span{padding:6px 9px;border-radius:999px;font-size:.72rem}.tag-list .positive{color:#17664f;background:#e8f8f2}.tag-list .negative{color:#9b2838;background:#fff0f2}.tag-list small{color:#69758b}.quota-diff{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}.quota-diff article{display:flex;justify-content:space-between;padding:10px;border-radius:10px;background:#f5f7fb;font-size:.75rem}.history-list article{display:flex;justify-content:space-between;gap:18px;padding:14px 0;border-bottom:1px solid #edf1f7}.history-list p,.history-list small{margin:4px 0 0;color:#69758b;font-size:.74rem}.history-list article>div:last-child{text-align:right}.modal-backdrop{position:fixed;inset:0;z-index:100;display:grid;place-items:center;padding:20px;background:rgba(12,24,48,.68);backdrop-filter:blur(8px)}.dialog{width:min(500px,100%);padding:24px;border-radius:18px;background:#fff;box-shadow:0 28px 70px rgba(0,0,0,.25)}.dialog p{color:#69758b}.dialog>div{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}.notice{margin:0;padding:12px 14px;border-radius:10px}.notice.error{color:#9b2838;background:#fff0f2}.notice.success{color:#17664f;background:#edf9f5}.empty{text-align:center;color:#69758b}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}.current-card .eyebrow{color:#dce7ff}@media(max-width:900px){.subscription-grid{grid-template-columns:1fr}}@media(max-width:680px){.page-heading,.current-card{display:grid}.current-status{text-align:left}.current-card dl{grid-template-columns:1fr}.quota-diff{grid-template-columns:1fr}}
</style>
