<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const anomalies = ref<DataObject[]>([])
const summary = ref<DataObject>({})
const recoveryPreview = ref<DataObject | null>(null)
const recoveryResult = ref<DataObject | null>(null)
const confirmRecovery = ref(false)
const loading = ref(false)
const error = ref('')
const message = ref('')
const filters = reactive({ type: 'ALL', severity: 'ALL' })
const { subtitle, translateError } = useI18n()

const bySeverity = computed(() => (summary.value.bySeverity ?? {}) as DataObject)
const orphanKeys = computed(() => (recoveryPreview.value?.orphanKeys ?? []) as string[])
const roomProjections = computed(() => (recoveryPreview.value?.roomProjections ?? []) as DataObject[])

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [itemsResponse, summaryResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/operations/anomalies', { params: filters }),
      api.get<ObjectSuccessResponse>('/api/v1/admin/operations/anomalies/summary'),
    ])
    anomalies.value = (itemsResponse.data.data ?? []) as DataObject[]
    summary.value = (summaryResponse.data.data ?? {}) as DataObject
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function previewRecovery() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/operations/redis-recovery/preview')
    recoveryPreview.value = (response.data.data ?? {}) as DataObject
    recoveryResult.value = null
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function executeRecovery() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/operations/redis-recovery/execute')
    const result = (response.data.data ?? {}) as DataObject
    recoveryResult.value = result
    confirmRecovery.value = false
    await load()
    await previewRecovery()
    message.value = `恢复完成：清理 ${result.removedKeys ?? 0} 个失效键，重建 ${result.recreatedKeys ?? 0} 个房间投影`
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

function severityLabel(value: unknown) {
  return ({ WARNING: '提醒', ERROR: '错误', CRITICAL: '阻断' } as Record<string, string>)[String(value)] ?? String(value)
}

function typeLabel(value: unknown) {
  return ({
    UNKNOWN_BED_RESIDENCY: '实际床位待确认',
    DUPLICATE_ACTIVE_RESIDENCY: '重复有效在住',
    STALE_BATCH_ROOM_LOCK: '过期寝室锁',
    STALE_BATCH_STUDENT_LOCK: '过期学生锁',
    ORPHAN_BED_HOLD: '孤立临时占用',
  } as Record<string, string>)[String(value)] ?? String(value)
}

