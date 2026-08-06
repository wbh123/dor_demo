<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject } from '../../api/types'

defineProps<{ tasks: DataObject[]; busy?: boolean }>()
const emit = defineEmits<{ refresh: []; cancel: [taskId: number] }>()
const downloadingId = ref<number | null>(null)
const downloadError = ref('')

function statusText(value: unknown) {
  return ({ QUEUED: '排队中', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[String(value)] ?? String(value)
}
function taskTypeText(value:unknown){return({AUDIT_EXPORT:'审计记录导出',CUSTOM_REPORT:'自定义报表导出'}as Record<string,string>)[String(value)]??String(value)}
async function download(task:DataObject){
  const taskId = Number(task.id)
  if (!taskId || downloadingId.value) return
  downloadingId.value = taskId
  downloadError.value = ''
  try {
    const response = await api.get(`/api/v1/admin/governance/exports/${taskId}/download`, {
      params: { token: String(task.downloadToken ?? '') },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = String(task.file_name ?? 'export.csv')
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (cause) {
    downloadError.value = cause instanceof Error ? cause.message : '导出文件下载失败'
  } finally {
    downloadingId.value = null
  }
}
</script>

<template>
  <section class="export-task-panel">
    <header><div><strong>异步导出任务</strong><span>大结果不会阻塞在线选寝，完成后在有效期内下载。</span></div><button class="button ghost small" type="button" :disabled="busy" @click="emit('refresh')">刷新</button></header>
    <p v-if="downloadError" class="alert error">{{ downloadError }}</p>
    <div class="task-list">
      <article v-for="task in tasks" :key="String(task.id)">
        <div><strong>{{ taskTypeText(task.task_type) }}</strong><span>{{ statusText(task.task_status) }} · {{ task.progress ?? 0 }}%</span></div>
        <progress :value="Number(task.progress ?? 0)" max="100" />
        <small v-if="task.error_message" class="error-text">{{ task.error_message }}</small>
        <small v-else>下载有效期：{{ task.expires_at ?? '任务完成后24小时' }}</small>
        <button v-if="task.task_status === 'QUEUED'" class="button ghost small" type="button" @click="emit('cancel', Number(task.id))">取消</button>
        <button v-else-if="task.task_status === 'SUCCEEDED'" class="button primary small" type="button" :disabled="downloadingId !== null" @click="download(task)">{{ downloadingId === Number(task.id) ? '下载中…' : '下载文件' }}</button>
      </article>
      <p v-if="tasks.length === 0" class="empty-state compact">暂无导出任务。</p>
    </div>
  </section>
</template>

<style scoped>
.export-task-panel{display:grid;gap:12px}.export-task-panel>header{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.export-task-panel>header div{display:grid;gap:4px}.export-task-panel>header span,.task-list span,.task-list small{color:var(--text-muted);font-size:12px}.task-list{display:grid;gap:10px}.task-list article{display:grid;grid-template-columns:minmax(160px,1fr) minmax(150px,2fr) minmax(180px,1fr) auto;align-items:center;gap:12px;padding:12px;border:1px solid var(--border);border-radius:12px}.task-list article>div{display:grid;gap:3px}.task-list progress{width:100%}.error-text{color:#b4232f!important}@media(max-width:720px){.task-list article{grid-template-columns:1fr}.export-task-panel>header{flex-direction:column}}
</style>
