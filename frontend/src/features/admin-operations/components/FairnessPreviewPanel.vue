<script setup lang="ts">
import { computed } from 'vue'
import type { DataObject } from '../../../api/types'

const props = defineProps<{
  batches: DataObject[]
  batchId: number
  randomSeed: number
  preview: DataObject | null
  loading?: boolean
}>()
const emit = defineEmits<{
  'update:batchId': [value: number]
  'update:randomSeed': [value: number]
  preview: []
}>()
const fairness = computed(() => (props.preview?.fairness ?? {}) as DataObject)
function batchLabel(batch: DataObject) {
  return `${batch.batch_name ?? batch.batch_code ?? `批次${batch.id}`} · ${batch.batch_status ?? '未知状态'}`
}
</script>

<template>
  <section class="panel">
    <div class="section-head"><div><span class="eyebrow">统一分配评估</span><h3>公平性预演</h3><p>只读取候选结果并计算得分离散程度，不写入正式分配。</p></div></div>
    <form class="preview-form" @submit.prevent="emit('preview')">
      <label><span>选择批次</span><select class="input" required :value="batchId" @change="emit('update:batchId', Number(($event.target as HTMLSelectElement).value))"><option :value="0" disabled>请选择批次</option><option v-for="batch in batches" :key="String(batch.id)" :value="Number(batch.id)">{{ batchLabel(batch) }}</option></select></label>
      <label><span>随机种子</span><input class="input" type="number" required :value="randomSeed" @input="emit('update:randomSeed', Number(($event.target as HTMLInputElement).value))" /></label>
      <button class="button primary" :disabled="loading || !batchId">{{ loading ? '正在预演…' : '运行预演' }}</button>
    </form>
    <div v-if="preview" class="preview-result">
      <article><span>平均得分</span><strong>{{ fairness.averageScore }}</strong></article><article><span>最低得分</span><strong>{{ fairness.minimumScore }}</strong></article><article><span>标准差</span><strong>{{ fairness.standardDeviation }}</strong></article><article><span>公平性指数</span><strong>{{ fairness.fairness }}</strong></article><article><span>已分配</span><strong>{{ fairness.assignedCount }}</strong></article><article><span>未分配</span><strong>{{ fairness.unassignedCount }}</strong></article><p>{{ preview.notice }}</p>
    </div>
  </section>
</template>

<style scoped>
.preview-form{display:grid;grid-template-columns:minmax(260px,1.5fr) 1fr auto;gap:10px;align-items:end}.preview-form label{display:grid;gap:6px}.preview-result{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-top:16px}.preview-result article{padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.preview-result span{display:block;color:var(--muted);font-size:12px}.preview-result strong{display:block;margin-top:5px}.preview-result p{grid-column:1/-1;color:var(--muted)}@media(max-width:1000px){.preview-result{grid-template-columns:repeat(3,1fr)}}@media(max-width:640px){.preview-result,.preview-form{grid-template-columns:1fr}}
</style>
