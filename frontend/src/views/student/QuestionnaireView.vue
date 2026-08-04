<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import QuestionnaireContent from './QuestionnaireContent.vue'

const route = useRoute()
const globalMode = computed(() => !Number(route.params.batchId || 0))
const checking = ref(true)
const directPreferenceWithoutBatchAllowed = ref(true)
const includedInSelectionBatch = ref(false)
const error = ref('')

onMounted(checkAccess)

async function checkAccess() {
  if (!globalMode.value) {
    checking.value = false
    return
  }
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/student/preferences')
    const data = (response.data.data ?? {}) as DataObject
    directPreferenceWithoutBatchAllowed.value = Boolean(data.directPreferenceWithoutBatchAllowed ?? true)
    includedInSelectionBatch.value = Boolean(data.includedInSelectionBatch)
  } catch (cause) {
    directPreferenceWithoutBatchAllowed.value = false
    error.value = cause instanceof Error
      ? cause.message
      : '管理员当前未开放无批次直接设置个人偏好。'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <p v-if="checking" class="panel empty-state">正在检查个人偏好开放策略…</p>
  <section v-else-if="globalMode && !includedInSelectionBatch && !directPreferenceWithoutBatchAllowed" class="panel preference-closed-card">
    <span class="eyebrow">PERSONAL PREFERENCES</span>
    <h2>个人偏好暂未开放</h2>
    <p>{{ error || '你当前没有被包含在可参与的选寝批次中，管理员尚未开放直接设置个人偏好。' }}</p>
    <RouterLink class="button primary" to="/student">返回选寝首页</RouterLink>
  </section>
  <QuestionnaireContent v-else />
</template>

<style scoped>
.preference-closed-card{display:grid;justify-items:start;gap:12px;max-width:760px;margin:0 auto;padding:28px;border-radius:22px}.preference-closed-card h2,.preference-closed-card p{margin:0}.preference-closed-card p{color:var(--muted);line-height:1.7}
</style>
