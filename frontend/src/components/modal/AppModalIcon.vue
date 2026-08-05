<script setup lang="ts">withDefaults(defineProps<{ tone?: 'neutral' | 'warning' | 'danger' }>(), { tone: 'neutral' })</script>
<template><span class="app-modal-icon" :class="`tone-${tone}`"><slot /></span></template>
<style scoped>.app-modal-icon{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;background:#eaf2ff;color:#2057a4;font-weight:900}.tone-warning{background:#fff4d6;color:#9a5a00}.tone-danger{background:#ffebed;color:#a92f3a}</style>
