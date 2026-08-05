<script setup lang="ts">
withDefaults(defineProps<{ type?: 'info' | 'success' | 'warning' | 'error'; message: string }>(), { type: 'info' })
</script>
<template><p class="app-modal-status" :class="`is-${type}`" role="status">{{ message }}</p></template>
<style scoped>.app-modal-status{margin:0;padding:11px 13px;border-radius:12px;background:#eff6ff;color:#24568f}.is-success{background:#edf9f2;color:#277149}.is-warning{background:#fff8e7;color:#8a4b08}.is-error{background:#fff1f2;color:#9b2730}</style>
