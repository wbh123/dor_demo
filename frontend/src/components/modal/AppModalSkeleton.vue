<script setup lang="ts">withDefaults(defineProps<{ rows?: number }>(), { rows: 4 })</script>
<template><div class="app-modal-skeleton" aria-hidden="true"><span v-for="row in rows" :key="row" /></div></template>
<style scoped>.app-modal-skeleton{display:grid;gap:10px}.app-modal-skeleton span{height:38px;border-radius:12px;background:linear-gradient(90deg,#eef2f7,#f8fafc,#eef2f7);background-size:200% 100%;animation:pulse 1.4s ease infinite}@keyframes pulse{to{background-position:-200% 0}}</style>
