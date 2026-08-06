import { computed, reactive, ref, watchEffect } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../../api/types'

export interface AnalyticsFilters {
  academicYear: string
  batchId: string
  majorId: string
  gradeYear: string
  degreeLevel: string
  studentCategory: string
  campusId: string
  buildingId: string
  roomType: string
}

export type AnalyticsMode = 'dashboard' | 'comparison' | 'trend'

interface AnalyticsCapabilities {
  canHistoricalDashboard: () => boolean
  canComparison: () => boolean
  canTrend: () => boolean
}

const EMPTY_FILTERS: AnalyticsFilters = {
  academicYear: '', batchId: '', majorId: '', gradeYear: '', degreeLevel: '',
  studentCategory: '', campusId: '', buildingId: '', roomType: '',
}

export function useHistoricalAnalytics(capabilities: AnalyticsCapabilities) {
  const filters = reactive<AnalyticsFilters>({ ...EMPTY_FILTERS })
  const definitions = ref<DataObject[]>([])
  const items = ref<DataObject[]>([])
  const mode = ref<AnalyticsMode>('dashboard')
  const privacy = ref<DataObject>({})
  const busy = ref(false)
  const error = ref('')
  const message = ref('')

  const modes = computed(() => [
    capabilities.canHistoricalDashboard() ? { key: 'dashboard' as const, label: '历史看板' } : null,
    capabilities.canComparison() ? { key: 'comparison' as const, label: '跨批次比较' } : null,
    capabilities.canTrend() ? { key: 'trend' as const, label: '趋势分析' } : null,
  ].filter(Boolean) as Array<{ key: AnalyticsMode; label: string }>)

  watchEffect(() => {
    if (modes.value.length && !modes.value.some((item) => item.key === mode.value)) {
      mode.value = modes.value[0].key
    }
  })

  async function loadDefinitions() {
    busy.value = true
    error.value = ''
    try {
      const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/governance/analytics/definitions')
      definitions.value = ((((response.data.data ?? {}) as DataObject).items ?? []) as DataObject[])
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '指标定义加载失败'
    } finally {
      busy.value = false
    }
  }

  async function run() {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
      const response = await api.post<ObjectSuccessResponse>(
        `/api/v1/admin/governance/analytics/${mode.value}`,
        payload(),
      )
      const data = (response.data.data ?? {}) as DataObject
      items.value = (data.items ?? []) as DataObject[]
      privacy.value = data
      message.value = `历史分析已更新，共返回 ${items.value.length} 个批次结果。`
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '历史分析查询失败'
    } finally {
      busy.value = false
    }
  }

  function updateFilters(value: AnalyticsFilters) {
    Object.assign(filters, value)
  }

  function reset() {
    Object.assign(filters, EMPTY_FILTERS)
    items.value = []
    privacy.value = {}
    message.value = ''
  }

  function payload() {
    return {
      academicYear: numberOrNull(filters.academicYear),
      batchId: numberOrNull(filters.batchId),
      majorId: numberOrNull(filters.majorId),
      gradeYear: numberOrNull(filters.gradeYear),
      degreeLevel: filters.degreeLevel,
      studentCategory: filters.studentCategory,
      campusId: numberOrNull(filters.campusId),
      buildingId: numberOrNull(filters.buildingId),
      roomType: filters.roomType,
    }
  }

  return {
    filters,
    definitions,
    items,
    mode,
    privacy,
    modes,
    busy,
    error,
    message,
    loadDefinitions,
    run,
    updateFilters,
    reset,
  }
}

function numberOrNull(value: string) {
  const parsed = Number(value)
  return value.trim() && Number.isFinite(parsed) ? parsed : null
}
