<script setup lang="ts">defineProps<{ title: string; description?: string }>()</script>
<template><div class="app-modal-title"><h3>{{ title }}</h3><p v-if="description">{{ description }}</p></div></template>
<style scoped>.app-modal-title h3{margin:0}.app-modal-title p{margin:5px 0 0;color:var(--muted,#64748b)}</style>
