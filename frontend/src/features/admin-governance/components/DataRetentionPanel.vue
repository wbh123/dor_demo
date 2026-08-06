<script setup lang="ts">
import type { DataObject } from '../../../api/types'

defineProps<{
  policy: DataObject | null
  statistics: DataObject | null
  simulation: DataObject | null
  busy?: boolean
  error?: string
  message?: string
}>()

const emit = defineEmits<{
  simulate: []
  'confirm-preflight': []
}>()
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">RETENTION</span>
        <h3>数据保留查询</h3>
        <p>Data retention · 本轮仅展示策略、到期统计、模拟清理和清理预检，不执行生产删除、备份或恢复。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div v-if="policy" class="retention-summary">
      <article><span>业务数据保留</span><strong>{{ policy.dataRetentionDays }}天</strong></article>
      <article><span>审计保留</span><strong>{{ policy.auditRetentionDays }}天</strong></article>
      <article><span>执行清理</span><strong>未开放</strong></article>
    </div>

    <div class="button-row">
      <button class="button secondary" type="button" :disabled="busy" @click="emit('simulate')">模拟清理</button>
      <button class="button danger" type="button" :disabled="busy" @click="emit('confirm-preflight')">记录清理预检</button>
    </div>

    <div v-if="statistics" class="json-card">
      <strong>到期数据统计</strong>
      <pre>{{ JSON.stringify(statistics, null, 2) }}</pre>
    </div>
    <div v-if="simulation" class="json-card">
      <strong>受保护数据与模拟结果</strong>
      <pre>{{ JSON.stringify(simulation, null, 2) }}</pre>
    </div>
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.json-card{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.json-card pre{max-height:360px;overflow:auto;margin:0;white-space:pre-wrap;font-size:11px}.retention-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.retention-summary article{padding:16px;border-radius:13px;background:var(--surface-soft)}.retention-summary span,.retention-summary strong{display:block}.retention-summary span{color:var(--text-muted);font-size:12px}.retention-summary strong{margin-top:5px;font-size:22px}@media(max-width:620px){.retention-summary{grid-template-columns:1fr}}
</style>
