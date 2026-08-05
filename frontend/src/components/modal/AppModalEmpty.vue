<script setup lang="ts">withDefaults(defineProps<{ title?: string; description?: string }>(), { title: '暂无数据', description: '' })</script>
<template><div class="app-modal-empty"><strong>{{ title }}</strong><p v-if="description">{{ description }}</p><slot /></div></template>
<style scoped>.app-modal-empty{display:grid;place-items:center;gap:8px;min-height:150px;text-align:center;color:var(--muted,#64748b)}.app-modal-empty strong{color:var(--text,#172033)}</style>
