<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import ExportTaskPanel from '../../../components/admin/ExportTaskPanel.vue'
import ReportBuilder from '../../../components/admin/ReportBuilder.vue'
import type {
  ReportDefinition,
  ReportMetadata,
} from '../composables/useReportWorkspace'

const props = defineProps<{
  metadata: ReportMetadata
  definition: ReportDefinition
  reason: string
  tasks: DataObject[]
  busy?: boolean
  exportBusy?: boolean
  error?: string
  message?: string
  exportError?: string
  exportMessage?: string
}>()

const emit = defineEmits<{
  'update:definition': [value: ReportDefinition]
  'update:reason': [value: string]
  save: []
  export: []
  'refresh-exports': []
  'cancel-export': [taskId: number]
}>()
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">REPORT</span>
        <h3>自定义报表</h3>
        <p>只能使用字段、筛选、排序和指标白名单，禁止输入任意结构化查询语言。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="exportError" class="alert error">{{ exportError }}</p>
    <p v-if="exportMessage" class="alert success">{{ exportMessage }}</p>

    <label>
      <span>保存或生成原因</span>
      <textarea
        class="input"
        rows="3"
        :value="reason"
        @input="emit('update:reason', ($event.target as HTMLTextAreaElement).value)"
      />
    </label>

    <ReportBuilder
      :model-value="definition"
      v-bind="metadata"
      :busy="busy"
      @update:model-value="emit('update:definition', $event)"
      @save="emit('save')"
      @export="emit('export')"
    />

    <ExportTaskPanel
      :tasks="tasks"
      :busy="exportBusy"
      @refresh="emit('refresh-exports')"
      @cancel="emit('cancel-export', $event)"
    />
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.governance-section>label{display:grid;gap:7px}
</style>
