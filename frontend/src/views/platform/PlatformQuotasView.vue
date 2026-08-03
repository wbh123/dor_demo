<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

type Row = Record<string, unknown>

const summary = ref<Row>({})
const error = ref('')
const success = ref('')
const loading = ref(false)
const saving = ref(false)
const form = ref({ quotaCode: '', quotaValue: 0, reason: '' })

const catalog = computed(() => Array.isArray(summary.value.catalog) ? summary.value.catalog as Row[] : [])
const usage = computed(() => Array.isArray(summary.value.usage) ? summary.value.usage as Row[] : [])
const overrides = computed(() => Array.isArray(summary.value.overrides) ? summary.value.overrides as Row[] : [])
const selectedQuota = computed(() => catalog.value.find((item) => codeOf(item) === form.value.quotaCode) ?? null)
const warningCount = computed(() => usage.value.filter((item) => Number(item.ratio ?? 0) >= .8).length)

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    summary.value = await platformApi.quotas()
    if (!form.value.quotaCode && catalog.value.length) form.value.quotaCode = codeOf(catalog.value[0])
  } catch (cause) { show(cause, '资源配额加载失败') }
  finally { loading.value = false }
}

function show(cause: unknown, fallback: string) {
  error.value = cause instanceof Error ? cause.message : fallback
  success.value = ''
}

async function submit() {
  if (!form.value.quotaCode || saving.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    await platformApi.addQuotaOverride({ ...form.value })
    success.value = `${nameOf(selectedQuota.value)}的新上限已保存。`
    form.value.quotaValue = 0; form.value.reason = ''
    await load()
  } catch (cause) { show(cause, '配额调整失败') }
  finally { saving.value = false }
}

function codeOf(row: Row) { return String(row.quota_code ?? row.quotaCode ?? '') }
function nameOf(row: Row | null) { return String(row?.quota_name ?? row?.quotaName ?? '资源项目') }
function unitOf(row: Row | null) { return String(row?.unit_name ?? row?.unitName ?? '项') }
function quotaCatalog(code: unknown) { return catalog.value.find((item) => codeOf(item) === String(code)) ?? null }
function percent(item: Row) { return Math.max(0, Math.round(Number(item.ratio ?? 0) * 100)) }
function usageStatus(item: Row) {
  const ratio = Number(item.ratio ?? 0)
  if (ratio >= 1) return '已达到上限'
  if (ratio >= .8) return '接近上限'
  return '使用正常'
}
</script>

