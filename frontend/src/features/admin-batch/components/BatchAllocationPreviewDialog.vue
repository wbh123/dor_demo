<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AppModal from '../../../components/modal/AppModal.vue'

const props = defineProps<{
  open: boolean
  allocationPreview: DataObject | null
  allocationBatchId: number | null
  allocationSummary: DataObject
  unassignedStudents: DataObject[]
  busy: boolean
}>()

const emit = defineEmits<{
  close: []
  commit: []
}>()

function close() {
  if (!props.busy) emit('close')
}

function commit() {
  if (!props.busy) emit('commit')
}
</script>

<template>
  <AppModal
    :open="open"
    :busy="busy"
    title="统一分配预演"
    description="确认预演结果后再执行正式统一分配。任务量较大时请保持当前页面打开。"
    size="wide"
    busy-text="正在执行统一分配，请勿重复操作…"
    @close="close"
  >
    <div class="allocation-stats">
      <article><span>学生</span><strong>{{ allocationSummary.studentCount ?? 0 }}</strong></article>
      <article><span>预计成功</span><strong>{{ allocationSummary.assignedCount ?? 0 }}</strong></article>
      <article><span>未分配</span><strong>{{ allocationSummary.unassignedCount ?? 0 }}</strong></article>
    </div>
    <p v-if="busy" class="alert info">正在写入床位分配结果和审计记录，完成前请勿关闭页面或重复点击。</p>
    <div v-if="unassignedStudents.length" class="table-wrap">
      <table>
        <thead><tr><th>学号</th><th>姓名</th><th>原因</th></tr></thead>
        <tbody>
          <tr v-for="student in unassignedStudents" :key="String(student.studentId)">
            <td>{{ student.studentNumber }}</td>
            <td>{{ student.studentName }}</td>
            <td>{{ student.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer>
      <button class="button ghost" type="button" :disabled="busy" @click="close">关闭</button>
      <button v-if="allocationBatchId" class="button primary" type="button" :disabled="busy" @click="commit">{{ busy ? '正在执行统一分配…' : '确认执行统一分配' }}</button>
    </template>
  </AppModal>
</template>

<style scoped>
.allocation-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:14px 0}.allocation-stats article{padding:14px;background:var(--surface-soft);border-radius:12px}.allocation-stats strong{display:block;font-size:24px}@media(max-width:720px){.allocation-stats{grid-template-columns:1fr}}
</style>
