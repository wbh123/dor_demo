<script setup lang="ts">const props=defineProps<{ value: number; label?: string }>();const normalized=Math.min(100,Math.max(0,props.value))</script>
<template><div class="app-modal-progress"><div><span>{{ label || '处理进度' }}</span><strong>{{ normalized }}%</strong></div><progress :value="normalized" max="100" /></div></template>
<style scoped>.app-modal-progress{display:grid;gap:8px}.app-modal-progress>div{display:flex;justify-content:space-between;gap:12px}progress{width:100%;height:10px}</style>
