<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const tasks = ref<DataObject[]>([])
const selected = ref<DataObject | null>(null)
const file = ref<File | null>(null)
const loading = ref(false)
const error = ref('')
const message = ref('')
const form = reactive({ importType: 'STUDENT', idempotencyKey: '' })
const { subtitle, translateError } = useI18n()

const fieldErrors = computed(() => (selected.value?.fieldErrors ?? []) as DataObject[])
const canCommit = computed(() => selected.value?.status === 'PREVIEWED' && fieldErrors.value.length === 0)
const canRollback = computed(() => selected.value?.status === 'COMMITTED')

onMounted(loadTasks)

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/import-tasks')
    tasks.value = (response.data.data ?? []) as DataObject[]
    if (selected.value) {
      selected.value = tasks.value.find(item => item.taskId === selected.value?.taskId) ?? null
    }
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function preview() {
  if (!file.value) {
    error.value = '请选择需要预检的文件'
    return
  }
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const data = new FormData()
    data.append('file', file.value)
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: form.importType },
      headers: {
        ...(form.idempotencyKey.trim() ? { 'Idempotency-Key': form.idempotencyKey.trim() } : {}),
        'Content-Type': 'multipart/form-data',
      },
    })
    selected.value = (response.data.data ?? {}) as DataObject
    message.value = fieldErrors.value.length
      ? `预检完成，发现 ${fieldErrors.value.length} 项字段错误`
      : '预检通过，可以提交导入任务'
    await loadTasks()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function selectTask(task: DataObject) {
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/import-tasks/${task.taskId}`)
    selected.value = (response.data.data ?? {}) as DataObject
  } catch (cause) {
    error.value = translateError(cause)
  }
}

async function commitTask() {
  if (!selected.value?.taskId) return
  loading.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/import-tasks/${selected.value.taskId}/commit`)
    selected.value = (response.data.data ?? {}) as DataObject
    message.value = '导入任务已提交'
    await loadTasks()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function rollbackTask() {
  if (!selected.value?.taskId) return
  loading.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/import-tasks/${selected.value.taskId}/rollback`)
    selected.value = (response.data.data ?? {}) as DataObject
    message.value = '导入任务已回滚'
    await loadTasks()
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

async function exportErrors() {
  if (!selected.value?.taskId) return
  try {
    const response = await api.get(`/api/v1/admin/import-tasks/${selected.value.taskId}/errors.csv`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `import-errors-${selected.value.taskId}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = translateError(cause)
  }
}

function statusLabel(value: unknown) {
  return ({ PREVIEWED: '已预检', COMMITTED: '已提交', ROLLED_BACK: '已回滚' } as Record<string, string>)[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div>
        <span class="eyebrow">{{ subtitle('导入质量', 'IMPORT QUALITY') }}</span>
        <h2>导入预检与回滚</h2>
        <p>文件先预检再提交，字段错误可导出，幂等键用于防止重复导入。</p>
      </div>
      <button class="button secondary" :disabled="loading" @click="loadTasks">刷新任务</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel preview-panel">
      <div class="section-head">
        <div><span class="eyebrow">第一步</span><h3>上传并预检</h3></div>
      </div>
      <form class="preview-form" @submit.prevent="preview">
        <label><span>数据类型</span><select v-model="form.importType" class="input"><option value="STUDENT">学生数据</option><option value="ROOM">宿舍数据</option></select></label>
        <label><span>导入文件</span><input class="input" type="file" accept=".csv,.xls,.xlsx" required @change="chooseFile" /></label>
        <label><span>幂等键（可选）</span><input v-model.trim="form.idempotencyKey" class="input" maxlength="200" placeholder="相同业务批次使用同一个键" /></label>
        <button class="button primary" :disabled="loading">{{ loading ? '处理中…' : '执行预检' }}</button>
      </form>
    </section>

    <section class="workspace-grid">
      <article class="panel task-list">
        <div class="section-head"><div><span class="eyebrow">任务历史</span><h3>导入任务</h3></div><span>{{ tasks.length }} 项</span></div>
        <button v-for="task in tasks" :key="String(task.taskId)" class="task-row" :class="{ active: selected?.taskId === task.taskId }" @click="selectTask(task)">
          <span><strong>{{ task.fileName }}</strong><small>{{ task.importType }} · {{ statusLabel(task.status) }}</small></span>
          <em>{{ task.validRows }}/{{ task.totalRows }}</em>
        </button>
        <p v-if="!tasks.length" class="empty-state">暂无导入任务</p>
      </article>

      <article class="panel detail-panel">
        <template v-if="selected">
          <div class="section-head"><div><span class="eyebrow">任务详情</span><h3>{{ selected.fileName }}</h3></div><span class="status-chip">{{ statusLabel(selected.status) }}</span></div>
          <div class="metric-grid"><div><span>总行数</span><strong>{{ selected.totalRows }}</strong></div><div><span>有效行</span><strong>{{ selected.validRows }}</strong></div><div><span>错误行</span><strong>{{ selected.invalidRows }}</strong></div><div><span>摘要算法</span><strong>{{ selected.digestAlgorithm }}</strong></div></div>
          <div class="action-row"><button class="button primary" :disabled="!canCommit || loading" @click="commitTask">提交导入</button><button class="button secondary" :disabled="!canRollback || loading" @click="rollbackTask">回滚任务</button><button class="button secondary" :disabled="!fieldErrors.length" @click="exportErrors">导出错误报告</button></div>
          <div class="table-wrap"><table><thead><tr><th>行号</th><th>字段</th><th>原值</th><th>错误原因</th></tr></thead><tbody><tr v-for="item in fieldErrors" :key="`${item.row}-${item.field}`"><td>{{ item.row }}</td><td>{{ item.field }}</td><td>{{ item.value || '-' }}</td><td>{{ item.message }}</td></tr></tbody></table></div>
          <p v-if="!fieldErrors.length" class="empty-state good">没有字段错误</p>
        </template>
        <p v-else class="empty-state">选择任务查看预检详情</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.preview-panel,.detail-panel,.task-list{display:grid;gap:16px}.preview-form{display:grid;grid-template-columns:150px minmax(220px,1fr) minmax(220px,1fr) auto;gap:10px;align-items:end}.preview-form label{display:grid;gap:6px}.workspace-grid{display:grid;grid-template-columns:minmax(260px,.75fr) minmax(0,1.6fr);gap:14px}.task-row{display:flex;justify-content:space-between;gap:10px;text-align:left;padding:12px;border:1px solid var(--line);border-radius:12px;background:var(--surface);cursor:pointer}.task-row.active{border-color:var(--primary);background:var(--soft)}.task-row span{display:grid;gap:4px}.task-row small{color:var(--muted)}.task-row em{font-style:normal;color:var(--muted)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric-grid div{padding:12px;border:1px solid var(--line);border-radius:12px;background:var(--soft)}.metric-grid span{display:block;color:var(--muted);font-size:12px}.metric-grid strong{display:block;margin-top:5px}.action-row{display:flex;gap:8px;flex-wrap:wrap}.status-chip{padding:5px 9px;border-radius:999px;background:var(--soft)}.empty-state{padding:22px;text-align:center;color:var(--muted)}.empty-state.good{color:#16734f}.alert.success{background:#eafaf2;color:#16734f}@media(max-width:1000px){.preview-form{grid-template-columns:1fr 1fr}.workspace-grid{grid-template-columns:1fr}.metric-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:640px){.preview-form,.metric-grid{grid-template-columns:1fr}}
</style>
