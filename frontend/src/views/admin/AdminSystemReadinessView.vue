<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/systemReadinessSchema'

type ReadinessReport = components['schemas']['SystemReadinessReport']
type ReadinessCheck = components['schemas']['ReadinessCheckResult']

const categoryLabels: Record<string, string> = {
  INFRASTRUCTURE: '基础设施',
  RESOURCE: '宿舍资源',
  STUDENT: '学生数据',
  BATCH: '选寝批次',
  AUTHORIZATION: '权限授权',
  MOBILE: '移动应用',
  LABEL: '现场管理',
}

const report = ref<ReadinessReport | null>(null)
const loading = ref(false)
const error = ref('')

const overallText = computed(() => {
  if (!report.value) return '尚未检查'
  if (report.value.overallStatus === 'READY') return '可以上线'
  if (report.value.overallStatus === 'READY_WITH_WARNINGS') return '可以运行，但存在风险'
  if (report.value.overallStatus === 'BLOCKED') return '存在阻断问题，禁止开放'
  return '状态未知'
})

const groupedChecks = computed(() => {
  const groups = new Map<string, ReadinessCheck[]>()
  for (const item of report.value?.checks ?? []) {
    const current = groups.get(item.category) ?? []
    current.push(item)
    groups.set(item.category, current)
  }
  return [...groups.entries()]
})

function severityLabel(item: ReadinessCheck) {
  if (item.blocking || item.severity === 'ERROR') return '× 阻断'
  if (item.severity === 'WARNING') return '! 风险'
  if (item.severity === 'INFO') return 'i 提示'
  return '✓ 正常'
}

function formatTime(value: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}

function formatEvidence(evidence: Record<string, unknown>) {
  const entries = Object.entries(evidence ?? {})
  if (!entries.length) return '无额外诊断数据'
  return entries.map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`).join('\n')
}

async function runCheck() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ReadinessReport>('/api/v1/admin/system-readiness')
    report.value = response.data
  } catch (reason: any) {
    error.value = reason?.response?.data?.message || reason?.message || '上线体检执行失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(runCheck)
</script>

<template>
  <div class="content-column readiness-page">
    <header class="page-title split-title">
      <div>
        <span class="eyebrow">试点准备中心</span>
        <h2>上线体检</h2>
        <p>一次检查数据库、宿舍资源、学生数据、活动批次、授权、移动应用和现场管理准备状态。</p>
      </div>
      <button class="button primary" :disabled="loading" @click="runCheck">{{ loading ? '检查中…' : '重新检查' }}</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>

    <section v-if="report" class="panel readiness-hero" :data-status="report.overallStatus">
      <div>
        <span class="eyebrow">当前结论</span>
        <h3>{{ overallText }}</h3>
        <p v-if="report.overallStatus === 'READY'">没有发现阻断问题或显著风险，可以进入单校试点。</p>
        <p v-else-if="report.overallStatus === 'READY_WITH_WARNINGS'">核心条件已具备，但建议在开放前处理下方风险项。</p>
        <p v-else-if="report.overallStatus === 'BLOCKED'">存在必须先处理的问题；体检中心不会自动修改任何业务数据。</p>
        <p v-else>当前返回了未识别的体检状态，请刷新后重试。</p>
      </div>
      <div class="readiness-summary">
        <strong>{{ report.summary.blocking }}</strong><span>阻断</span>
        <strong>{{ report.summary.warnings }}</strong><span>风险</span>
        <strong>{{ report.summary.passed }}</strong><span>正常</span>
      </div>
      <small>最近检查：{{ formatTime(report.checkedAt) }}</small>
    </section>

    <p v-else-if="loading" class="panel empty-state">正在执行上线体检…</p>

    <section v-for="[category, checks] in groupedChecks" :key="category" class="content-column readiness-category">
      <div class="section-heading">
        <div><span class="eyebrow">{{ category }}</span><h3>{{ categoryLabels[category] ?? category }}</h3></div>
        <span class="badge">{{ checks.length }} 项</span>
      </div>

      <article v-for="item in checks" :key="item.code" class="panel readiness-check" :data-severity="item.severity">
        <div class="readiness-check-head">
          <div>
            <span class="readiness-state">{{ severityLabel(item) }}</span>
            <h4>{{ item.title }}</h4>
          </div>
          <RouterLink v-if="item.actionRoute" class="button ghost" :to="item.actionRoute">去处理</RouterLink>
        </div>
        <p>{{ item.summary }}</p>
        <p v-if="item.suggestedAction" class="readiness-action"><strong>建议：</strong>{{ item.suggestedAction }}</p>
        <details>
          <summary>查看诊断详情</summary>
          <pre>{{ formatEvidence(item.evidence) }}</pre>
          <div class="readiness-meta"><span>检查码：{{ item.code }}</span><span>检查时间：{{ formatTime(item.checkedAt) }}</span></div>
        </details>
      </article>
    </section>
  </div>
</template>

<style scoped>
.readiness-page { gap: 18px; }
.readiness-hero { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 18px; align-items: center; }
.readiness-hero > small { grid-column: 1 / -1; opacity: .72; }
.readiness-summary { display: grid; grid-template-columns: repeat(3, auto); gap: 4px 12px; text-align: center; }
.readiness-summary strong { font-size: 1.65rem; }
.readiness-summary span { font-size: .82rem; opacity: .72; }
.readiness-category { gap: 10px; }
.section-heading, .readiness-check-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading h3, .readiness-check h4 { margin: 2px 0 0; }
.readiness-check { display: grid; gap: 10px; }
.readiness-state { font-weight: 700; }
.readiness-check[data-severity='PASS'] .readiness-state { color: var(--success, #238636); }
.readiness-check[data-severity='WARNING'] .readiness-state { color: var(--warning, #9a6700); }
.readiness-check[data-severity='ERROR'] .readiness-state { color: var(--danger, #cf222e); }
.readiness-action { margin: 0; }
details summary { cursor: pointer; font-weight: 600; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 10px 0; padding: 10px; border-radius: 10px; background: var(--surface-muted, rgba(127,127,127,.08)); font: inherit; font-size: .86rem; }
.readiness-meta { display: flex; flex-wrap: wrap; gap: 8px 18px; font-size: .8rem; opacity: .7; }
@media (max-width: 760px) { .readiness-hero { grid-template-columns: 1fr; } }
</style>
