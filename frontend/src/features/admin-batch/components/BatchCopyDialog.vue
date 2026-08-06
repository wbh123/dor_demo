<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AppModal from '../../../components/modal/AppModal.vue'

export interface BatchCopyForm {
  batchCode: string
  batchName: string
  startAt: string
  endAt: string
  reason: string
}

const props = defineProps<{
  open: boolean
  copySource: DataObject | null
  copyForm: BatchCopyForm
  busy?: boolean
}>()

const emit = defineEmits<{
  'update:copy-form': [value: BatchCopyForm]
  submit: []
  close: []
}>()

function update<K extends keyof BatchCopyForm>(key: K, value: BatchCopyForm[K]) {
  emit('update:copy-form', { ...props.copyForm, [key]: value })
}
</script>

<template>
  <AppModal
    :open="open"
    :title="`复制“${copySource?.batch_name ?? ''}”`"
    description="自动保留选择模式、类别隔离、规则模板和宿舍范围。"
    size="wide"
    :busy="busy"
    :prevent-close="busy"
    @close="emit('close')"
  >
    <form class="form-grid two-column" @submit.prevent="emit('submit')">
      <label><span>新批次编号</span><input :value="copyForm.batchCode" class="input" required @input="update('batchCode', ($event.target as HTMLInputElement).value.trim())" /></label>
      <label><span>新批次名称</span><input :value="copyForm.batchName" class="input" required @input="update('batchName', ($event.target as HTMLInputElement).value.trim())" /></label>
      <label><span>开始时间</span><input :value="copyForm.startAt" class="input" type="datetime-local" required @input="update('startAt', ($event.target as HTMLInputElement).value)" /><small>24小时制</small></label>
      <label><span>结束时间</span><input :value="copyForm.endAt" class="input" type="datetime-local" required @input="update('endAt', ($event.target as HTMLInputElement).value)" /><small>24小时制</small></label>
      <label class="span-2"><span>复制原因</span><textarea :value="copyForm.reason" class="input" required rows="3" @input="update('reason', ($event.target as HTMLTextAreaElement).value.trim())" /></label>
    </form>
    <template #footer>
      <button class="button ghost" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button primary" type="button" :disabled="busy" @click="emit('submit')">{{ busy ? '复制中…' : '创建草稿副本' }}</button>
    </template>
  </AppModal>
</template>
