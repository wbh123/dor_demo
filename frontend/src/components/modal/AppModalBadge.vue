<script setup lang="ts">withDefaults(defineProps<{ tone?: 'neutral' | 'success' | 'warning' | 'danger' }>(), { tone: 'neutral' })</script>
<template><span class="app-modal-badge" :class="`tone-${tone}`"><slot /></span></template>
<style scoped>.app-modal-badge{display:inline-flex;padding:5px 8px;border-radius:999px;background:#f1f5f9;color:#475569;font-size:12px;font-weight:800}.tone-success{background:#eaf8ef;color:#277149}.tone-warning{background:#fff5dc;color:#8a4b08}.tone-danger{background:#ffebed;color:#9b2730}</style>
