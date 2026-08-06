<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AnalyticsFilterBar from '../../../components/admin/AnalyticsFilterBar.vue'
import MetricDefinitionPopover from '../../../components/admin/MetricDefinitionPopover.vue'
import type {
  AnalyticsFilters,
  AnalyticsMode,
} from '../composables/useHistoricalAnalytics'

const props = defineProps<{
  filters: AnalyticsFilters
  definitions: DataObject[]
  items: DataObject[]
  mode: AnalyticsMode
  modes: Array<{ key: AnalyticsMode; label: string }>
  privacy: DataObject
  busy?: boolean
  error?: string
  message?: string
}>()

const emit = defineEmits<{
  'update:filters': [value: AnalyticsFilters]
  'update:mode': [value: AnalyticsMode]
  run: []
  reset: []
}>()
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">ANALYTICS</span>
        <h3>历史分析</h3>
        <p>Historical analytics · 已结束批次使用不可变快照，后续换寝不会改变历史口径。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="mode-switch">
      <button
        v-for="item in modes"
        :key="item.key"
        class="button"
        :class="mode===item.key?'primary':'ghost'"
        type="button"
        :disabled="busy"
        @click="emit('update:mode', item.key)"
      >{{ item.label }}</button>
    </div>

    <AnalyticsFilterBar
      :model-value="filters"
      :busy="busy"
      @update:model-value="emit('update:filters', $event)"
      @apply="emit('run')"
      @reset="emit('reset')"
    />

    <div class="analytics-summary-grid">
      <article><span>结果批次</span><strong>{{ items.length }}</strong></article>
      <article><span>统计口径</span><strong>{{ items[0]?.metric_version || '待查询' }}</strong></article>
      <article><span>隐私阈值</span><strong>{{ privacy.privacyThreshold || '—' }}</strong></article>
    </div>
    <p v-if="privacy.preferenceDimensionsSuppressed" class="alert warning">
      当前组合样本少于 {{ privacy.privacyThreshold }} 人，已隐藏个人偏好维度。
    </p>

    <div class="metric-definition-grid">
      <article v-for="definition in definitions" :key="String(definition.code)">
        <div><strong>{{ definition.nameZhCn }}</strong><span>{{ definition.nameEnUs }}</span></div>
        <MetricDefinitionPopover :definition="definition" />
      </article>
    </div>

    <div class="analytics-result-grid">
      <article v-for="item in items" :key="String(item.id)">
        <strong>{{ item.batch_name }}</strong>
        <span>{{ item.metric_version }} · {{ item.data_updated_at }}</span>
        <pre>{{ JSON.stringify(item.metrics, null, 2) }}</pre>
      </article>
    </div>
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.mode-switch{display:flex;gap:8px;flex-wrap:wrap}.analytics-summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:12px 0}.analytics-summary-grid article{display:grid;gap:5px;padding:11px;border-radius:11px;background:var(--surface)}.analytics-summary-grid span{color:var(--text-muted);font-size:12px}.metric-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px}.metric-definition-grid article{display:flex;justify-content:space-between;gap:10px;padding:12px;border:1px solid var(--border);border-radius:12px}.metric-definition-grid article>div{display:grid;gap:3px}.metric-definition-grid span{color:var(--text-muted);font-size:12px}.analytics-result-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}.analytics-result-grid article{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px}.analytics-result-grid span{color:var(--text-muted);font-size:12px}.analytics-result-grid pre{max-height:360px;overflow:auto;margin:0;white-space:pre-wrap;font-size:11px}@media(max-width:760px){.analytics-summary-grid{grid-template-columns:1fr}}
</style>
