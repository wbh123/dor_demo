<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AppConfirmDialog, { type ConfirmDialogPayload } from '../../../components/modal/AppConfirmDialog.vue'

const props = defineProps<{
  open: boolean
  publishConfirmation: DataObject | null
  publishPreflightSnapshot: DataObject | null
  selectedStudentCount: number
  scopeOpen: boolean
  publishing: boolean
  action: (payload: ConfirmDialogPayload) => Promise<void> | void
  modeText: (mode: unknown) => string
  formatDateTime: (value: unknown) => string
  batchRuleSummary: (batch: DataObject) => string
}>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <AppConfirmDialog
    :open="open"
    :title="`${publishConfirmation?.batch_name ?? '批次'} 已完成发布准备`"
    description="发布后学生可以按开放时间参与选择，参与范围、模式和主要规则不能在活动进行中随意修改。"
    variant="warning"
    confirm-text="确认发布"
    cancel-text="暂不发布"
    :busy="publishing"
    :action="action"
    @close="emit('close')"
  >
    <div class="publish-confirmation-facts">
      <span>参与学生 {{ scopeOpen ? selectedStudentCount : (publishConfirmation?.eligible_count ?? 0) }} 人</span>
      <span>可选宿舍 {{ publishPreflightSnapshot?.roomCount ?? 0 }} 间</span>
      <span>可用床位/容量 {{ publishPreflightSnapshot?.availableCapacity ?? 0 }}</span>
      <span>模式 {{ props.modeText(publishConfirmation?.selection_mode) }}</span>
    </div>
    <dl class="publish-detail-list">
      <div><dt>开放时间</dt><dd>{{ props.formatDateTime(publishConfirmation?.start_at) }} 至 {{ props.formatDateTime(publishConfirmation?.end_at) }}</dd></div>
      <div><dt>主要规则</dt><dd>{{ props.batchRuleSummary(publishConfirmation ?? {}) }}</dd></div>
      <div><dt>发布限制</dt><dd>发布后范围将参与活动锁定；需要调整时应先按批次生命周期暂停或结束活动。</dd></div>
    </dl>
  </AppConfirmDialog>
</template>

<style scoped>
.publish-confirmation-facts{display:flex;gap:8px;flex-wrap:wrap;margin:4px 0 16px}.publish-confirmation-facts span{padding:7px 10px;border-radius:999px;color:#315c9e;background:#edf3ff;font-size:12px;font-weight:700}.publish-detail-list{display:grid;gap:10px;margin:0}.publish-detail-list>div{display:grid;grid-template-columns:90px minmax(0,1fr);gap:12px;padding:10px 0;border-top:1px solid var(--border)}.publish-detail-list dt{font-weight:800}.publish-detail-list dd{margin:0;color:var(--text-muted);line-height:1.6}@media(max-width:720px){.publish-detail-list>div{grid-template-columns:1fr;gap:4px}}
</style>
