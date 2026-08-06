import { onBeforeUnmount, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ListSuccessResponse } from '../../../api/types'

export function useGovernanceExports() {
  const tasks = ref<DataObject[]>([])
  const busy = ref(false)
  const error = ref('')
  const message = ref('')
  let pollTimer: number | undefined

  async function load(options: { silent?: boolean } = {}) {
    if (!options.silent) busy.value = true
    error.value = ''
    try {
      const response = await api.get<ListSuccessResponse>('/api/v1/admin/governance/exports')
      tasks.value = (response.data.data ?? []) as DataObject[]
      syncPolling()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '导出任务加载失败'
    } finally {
      if (!options.silent) busy.value = false
    }
  }

  async function cancel(taskId: number) {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      await api.post(`/api/v1/admin/governance/exports/${taskId}/cancel`)
      message.value = '导出任务已取消。'
      await load({ silent: true })
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '取消导出任务失败'
    } finally {
      busy.value = false
    }
  }

  async function refreshAfterQueued(text: string) {
    message.value = text
    await load({ silent: true })
  }

  function syncPolling() {
    const active = tasks.value.some((task) => ['QUEUED', 'RUNNING'].includes(String(task.task_status)))
    if (active && !pollTimer) {
      pollTimer = window.setInterval(() => void load({ silent: true }), 2000)
    } else if (!active) {
      stopPolling()
    }
  }

  function stopPolling() {
    if (!pollTimer) return
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }

  function clearFeedback() {
    error.value = ''
    message.value = ''
  }

  onBeforeUnmount(stopPolling)

  return {
    tasks,
    busy,
    error,
    message,
    load,
    cancel,
    refreshAfterQueued,
    clearFeedback,
  }
}
