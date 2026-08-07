<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'
import StudentRoomChangeView from './StudentRoomChangeView.vue'

const loading = ref(true)
const error = ref('')
const residency = ref<DataObject>({})
const { translateError } = useI18n()

const eligible = computed(() => {
  if (!Boolean(residency.value.resident)) return false
  const current = (residency.value.residency ?? {}) as DataObject
  return Number(current.room_id ?? 0) > 0 && Number(current.bed_id ?? 0) > 0
})

onMounted(async () => {
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/student/residency')
    residency.value = (response.data.data ?? {}) as DataObject
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="loading" class="panel empty-state">正在确认当前住宿信息…</div>
  <p v-else-if="error" class="alert error">{{ error }}</p>
  <section v-else-if="!eligible" class="panel room-change-residency-gate" role="status">
    <strong>只有当前已入住学生可以申请换寝</strong>
  </section>
  <StudentRoomChangeView v-else />
</template>

<style scoped>
.room-change-residency-gate{display:flex;align-items:center;justify-content:center;min-height:180px;text-align:center}.room-change-residency-gate strong{font-size:1.05rem}
</style>
