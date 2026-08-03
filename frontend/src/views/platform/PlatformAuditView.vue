<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

type Row = Record<string, unknown>
const rows = ref<Row[]>([])
const error = ref('')
const loading = ref(false)
const keyword = ref('')
const resultFilter = ref('ALL')

const filteredRows = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const success = Boolean(row.success)
    if (resultFilter.value === 'SUCCESS' && !success) return false
    if (resultFilter.value === 'FAILED' && success) return false
    const searchable = `${actionText(row.operation_type)} ${targetText(row.target_type)} ${row.change_reason ?? ''}`.toLowerCase()
    return !term || searchable.includes(term)
  })
})
const successCount = computed(() => rows.value.filter((row) => Boolean(row.success)).length)
const failedCount = computed(() => rows.value.length - successCount.value)

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try { rows.value = await platformApi.audit(200) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '操作记录加载失败' }
  finally { loading.value = false }
}

function actionText(value: unknown) {
  const labels: Record<string, string> = {
    PLAN_CREATE: '创建服务套餐', PLAN_REVISE: '修订服务套餐',
    SUBSCRIPTION_UPGRADE: '升级服务套餐', SUBSCRIPTION_DOWNGRADE: '调整服务套餐',
    SUBSCRIPTION_SUSPEND: '暂停服务', SUBSCRIPTION_RESUME: '恢复服务',
    SUBSCRIPTION_TERMINATE: '终止服务', SUBSCRIPTION_EMERGENCY_STOP: '紧急停止服务',
    SUBSCRIPTION_EMERGENCY_RESUME: '解除紧急停止', FEATURE_STATE_SET: '调整单项功能授权',
    FEATURE_BATCH_STATE_SET: '批量调整功能授权', FEATURE_OVERRIDE_ADD: '增加功能授权',
    FEATURE_OVERRIDE_REMOVE: '移除功能授权', QUOTA_OVERRIDE_UPDATE: '调整资源上限',
  }
  return labels[String(value ?? '')] ?? '完成服务管理操作'
}
function targetText(value: unknown) {
  const labels: Record<string, string> = {
    SUBSCRIPTION_PLAN: '服务套餐', SERVICE_SUBSCRIPTION: '当前学校服务',
    FEATURE_CATALOG: '功能授权', SUBSCRIPTION_FEATURE_OVERRIDE: '功能授权',
    SUBSCRIPTION_QUOTA_OVERRIDE: '资源配额',
  }
  return labels[String(value ?? '')] ?? '系统服务'
}
function timeText(row: Row) { return String(row.created_at ?? '-') }
</script>

<template>
  <section class="audit-page">
    <header class="page-heading"><div><p class="eyebrow">安全与追溯</p><h1>操作审计</h1><p>查看服务管理人员做了什么、是否成功以及变更原因，不展示内部数据结构。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新记录' }}</button></header>
    <p v-if="error" class="notice error">{{ error }}</p>

    <div class="summary-grid"><article><span>最近记录</span><strong>{{ rows.length }}</strong><small>最多显示200条</small></article><article><span>成功操作</span><strong>{{ successCount }}</strong><small>已正常完成</small></article><article><span>失败操作</span><strong>{{ failedCount }}</strong><small>建议及时检查</small></article></div>

    <section class="panel">
      <div class="toolbar"><label><span>搜索操作或原因</span><input v-model.trim="keyword" type="search" placeholder="例如：调整资源上限" /></label><label><span>操作结果</span><select v-model="resultFilter"><option value="ALL">全部结果</option><option value="SUCCESS">仅成功</option><option value="FAILED">仅失败</option></select></label></div>
      <div class="audit-list">
        <article v-for="row in filteredRows" :key="String(row.id)" :class="{ failed: !Boolean(row.success) }">
          <span class="status-dot" />
          <div class="audit-main"><div class="audit-title"><strong>{{ actionText(row.operation_type) }}</strong><span>{{ Boolean(row.success) ? '操作成功' : '操作失败' }}</span></div><p>对象：{{ targetText(row.target_type) }}</p><p class="reason">原因：{{ row.change_reason || '未填写具体原因' }}</p></div>
          <div class="audit-meta"><time>{{ timeText(row) }}</time><small v-if="!Boolean(row.success)">{{ row.error_code ? '系统已记录失败原因' : '未能完成操作' }}</small></div>
        </article>
        <p v-if="!filteredRows.length" class="empty">没有符合条件的操作记录。</p>
      </div>
    </section>
  </section>
</template>

<style scoped>
.audit-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading button{padding:10px 15px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.summary-grid article,.panel{padding:20px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.summary-grid span,.summary-grid small{display:block;color:#69758b;font-size:.76rem}.summary-grid strong{display:block;margin:10px 0 6px;font-size:1.8rem}.toolbar{display:grid;grid-template-columns:1fr 220px;gap:14px;margin-bottom:18px}.toolbar label{display:grid;gap:7px}.toolbar span{color:#526078;font-size:.76rem;font-weight:700}.toolbar input,.toolbar select{width:100%;padding:11px 12px;border:1px solid #d7dfeb;border-radius:10px;background:#fff}.audit-list{display:grid}.audit-list article{display:grid;grid-template-columns:12px minmax(0,1fr) auto;gap:14px;padding:16px 0;border-bottom:1px solid #edf1f7}.status-dot{width:9px;height:9px;margin-top:6px;border-radius:50%;background:#19a278;box-shadow:0 0 0 5px rgba(25,162,120,.12)}.failed .status-dot{background:#c33c4b;box-shadow:0 0 0 5px rgba(195,60,75,.11)}.audit-title{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.audit-title span{padding:4px 8px;border-radius:999px;color:#17664f;background:#edf9f5;font-size:.68rem}.failed .audit-title span{color:#9b2838;background:#fff0f2}.audit-main p{margin:5px 0 0;color:#69758b;font-size:.74rem}.audit-main .reason{color:#526078}.audit-meta{text-align:right;color:#69758b;font-size:.72rem}.audit-meta small{display:block;margin-top:6px;color:#9b2838}.notice{margin:0;padding:12px 14px;border-radius:10px}.notice.error{color:#9b2838;background:#fff0f2}.empty{text-align:center;color:#69758b}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}@media(max-width:760px){.summary-grid,.toolbar{grid-template-columns:1fr}.page-heading{display:grid}.audit-list article{grid-template-columns:12px 1fr}.audit-meta{grid-column:2;text-align:left}}
</style>
