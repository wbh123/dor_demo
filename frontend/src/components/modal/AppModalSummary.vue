<script setup lang="ts">
defineProps<{ items: Array<{ label: string; value: string | number }> }>()
</script>
<template><dl class="app-modal-summary"><div v-for="item in items" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl></template>
<style scoped>.app-modal-summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:0}.app-modal-summary>div{padding:11px 13px;border-radius:13px;background:var(--soft,#f8fafc)}dt{color:var(--muted,#64748b);font-size:12px}dd{margin:4px 0 0;font-weight:800}</style>
