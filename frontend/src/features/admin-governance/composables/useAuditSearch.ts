import { computed, reactive, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../../api/types'

export interface AuditFilters {
  occurredFrom: string
  occurredTo: string
  operatorId: string
  operatorRole: string
  module: string
  actionType: string
  targetType: string
  targetId: string
  success: string
  errorCode: string
  requestId: string
  networkAddress: string
  keyword: string
}

const EMPTY_FILTERS: AuditFilters = {
  occurredFrom: '',
  occurredTo: '',
  operatorId: '',
  operatorRole: '',
  module: '',
  actionType: '',
  targetType: '',
  targetId: '',
  success: '',
  errorCode: '',
  requestId: '',
  networkAddress: '',
  keyword: '',
}

export function useAuditSearch(refreshExports: () => Promise<unknown>) {
  const filters = reactive<AuditFilters>({ ...EMPTY_FILTERS })
  const rows = ref<DataObject[]>([])
  const selectedAudit = ref<DataObject | null>(null)
  const total = ref(0)
  const busy = ref(false)
  const error = ref('')
  const message = ref('')
  const exportConfirm = ref(false)
  const includeSensitive = ref(false)

  const payload = computed(() => ({
    occurredFrom: toIso(filters.occurredFrom),
    occurredTo: toIso(filters.occurredTo),
    operatorId: numberOrNull(filters.operatorId),
    operatorRole: filters.operatorRole,
    module: filters.module,
    actionType: filters.actionType,
    targetType: filters.targetType,
    targetId: filters.targetId,
    success: filters.success === '' ? null : filters.success === 'true',
    errorCode: filters.errorCode,
    requestId: filters.requestId,
    networkAddress: filters.networkAddress,
    keyword: filters.keyword,
    page: 1,
    size: 50,
  }))

  async function query() {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      const response = await api.post<ObjectSuccessResponse>(
        '/api/v1/admin/governance/audit/query',
        payload.value,
      )
      const data = (response.data.data ?? {}) as DataObject
      rows.value = (data.items ?? []) as DataObject[]
      total.value = Number(data.total ?? rows.value.length)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '审计查询失败'
    } finally {
      busy.value = false
    }
  }

  function updateFilters(value: AuditFilters) {
    Object.assign(filters, value)
  }

  function reset() {
    Object.assign(filters, EMPTY_FILTERS)
    selectedAudit.value = null
  }

  function openExport(sensitive: boolean) {
    includeSensitive.value = sensitive
    exportConfirm.value = true
  }

  async function requestExport(request: { reason: string }) {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      await api.post('/api/v1/admin/governance/audit/export', {
        query: payload.value,
        includeSensitiveData: includeSensitive.value,
        reason: request.reason,
      })
      message.value = '审计导出已进入异步任务队列。'
      await refreshExports()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '创建审计导出任务失败'
      throw cause
    } finally {
      busy.value = false
    }
  }

  function auditJson(value: unknown) {
    if (value == null || value === '') return '无'
    try {
      return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
    } catch {
      return String(value)
    }
  }

  return {
    filters,
    rows,
    selectedAudit,
    total,
    busy,
    error,
    message,
    exportConfirm,
    includeSensitive,
    payload,
    query,
    updateFilters,
    reset,
    openExport,
    requestExport,
    auditJson,
  }
}

function numberOrNull(value: string) {
  const parsed = Number(value)
  return value.trim() && Number.isFinite(parsed) ? parsed : null
}

function toIso(value: string) {
  return value ? new Date(value).toISOString() : null
}
