<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AppModal from '../../../components/modal/AppModal.vue'

const props = defineProps<{
  open: boolean
  preflightBatch: DataObject | null
  roomPreflight: DataObject | null
  preflightRooms: DataObject[]
  preflightBlockers: DataObject[]
  preflightMissingSteps: string[]
  runningPreflight: boolean
  issueText: (room: DataObject) => string
}>()

const emit = defineEmits<{
  close: []
  'reopen-scope': []
}>()
</script>

<template>
  <AppModal
    :open="open"
    :title="`${preflightBatch?.batch_name ?? '批次'} · 发布预检`"
    :description="`可用容量${roomPreflight?.availableCapacity ?? 0}，涉及${roomPreflight?.roomCount ?? 0}间宿舍。`"
    size="wide"
    :busy="runningPreflight"
    :prevent-close="runningPreflight"
    @close="emit('close')"
  >
    <div class="preflight-summary" :class="{ pass: roomPreflight?.publishable }">
      <strong>{{ roomPreflight?.publishable ? '检查通过，可以发布' : preflightMissingSteps.length ? '发布准备尚未完成' : `存在${preflightBlockers.length}间阻断宿舍` }}</strong>
      <span>{{ roomPreflight?.publishable ? '参与范围、宿舍范围和床位可用性已满足发布条件。' : '已保存的范围不会丢失，请按下方提示补齐后再次执行。' }}</span>
    </div>
    <div v-if="preflightMissingSteps.length" class="readiness-grid">
      <article><strong>仍需完成</strong><ul><li v-for="step in preflightMissingSteps" :key="step">{{ step }}</li></ul></article>
      <article><strong>已经完成</strong><ul><li v-for="step in (roomPreflight?.completedSteps ?? [])" :key="String(step)">{{ step }}</li></ul></article>
    </div>
    <div class="preflight-room-grid">
      <article v-for="room in preflightRooms" :key="String(room.id)" :class="{ blocker: (room.issues ?? []).length > 0 }">
        <strong>{{ room.building_name }} {{ room.room_number }}</strong>
        <span>在住{{ room.activeResidents }} · 剩余{{ room.remainingCapacity }}</span>
        <small v-if="(room.issues ?? []).length">{{ props.issueText(room) }}</small>
        <small v-else>符合发布条件</small>
      </article>
    </div>
    <template #footer>
      <button class="button ghost" type="button" :disabled="runningPreflight" @click="emit('close')">关闭</button>
      <button
        v-if="preflightBatch?.batch_status === 'DRAFT' && !roomPreflight?.publishable"
        class="button primary"
        type="button"
        @click="emit('reopen-scope')"
      >在参与范围中补齐</button>
    </template>
  </AppModal>
</template>

<style scoped>
.preflight-summary{display:grid;gap:5px;padding:15px;border-radius:13px;background:#fef2f2;color:#991b1b}.preflight-summary.pass{background:#f0fdf4;color:#166534}.readiness-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:14px}.readiness-grid article{padding:14px;border:1px solid var(--border);border-radius:12px;background:var(--surface-soft)}.readiness-grid ul{margin:8px 0 0;padding-left:20px;color:var(--text-muted)}.preflight-room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px;margin-top:14px}.preflight-room-grid article{display:grid;gap:5px;padding:14px;border:1px solid var(--border);border-radius:12px}.preflight-room-grid article.blocker{border-color:#fecaca;background:#fff7f7}.preflight-room-grid span,.preflight-room-grid small{color:var(--text-muted)}@media(max-width:720px){.readiness-grid{grid-template-columns:1fr}}
</style>
