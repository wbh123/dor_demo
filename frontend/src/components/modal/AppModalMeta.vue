<script setup lang="ts">defineProps<{ label: string; value: string | number }>()</script>
<template><span class="app-modal-meta"><b>{{ label }}</b>{{ value }}</span></template>
<style scoped>.app-modal-meta{display:inline-flex;gap:5px;padding:6px 9px;border-radius:999px;background:var(--soft,#f1f5f9);color:var(--muted,#64748b);font-size:12px}.app-modal-meta b{color:var(--text,#172033)}</style>
