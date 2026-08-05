<script setup lang="ts">
withDefaults(defineProps<{ columns?: 1 | 2 | 3 }>(), { columns: 2 })
</script>
<template><div class="app-modal-grid" :class="`columns-${columns}`"><slot /></div></template>
<style scoped>.app-modal-grid{display:grid;gap:14px}.columns-1{grid-template-columns:1fr}.columns-2{grid-template-columns:repeat(2,minmax(0,1fr))}.columns-3{grid-template-columns:repeat(3,minmax(0,1fr))}@media(max-width:720px){.columns-2,.columns-3{grid-template-columns:1fr}}</style>
