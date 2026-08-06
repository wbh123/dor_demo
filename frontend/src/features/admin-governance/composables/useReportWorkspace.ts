import { reactive, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../../api/types'

export interface ReportMetadata {
  fields: string[]
  filters: string[]
  sorts: string[]
  metrics: string[]
}

export interface ReportDefinition {
  name: string
  fields: string[]
  filters: Record<string, unknown>
  sorts: string[]
  metrics: string[]
  locale: 'zh-CN' | 'en-US'
}

export function useReportWorkspace(refreshExports: () => Promise<unknown>) {
  const metadata = reactive<ReportMetadata>({ fields: [], filters: [], sorts: [], metrics: [] })
  const definition = reactive<ReportDefinition>({
    name: '批次历史分析报表',
    fields: [],
    filters: {},
    sorts: [],
    metrics: [],
    locale: 'zh-CN',
  })
  const reason = ref('')
  const busy = ref(false)
  const error = ref('')
  const message = ref('')

  async function execute(action: () => Promise<void>) {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      await action()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '报表操作失败'
    } finally {
      busy.value = false
    }
  }

  async function loadMetadata() {
    await execute(async () => {
      const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/reports/metadata')
      const data = (response.data.data ?? {}) as DataObject
      metadata.fields = (data.fields ?? []) as string[]
      metadata.filters = (data.filters ?? []) as string[]
      metadata.sorts = (data.sorts ?? []) as string[]
      metadata.metrics = (data.metrics ?? []) as string[]
    })
  }

  async function save() {
    await execute(async () => {
      await api.post('/api/v1/admin/governance/reports/templates', {
        definition,
        reason: reason.value,
      })
      message.value = '报表模板已保存。'
    })
  }

  async function exportReport() {
    await execute(async () => {
      await api.post('/api/v1/admin/governance/reports/export', {
        definition,
        reason: reason.value,
      })
      message.value = '报表已进入异步生成队列。'
      await refreshExports()
    })
  }

  function updateDefinition(value: ReportDefinition) {
    Object.assign(definition, value)
  }

  return {
    metadata,
    definition,
    reason,
    busy,
    error,
    message,
    loadMetadata,
    save,
    exportReport,
    updateDefinition,
  }
}
