<script setup lang="ts">
withDefaults(defineProps<{
  align?: 'start' | 'between' | 'end'
}>(), { align: 'end' })
</script>

<template>
  <div class="app-modal-footer-content" :class="`align-${align}`"><slot /></div>
</template>

<style scoped>
.app-modal-footer-content{display:flex;align-items:center;gap:10px;width:100%;flex-wrap:wrap}.align-start{justify-content:flex-start}.align-between{justify-content:space-between}.align-end{justify-content:flex-end}@media(max-width:640px){.app-modal-footer-content :deep(.button){flex:1 1 140px}}
</style>
