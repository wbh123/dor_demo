<script setup lang="ts">
import FairnessPreviewPanel from '../../features/admin-operations/components/FairnessPreviewPanel.vue'
import OperationsHealthSummary from '../../features/admin-operations/components/OperationsHealthSummary.vue'
import OperationsMetricsGrid from '../../features/admin-operations/components/OperationsMetricsGrid.vue'
import { useAdminOperationsPage } from '../../features/admin-operations/composables/useAdminOperationsPage'

const {
  overview,
  health,
  preview,
  loading,
  previewLoading,
  error,
  form,
  selectableBatches,
  subtitle,
  load,
  loadPreview,
} = useAdminOperationsPage()
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('运营分析', 'OPERATIONS') }}</span><h2>运营与健康</h2><p>集中查看床位利用率、未选学生、人工调整和统一分配公平性。</p></div>
      <button class="button secondary" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="loading" class="panel empty-state">正在加载运营与健康数据…</p>
    <p v-else-if="error" class="alert error">{{ error }}</p>
    <template v-else>
      <OperationsMetricsGrid :overview="overview" />
      <OperationsHealthSummary :health="health" />
      <FairnessPreviewPanel
        :batches="selectableBatches"
        :batch-id="form.batchId"
        :random-seed="form.randomSeed"
        :preview="preview"
        :loading="previewLoading"
        @update:batch-id="form.batchId = $event"
        @update:random-seed="form.randomSeed = $event"
        @preview="loadPreview"
      />
    </template>
  </div>
</template>
