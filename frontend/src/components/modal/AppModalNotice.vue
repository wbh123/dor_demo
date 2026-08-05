<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'neutral' | 'warning' | 'danger' }>(), { tone: 'neutral' })
</script>
<template><aside class="app-modal-notice" :class="`tone-${tone}`"><slot /></aside></template>
<style scoped>.app-modal-notice{padding:12px 14px;border-radius:13px;background:#f1f5f9;color:#475569;line-height:1.6}.tone-warning{background:#fff8e7;color:#8a4b08}.tone-danger{background:#fff1f2;color:#9b2730}</style>
