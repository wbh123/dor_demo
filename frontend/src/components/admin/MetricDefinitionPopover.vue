<script setup lang="ts">
import { ref } from 'vue'
import type { DataObject } from '../../api/types'
defineProps<{ definition: DataObject }>()
const open = ref(false)
</script>

<template>
  <span class="metric-definition-popover">
    <button class="metric-help" type="button" :aria-expanded="open" @click="open = !open">?</button>
    <span v-if="open" class="metric-popover-card" role="tooltip">
      <strong>{{ definition.nameZhCn ?? definition.name_zh_cn }}</strong>
      <span>{{ definition.nameEnUs ?? definition.name_en_us }}</span>
      <dl><div><dt>时间范围</dt><dd>{{ definition.timeRange }}</dd></div><div><dt>数据口径</dt><dd>{{ definition.sourceBasis }}</dd></div><div><dt>更新时间</dt><dd>{{ definition.dataUpdatedAt }}</dd></div><div><dt>口径版本</dt><dd>{{ definition.metricVersion }}</dd></div></dl>
      <small>{{ definition.privacyNote }}</small>
    </span>
  </span>
</template>

<style scoped>
.metric-definition-popover{position:relative;display:inline-flex}.metric-help{display:grid;place-items:center;width:22px;height:22px;border:1px solid var(--border);border-radius:50%;background:var(--surface);color:var(--primary);font-weight:900;cursor:pointer}.metric-popover-card{position:absolute;z-index:20;right:0;top:30px;display:grid;gap:7px;width:min(340px,80vw);padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface);box-shadow:0 16px 42px rgba(15,23,42,.18)}.metric-popover-card>span,.metric-popover-card small{color:var(--text-muted)}.metric-popover-card dl{display:grid;gap:7px;margin:0}.metric-popover-card dl>div{display:grid;grid-template-columns:74px 1fr;gap:8px}.metric-popover-card dt{font-weight:700}.metric-popover-card dd{margin:0;color:var(--text-muted)}
</style>
