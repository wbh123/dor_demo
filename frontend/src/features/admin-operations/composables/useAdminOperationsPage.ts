import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../../api/types'
import { useI18n } from '../../../i18n'

export function useAdminOperationsPage() {
  const overview = ref<DataObject>({})
  const health = ref<DataObject>({})
  const batches = ref<DataObject[]>([])
  const preview = ref<DataObject | null>(null)
  const loading = ref(true)
  const previewLoading = ref(false)
  const error = ref('')
  const form = reactive({ batchId: 0, randomSeed: 2026 })
  const { subtitle, translateError } = useI18n()

  const selectableBatches = computed(() => batches.value.filter((batch) =>
    !['DRAFT', 'CANCELLED'].includes(String(batch.batch_status))))

  onMounted(load)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const [overviewResponse, healthResponse, batchResponse] = await Promise.all([
        api.get<ObjectSuccessResponse>('/api/v1/admin/operations/overview'),
        api.get<ObjectSuccessResponse>('/api/v1/admin/operations/health'),
        api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      ])
      overview.value = (overviewResponse.data.data ?? {}) as DataObject
      health.value = (healthResponse.data.data ?? {}) as DataObject
      batches.value = (batchResponse.data.data ?? []) as DataObject[]
      if (!form.batchId && selectableBatches.value.length) {
        form.batchId = Number(selectableBatches.value[0].id)
      }
    } catch (cause) {
      error.value = translateError(cause)
    } finally {
      loading.value = false
    }
  }

  async function loadPreview() {
    if (!form.batchId || previewLoading.value) return
    previewLoading.value = true
    error.value = ''
    try {
      const response = await api.get<ObjectSuccessResponse>(
        `/api/v1/admin/batches/${form.batchId}/allocation/optimized-preview`,
        { params: { randomSeed: form.randomSeed } },
      )
      preview.value = (response.data.data ?? {}) as DataObject
    } catch (cause) {
      error.value = translateError(cause)
    } finally {
      previewLoading.value = false
    }
  }

  return {
    overview,
    health,
    batches,
    preview,
    loading,
    previewLoading,
    error,
    form,
    selectableBatches,
    subtitle,
    load,
    loadPreview,
  }
}
