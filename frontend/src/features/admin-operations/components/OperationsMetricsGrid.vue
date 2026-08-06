<script setup lang="ts">
import { computed } from 'vue'
import type { DataObject } from '../../../api/types'

const props = defineProps<{ overview: DataObject }>()
const bedUtilization = computed(() => (props.overview.bedUtilization ?? {}) as DataObject)
function number(value: unknown) { return Number(value ?? 0).toLocaleString() }
</script>

<template>
  <section class="metric-grid">
    <article class="panel metric-card"><span>启用床位利用率</span><strong>{{ bedUtilization.rate ?? 0 }}%</strong><small>{{ number(bedUtilization.occupiedBeds) }} / {{ number(bedUtilization.enabledBeds) }} 张床</small></article>
    <article class="panel metric-card"><span>未选学生</span><strong>{{ number(overview.unselectedStudents) }}</strong><small>活动或已关闭批次中仍未入住</small></article>
    <article class="panel metric-card"><span>人工调整</span><strong>{{ number(overview.manualAdjustments) }}</strong><small>换床与换寝历史事件</small></article>
    <article class="panel metric-card"><span>待审换寝</span><strong>{{ number(overview.pendingRoomChanges) }}</strong><small>需要管理员处理</small></article>
    <article class="panel metric-card"><span>床位待确认</span><strong>{{ number(overview.unknownBedResidents) }}</strong><small>已住寝室但未确认具体床位</small></article>
    <article class="panel metric-card"><span>活动批次</span><strong>{{ number(overview.activeBatches) }}</strong><small>已发布、开放或暂停</small></article>
  </section>
</template>

<style scoped>
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.metric-card{display:grid;gap:5px}.metric-card span{color:var(--muted)}.metric-card strong{font-size:28px}.metric-card small{color:var(--muted)}@media(max-width:1000px){.metric-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:640px){.metric-grid{grid-template-columns:1fr}}
</style>
