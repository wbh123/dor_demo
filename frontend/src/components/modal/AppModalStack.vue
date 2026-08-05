<script setup lang="ts">withDefaults(defineProps<{ gap?: 'small' | 'medium' | 'large' }>(), { gap: 'medium' })</script>
<template><div class="app-modal-stack" :class="`gap-${gap}`"><slot /></div></template>
<style scoped>.app-modal-stack{display:grid}.gap-small{gap:8px}.gap-medium{gap:14px}.gap-large{gap:22px}</style>
