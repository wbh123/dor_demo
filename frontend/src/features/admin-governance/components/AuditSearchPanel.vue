<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AuditFilterBar from '../../../components/admin/AuditFilterBar.vue'
import ExportTaskPanel from '../../../components/admin/ExportTaskPanel.vue'
import type { AuditFilters } from '../composables/useAuditSearch'

const props = defineProps<{
  filters: AuditFilters
  rows: DataObject[]
  selectedAudit: DataObject | null
  total: number
  tasks: DataObject[]
  canQuery: boolean
  canExport: boolean
  canMaskedExport: boolean
  canSensitiveExport: boolean
  busy?: boolean
  exportBusy?: boolean
  error?: string
  message?: string
  exportError?: string
  exportMessage?: string
  formatJson: (value: unknown) => string
}>()

const emit = defineEmits<{
  'update:filters': [value: AuditFilters]
  search: []
  reset: []
  'select-audit': [value: DataObject | null]
  'request-export': [includeSensitive: boolean]
  'refresh-exports': []
  'cancel-export': [taskId: number]
}>()
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">AUDIT</span>
        <h3>高级审计</h3>
        <p>审计记录不可由学校管理员修改或删除；平台授权审计不在此查询中。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="exportError" class="alert error">{{ exportError }}</p>
    <p v-if="exportMessage" class="alert success">{{ exportMessage }}</p>

    <AuditFilterBar
      v-if="canQuery"
      :model-value="filters"
      :busy="busy"
      @update:model-value="emit('update:filters', $event)"
      @search="emit('search')"
      @reset="emit('reset')"
    />
    <p v-else class="alert warning">当前仅开通审计导出，未开通高级查询。</p>

    <div class="button-row">
      <button
        v-if="canMaskedExport"
        class="button secondary"
        type="button"
        :disabled="busy"
        @click="emit('request-export', false)"
      >脱敏导出</button>
      <button
        v-if="canSensitiveExport"
        class="button danger"
        type="button"
        :disabled="busy"
        @click="emit('request-export', true)"
      >完整敏感导出</button>
      <span class="result-count">查询结果 {{ total }} 条</span>
    </div>

    <div v-if="canQuery" class="table-wrap">
      <table>
        <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>目标</th><th>结果</th><th>请求编号</th><th>网络地址</th><th>详情</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="String(row.id)">
            <td>{{ row.occurred_at }}</td>
            <td>{{ row.operator_type }} #{{ row.operator_user_id }}</td>
            <td>{{ row.action_type }}</td>
            <td>{{ row.resource_type }} {{ row.resource_id }}</td>
            <td>{{ row.result_status }}</td>
            <td>{{ row.request_id }}</td>
            <td>{{ row.network_address }}</td>
            <td><button class="button ghost small" type="button" @click="emit('select-audit', row)">查看</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <article v-if="selectedAudit" class="audit-detail-card">
      <div class="section-head">
        <div>
          <span class="eyebrow">AUDIT DETAIL</span>
          <h4>{{ selectedAudit.action_type }}</h4>
          <p>{{ selectedAudit.module || selectedAudit.resource_type }} · {{ selectedAudit.occurred_at }}</p>
        </div>
        <button class="button ghost small" type="button" @click="emit('select-audit', null)">关闭</button>
      </div>
      <div class="audit-detail-grid">
        <div><span>结果</span><strong>{{ selectedAudit.result_status }}</strong></div>
        <div><span>错误代码</span><strong>{{ selectedAudit.error_code || '无' }}</strong></div>
        <div><span>原因</span><strong>{{ selectedAudit.reason || '未填写' }}</strong></div>
        <div><span>请求编号</span><strong>{{ selectedAudit.request_id || '无' }}</strong></div>
      </div>
      <div class="audit-json-grid">
        <div><strong>变更前</strong><pre>{{ props.formatJson(selectedAudit.before_data) }}</pre></div>
        <div><strong>变更后</strong><pre>{{ props.formatJson(selectedAudit.after_data) }}</pre></div>
      </div>
    </article>

    <ExportTaskPanel
      v-if="canExport"
      :tasks="tasks"
      :busy="exportBusy"
      @refresh="emit('refresh-exports')"
      @cancel="emit('cancel-export', $event)"
    />
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.result-count{margin-left:auto;color:var(--text-muted)}.audit-detail-card{display:grid;gap:14px;margin-top:14px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.audit-detail-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.audit-detail-grid div{display:grid;gap:5px;padding:11px;border-radius:11px;background:var(--surface)}.audit-detail-grid span{color:var(--text-muted);font-size:12px}.audit-json-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.audit-json-grid pre{max-height:240px;overflow:auto;white-space:pre-wrap}@media(max-width:760px){.audit-detail-grid,.audit-json-grid{grid-template-columns:1fr}}
</style>
