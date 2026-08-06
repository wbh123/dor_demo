import { ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../../api/types'

export function useDataRetention() {
  const policy = ref<DataObject | null>(null)
  const statistics = ref<DataObject | null>(null)
  const simulation = ref<DataObject | null>(null)
  const busy = ref(false)
  const error = ref('')
  const message = ref('')

  async function execute(action: () => Promise<void>) {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      await action()
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '数据保留操作失败'
      return false
    } finally {
      busy.value = false
    }
  }

  async function load() {
    await execute(async () => {
      const [policyResponse, statisticsResponse] = await Promise.all([
        api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/policy'),
        api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/statistics'),
      ])
      policy.value = (policyResponse.data.data ?? {}) as DataObject
      statistics.value = (statisticsResponse.data.data ?? {}) as DataObject
    })
  }

  async function simulate() {
    await execute(async () => {
      const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/simulate')
      simulation.value = (response.data.data ?? {}) as DataObject
      message.value = '模拟清理结果已更新，本次未删除任何数据。'
    })
  }

  async function preflight(payload: { reason: string }) {
    const succeeded = await execute(async () => {
      await api.post('/api/v1/admin/governance/retention/preflight', { reason: payload.reason })
      const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/retention/simulate')
      simulation.value = (response.data.data ?? {}) as DataObject
      message.value = '数据保留清理预检已记录，本轮不会执行删除。'
    })
    if (!succeeded) throw new Error(error.value || '数据保留预检失败')
  }

  return {
    policy,
    statistics,
    simulation,
    busy,
    error,
    message,
    load,
    simulate,
    preflight,
  }
}