function detailText(value: unknown) {
  const details = (value ?? {}) as DataObject
  return Object.entries(details).map(([key, item]) => `${key}: ${String(item ?? '-')}`).join(' · ')
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div>
        <span class="eyebrow">{{ subtitle('异常工作台', 'ANOMALY WORKBENCH') }}</span>
        <h2>异常处理与缓存恢复</h2>
        <p>集中发现住宿事实、活动批次锁和 Redis 临时状态问题；高风险恢复必须先预检。</p>
      </div>
      <button class="button secondary" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="metric-grid">
      <article class="panel metric-card"><span>全部问题</span><strong>{{ summary.total ?? 0 }}</strong><small>当前可见异常</small></article>
      <article class="panel metric-card critical"><span>阻断问题</span><strong>{{ bySeverity.CRITICAL ?? 0 }}</strong><small>应立即停止相关操作</small></article>
      <article class="panel metric-card error-card"><span>错误</span><strong>{{ bySeverity.ERROR ?? 0 }}</strong><small>需要管理员处理</small></article>
      <article class="panel metric-card warning"><span>提醒</span><strong>{{ bySeverity.WARNING ?? 0 }}</strong><small>建议尽快核对</small></article>
    </section>

    <section class="panel filters-panel">
      <div class="filters">
        <label><span>问题类型</span><select v-model="filters.type" class="input"><option value="ALL">全部类型</option><option value="UNKNOWN_BED_RESIDENCY">实际床位待确认</option><option value="DUPLICATE_ACTIVE_RESIDENCY">重复有效在住</option><option value="STALE_BATCH_ROOM_LOCK">过期寝室锁</option><option value="STALE_BATCH_STUDENT_LOCK">过期学生锁</option><option value="ORPHAN_BED_HOLD">孤立临时占用</option></select></label>
        <label><span>严重程度</span><select v-model="filters.severity" class="input"><option value="ALL">全部程度</option><option value="WARNING">提醒</option><option value="ERROR">错误</option><option value="CRITICAL">阻断</option></select></label>
        <button class="button primary" @click="load">应用筛选</button>
      </div>
      <div class="anomaly-list">
        <article v-for="item in anomalies" :key="`${item.type}-${item.targetId}`" class="anomaly-card" :class="String(item.severity).toLowerCase()">
          <header><div><span class="severity-chip">{{ severityLabel(item.severity) }}</span><h3>{{ typeLabel(item.type) }}</h3></div><code>{{ item.targetId }}</code></header>
          <p>{{ item.message }}</p>
          <small>{{ detailText(item.details) }}</small>
          <footer><strong>处理建议</strong><span>{{ item.resolutionHint }}</span></footer>
        </article>
        <p v-if="!anomalies.length" class="empty-state">当前筛选条件下没有异常</p>
      </div>
    </section>

    <section class="panel recovery-panel">
      <div class="section-head"><div><span class="eyebrow">Redis 恢复</span><h3>临时状态安全恢复</h3><p>MySQL 在住记录是最终事实；系统不会猜测已经丢失的临时占用。</p></div><button class="button secondary" :disabled="loading" @click="previewRecovery">运行预检</button></div>
      <template v-if="recoveryPreview">
        <div class="recovery-metrics"><article><span>扫描临时键</span><strong>{{ recoveryPreview.scannedKeys ?? 0 }}</strong></article><article><span>待清理失效键</span><strong>{{ orphanKeys.length }}</strong></article><article><span>保留有效键</span><strong>{{ (recoveryPreview.retainedKeys as unknown[] | undefined)?.length ?? 0 }}</strong></article><article><span>待重建房间投影</span><strong>{{ roomProjections.length }}</strong></article></div>
        <ul class="warning-list"><li v-for="item in (recoveryPreview.warnings ?? []) as string[]" :key="item">{{ item }}</li></ul>
        <details v-if="orphanKeys.length"><summary>查看待清理键</summary><code v-for="key in orphanKeys" :key="key">{{ key }}</code></details>
        <button class="button danger" :disabled="loading" @click="confirmRecovery = true">确认执行恢复</button>
      </template>
      <p v-else class="empty-state">先运行预检，再决定是否执行恢复。</p>
    </section>

    <div v-if="confirmRecovery" class="modal-backdrop" @click.self="confirmRecovery = false">
      <section class="panel confirm-dialog">
        <span class="eyebrow">高风险操作确认</span>
        <h3>执行 Redis 状态恢复？</h3>
        <p>将清理 {{ orphanKeys.length }} 个失效临时键，并根据 MySQL 最终在住事实重建 {{ roomProjections.length }} 个房间占用投影。丢失的临时选床不会被猜测恢复。</p>
        <div class="action-row"><button class="button secondary" @click="confirmRecovery = false">取消</button><button class="button danger" :disabled="loading" @click="executeRecovery">确认执行</button></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.metric-card{display:grid;gap:5px}.metric-card span{color:var(--muted)}.metric-card strong{font-size:28px}.metric-card small{color:var(--muted)}.metric-card.critical{border-color:#b91c1c}.metric-card.error-card{border-color:#ea580c}.metric-card.warning{border-color:#ca8a04}.filters-panel,.recovery-panel{display:grid;gap:16px}.filters{display:grid;grid-template-columns:1fr 1fr auto;gap:10px;align-items:end}.filters label{display:grid;gap:6px}.anomaly-list{display:grid;gap:10px}.anomaly-card{padding:15px;border:1px solid var(--line);border-left-width:5px;border-radius:14px;background:var(--surface)}.anomaly-card.warning{border-left-color:#ca8a04}.anomaly-card.error{border-left-color:#ea580c}.anomaly-card.critical{border-left-color:#b91c1c}.anomaly-card header{display:flex;justify-content:space-between;gap:12px}.anomaly-card header>div{display:flex;align-items:center;gap:8px}.anomaly-card h3{margin:0}.severity-chip{padding:3px 7px;border-radius:999px;background:var(--soft);font-size:12px}.anomaly-card p{margin:10px 0}.anomaly-card small{color:var(--muted)}.anomaly-card footer{display:grid;grid-template-columns:auto 1fr;gap:8px;margin-top:12px;padding-top:10px;border-top:1px solid var(--line)}.recovery-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.recovery-metrics article{padding:13px;border:1px solid var(--line);border-radius:12px;background:var(--soft)}.recovery-metrics span{display:block;color:var(--muted);font-size:12px}.recovery-metrics strong{display:block;margin-top:5px}.warning-list{margin:0;color:#8a5a00}.recovery-panel details{display:grid;gap:5px}.recovery-panel details code{display:block;margin-top:5px}.modal-backdrop{position:fixed;inset:0;z-index:100;display:grid;place-items:center;padding:30px;background:rgba(12,24,48,.68);backdrop-filter:blur(8px)}.confirm-dialog{width:min(560px,100%);display:grid;gap:14px}.action-row{display:flex;justify-content:flex-end;gap:8px}.button.danger{background:#b91c1c;color:white;border-color:#b91c1c}.alert.success{background:#eafaf2;color:#16734f}@media(max-width:900px){.metric-grid,.recovery-metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:640px){.metric-grid,.recovery-metrics,.filters{grid-template-columns:1fr}.modal-backdrop{padding:10px}}
</style>
