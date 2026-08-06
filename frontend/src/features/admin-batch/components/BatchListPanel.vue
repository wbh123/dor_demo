<script setup lang="ts">
import type { DataObject } from '../../../api/types'

const props = defineProps<{
  batches: DataObject[]
  publishFlowBusy: boolean
  nextActions: (status: unknown) => string[]
  modeText: (mode: unknown) => string
  statusText: (status: unknown) => string
  actionText: (action: unknown) => string
}>()

const emit = defineEmits<{
  'open-scope': [batch: DataObject]
  preflight: [batch: DataObject]
  'open-copy': [batch: DataObject]
  'change-status': [batch: DataObject, target: string]
  'preview-allocation': [batch: DataObject]
  download: [batch: DataObject]
}>()
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <div><span class="eyebrow">BATCH LIST</span><h3>批次管理</h3></div>
    </div>
    <div class="batch-list">
      <article v-for="batch in batches" :key="String(batch.id)" class="batch-card">
        <header>
          <div>
            <div class="badge-row">
              <span class="status-chip compact">{{ props.statusText(batch.batch_status) }}</span>
              <span class="status-chip compact mode">{{ props.modeText(batch.selection_mode) }}</span>
              <span v-if="batch.separate_student_categories" class="status-chip compact category">国内/国际隔离</span>
            </div>
            <h3>{{ batch.batch_name }}</h3>
            <p>{{ batch.batch_code }}</p>
          </div>
          <div class="batch-counts"><strong>{{ batch.eligible_count ?? 0 }}</strong><span>已选参与学生</span></div>
        </header>
        <div class="batch-facts">
          <span>寝室结果 {{ batch.room_assigned_count ?? 0 }}</span>
          <span>床位结果 {{ batch.bed_assigned_count ?? batch.assigned_count ?? 0 }}</span>
          <span>活动锁定宿舍 {{ batch.locked_room_count ?? 0 }}</span>
          <span v-if="Number(batch.unconfirmed_bed_resident_count ?? 0) > 0" class="warn">{{ batch.unconfirmed_bed_resident_count }}人待确认床位</span>
        </div>
        <div class="button-row wrap">
          <button v-if="batch.batch_status === 'DRAFT'" class="button secondary small" :disabled="publishFlowBusy" @click="emit('open-scope', batch)">配置参与范围</button>
          <button class="button ghost small" :disabled="publishFlowBusy" @click="emit('preflight', batch)">宿舍预检</button>
          <button class="button ghost small" :disabled="publishFlowBusy" @click="emit('open-copy', batch)">复制配置</button>
          <button
            v-for="target in props.nextActions(batch.batch_status)"
            :key="target"
            class="button small"
            :class="target === 'CANCELLED' ? 'danger' : 'primary'"
            :disabled="publishFlowBusy"
            @click="emit('change-status', batch, target)"
          >{{ props.actionText(target) }}</button>
          <button v-if="['CLOSED', 'ALLOCATING'].includes(String(batch.batch_status))" class="button secondary small" @click="emit('preview-allocation', batch)">统一分配预演</button>
          <button class="button ghost small" @click="emit('download', batch)">导出结果</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.batch-list{display:grid;gap:14px}.batch-card{padding:18px;border:1px solid var(--border);border-radius:16px;background:var(--surface)}.batch-card header{display:flex;justify-content:space-between;gap:16px}.batch-card h3{margin:8px 0 3px}.batch-card p{margin:0;color:var(--text-muted)}.badge-row,.batch-facts{display:flex;gap:8px;flex-wrap:wrap}.status-chip.mode{background:#eff6ff;color:#1d4ed8}.status-chip.category{background:#f5f3ff;color:#6d28d9}.batch-counts{text-align:right}.batch-counts strong{display:block;font-size:26px}.batch-counts span,.batch-facts{color:var(--text-muted);font-size:13px}.batch-facts{margin:14px 0}.batch-facts .warn{color:#b45309;font-weight:700}@media(max-width:720px){.batch-card header{flex-direction:column}.batch-counts{text-align:left}}
</style>