<template>
  <section class="quota-page">
    <header class="page-heading"><div><p class="eyebrow">服务容量</p><h1>资源配额</h1><p>以图形化方式查看当前容量，并为单一学校调整资源上限。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新数据' }}</button></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>

    <div class="summary-cards"><article><span>配额项目</span><strong>{{ usage.length }}</strong><small>当前正在管理</small></article><article><span>需要关注</span><strong>{{ warningCount }}</strong><small>使用率达到80%以上</small></article><article><span>历史调整</span><strong>{{ overrides.length }}</strong><small>已保存的容量变更</small></article></div>

    <section class="panel">
      <div class="section-title"><div><h2>当前使用情况</h2><p>进度条显示已用数量与当前上限。</p></div></div>
      <div v-if="usage.length" class="quota-grid">
        <article v-for="item in usage" :key="String(item.quotaCode)">
          <div class="quota-head"><div><strong>{{ nameOf(quotaCatalog(item.quotaCode)) }}</strong><small>{{ usageStatus(item) }}</small></div><span>{{ item.used }} / {{ item.limit }} {{ unitOf(quotaCatalog(item.quotaCode)) }}</span></div>
          <div class="progress" :class="{ warning: Number(item.ratio || 0) >= .8, exceeded: Number(item.ratio || 0) >= 1 }"><i :style="{ width: `${Math.min(100, percent(item))}%` }" /></div>
          <div class="quota-foot"><span>已使用 {{ percent(item) }}%</span><span>剩余 {{ Math.max(0, Number(item.limit || 0) - Number(item.used || 0)) }} {{ unitOf(quotaCatalog(item.quotaCode)) }}</span></div>
        </article>
      </div>
      <p v-else class="empty">暂无配额数据。</p>
    </section>

    <section class="panel adjustment-panel">
      <div class="section-title"><div><h2>调整资源上限</h2><p>选择业务项目、填写新的上限和调整原因，无需编辑任何原始数据。</p></div></div>
      <form class="quota-form" @submit.prevent="submit">
        <label><span>资源项目</span><select v-model="form.quotaCode" required><option v-for="item in catalog" :key="codeOf(item)" :value="codeOf(item)">{{ nameOf(item) }}</option></select></label>
        <label><span>新的上限（{{ unitOf(selectedQuota) }}）</span><input v-model.number="form.quotaValue" type="number" min="0" required /></label>
        <label class="span-2"><span>调整原因</span><textarea v-model.trim="form.reason" required maxlength="500" rows="3" placeholder="例如：新生人数增加，需要扩大学生容量" /></label>
        <div class="form-actions span-2"><span>保存后立即作为当前学校的有效上限。</span><button :disabled="saving">{{ saving ? '正在保存…' : '保存调整' }}</button></div>
      </form>
    </section>

    <section class="panel">
      <div class="section-title"><div><h2>最近调整记录</h2><p>仅展示普通人可理解的资源名称、数值和原因。</p></div></div>
      <div class="history-list"><article v-for="item in overrides.slice(0, 12)" :key="String(item.id)"><div><strong>{{ nameOf(item) }}</strong><p>{{ item.change_reason || '未填写原因' }}</p></div><div><strong>{{ item.quota_value }} {{ unitOf(item) }}</strong><small>{{ item.created_at || item.effective_from || '-' }}</small></div></article><p v-if="!overrides.length" class="empty">尚无单独调整记录。</p></div>
    </section>
  </section>
</template>

<style scoped>
.quota-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading button,.form-actions button{padding:10px 15px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.summary-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.summary-cards article,.panel{padding:20px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.summary-cards span,.summary-cards small{display:block;color:#69758b;font-size:.76rem}.summary-cards strong{display:block;margin:10px 0 6px;font-size:1.8rem}.section-title{margin-bottom:18px}.section-title h2{margin:0 0 5px;font-size:1.05rem}.section-title p{margin:0;color:#69758b;font-size:.78rem}.quota-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:14px}.quota-grid article{padding:16px;border:1px solid #e2e8f1;border-radius:14px;background:#fbfcfe}.quota-head,.quota-foot{display:flex;justify-content:space-between;gap:12px}.quota-head small,.quota-foot{color:#69758b;font-size:.72rem}.quota-head div{display:grid;gap:4px}.progress{height:10px;margin:14px 0 8px;overflow:hidden;border-radius:999px;background:#edf1f7}.progress i{display:block;height:100%;border-radius:inherit;background:#3b82f6}.progress.warning i{background:#d6902f}.progress.exceeded i{background:#c33c4b}.quota-form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.quota-form label{display:grid;gap:7px}.quota-form label span{color:#526078;font-size:.78rem;font-weight:700}.quota-form input,.quota-form select,.quota-form textarea{width:100%;padding:11px 12px;border:1px solid #d7dfeb;border-radius:10px;background:#fff}.span-2{grid-column:1/-1}.form-actions{display:flex;justify-content:space-between;align-items:center;gap:16px;color:#69758b;font-size:.76rem}.history-list{display:grid}.history-list article{display:flex;justify-content:space-between;gap:18px;padding:14px 0;border-bottom:1px solid #edf1f7}.history-list p,.history-list small{margin:4px 0 0;color:#69758b;font-size:.74rem}.history-list article>div:last-child{text-align:right}.notice{margin:0;padding:12px 14px;border-radius:10px}.notice.error{color:#9b2838;background:#fff0f2}.notice.success{color:#17664f;background:#edf9f5}.empty{text-align:center;color:#69758b}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}@media(max-width:720px){.summary-cards,.quota-form{grid-template-columns:1fr}.span-2{grid-column:auto}.page-heading,.form-actions{display:grid}}
</style>
