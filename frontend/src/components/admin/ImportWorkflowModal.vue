<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const props = defineProps<{
  open: boolean
  importType: 'STUDENT' | 'ROOM'
  title: string
}>()

const emit = defineEmits<{
  close: []
  committed: [task: DataObject]
}>()

const file = ref<File | null>(null)
const selected = ref<DataObject | null>(null)
const loading = ref(false)
const error = ref('')
const message = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive({ idempotencyKey: '' })
const { translateError } = useI18n()

const fieldErrors = computed(() => (selected.value?.fieldErrors ?? []) as DataObject[])
const canCommit = computed(() => selected.value?.status === 'PREVIEWED' && fieldErrors.value.length === 0)

watch(() => props.open, (open) => {
  if (open) reset()
})

function reset() {
  file.value = null
  selected.value = null
  loading.value = false
  error.value = ''
  message.value = ''
  form.idempotencyKey = ''
  if (fileInput.value) fileInput.value.value = ''
}

function close() {
  if (!loading.value) emit('close')
}

function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
  selected.value = null
  error.value = ''
  message.value = ''
}

async function preview() {
  if (!file.value || loading.value) return
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const data = new FormData()
    data.append('file', file.value)
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: props.importType },
      headers: {
        ...(form.idempotencyKey.trim() ? { 'Idempotency-Key': form.idempotencyKey.trim() } : {}),
        'Content-Type': 'multipart/form-data',
      },
    })
    selected.value = (response.data.data ?? {}) as DataObject
    message.value = fieldErrors.value.length
      ? `预检完成，发现 ${fieldErrors.value.length} 项需要修正的问题。`
      : '预检通过，请核对数据行数后提交导入。'
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function commitTask() {
  if (!selected.value?.taskId || !canCommit.value || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/import-tasks/${selected.value.taskId}/commit`)
    const task = (response.data.data ?? {}) as DataObject
    selected.value = task
    message.value = `导入完成，共写入 ${Number(task.mutationCount ?? task.validRows ?? 0)} 项数据。`
    emit('committed', task)
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function exportErrors() {
  if (!selected.value?.taskId) return
  try {
    const response = await api.get(`/api/v1/admin/import-tasks/${selected.value.taskId}/errors.csv`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${props.importType === 'STUDENT' ? '学生' : '宿舍'}导入错误报告.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = translateError(cause)
  }
}
</script>

<template>
  <div v-if="open" class="modal-overlay import-workflow-overlay" @click.self="close">
    <section class="modal-card import-workflow-dialog" role="dialog" aria-modal="true" aria-labelledby="import-workflow-title">
      <header class="section-head split-title compact-section-head">
        <div><span class="eyebrow">批量导入</span><h3 id="import-workflow-title">{{ title }}</h3><p>文件会先进行预检，只有全部校验通过后才会写入正式数据。</p></div>
        <button class="button ghost small" type="button" :disabled="loading" @click="close">关闭</button>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="message" class="alert success">{{ message }}</p>

      <form class="import-workflow-form" @submit.prevent="preview">
        <label><span>选择 Excel 或 CSV 文件</span><input ref="fileInput" class="input" type="file" accept=".csv,.xls,.xlsx" required @change="chooseFile" /></label>
        <label><span>防重复标识（可选）</span><input v-model.trim="form.idempotencyKey" class="input" maxlength="200" placeholder="同一批文件重复操作时填写相同标识" /></label>
        <button class="button primary" :disabled="!file || loading">{{ loading ? '处理中…' : '上传并预检' }}</button>
      </form>

      <section v-if="selected" class="import-preview-result">
        <div class="metric-grid">
          <article><span>文件</span><strong>{{ selected.fileName }}</strong></article>
          <article><span>总行数</span><strong>{{ selected.totalRows ?? 0 }}</strong></article>
          <article><span>可导入</span><strong>{{ selected.validRows ?? 0 }}</strong></article>
          <article><span>错误行</span><strong>{{ selected.invalidRows ?? 0 }}</strong></article>
        </div>
        <div v-if="fieldErrors.length" class="table-wrap error-table">
          <table><thead><tr><th>行号</th><th>字段</th><th>填写值</th><th>问题</th></tr></thead><tbody><tr v-for="item in fieldErrors.slice(0, 20)" :key="`${item.row}-${item.field}`"><td>{{ item.row }}</td><td>{{ item.field }}</td><td>{{ item.value || '-' }}</td><td>{{ item.message }}</td></tr></tbody></table>
          <p v-if="fieldErrors.length > 20" class="more-errors">还有 {{ fieldErrors.length - 20 }} 项，请下载完整报告查看。</p>
        </div>
        <div class="button-row import-actions">
          <button class="button ghost" type="button" :disabled="!fieldErrors.length" @click="exportErrors">下载错误报告</button>
          <button class="button primary" type="button" :disabled="!canCommit || loading" @click="commitTask">确认导入 {{ selected.validRows ?? 0 }} 条</button>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.import-workflow-overlay{z-index:1320;padding:24px;background:rgba(9,23,48,.76);backdrop-filter:blur(6px)}.import-workflow-dialog{width:min(940px,calc(100vw - 48px));max-height:calc(100vh - 48px);padding:24px;overflow:auto;border-radius:24px;background:var(--panel,#fff)}.compact-section-head{margin-bottom:14px}.import-workflow-form{display:grid;grid-template-columns:minmax(260px,1.4fr) minmax(220px,1fr) auto;gap:10px;align-items:end}.import-workflow-form label{display:grid;gap:6px}.import-preview-result{display:grid;gap:14px;margin-top:18px;padding-top:18px;border-top:1px solid var(--line)}.metric-grid{display:grid;grid-template-columns:2fr repeat(3,1fr);gap:10px}.metric-grid article{min-width:0;padding:12px;border:1px solid var(--line);border-radius:13px;background:var(--soft)}.metric-grid span{display:block;color:var(--muted);font-size:12px}.metric-grid strong{display:block;margin-top:5px;overflow:hidden;text-overflow:ellipsis}.error-table{max-height:300px}.more-errors{margin:8px 0 0;color:var(--muted);font-size:13px}.import-actions{justify-content:flex-end}@media(max-width:780px){.import-workflow-form,.metric-grid{grid-template-columns:1fr}.import-workflow-overlay{padding:10px}.import-workflow-dialog{width:100%;max-height:calc(100vh - 20px);padding:18px}}
</style>
