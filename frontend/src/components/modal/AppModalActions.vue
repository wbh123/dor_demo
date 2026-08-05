<script setup lang="ts">
withDefaults(defineProps<{ wrap?: boolean }>(), { wrap: true })
</script>

<template><div class="app-modal-actions" :class="{ wrap }"><slot /></div></template>

<style scoped>
.app-modal-actions{display:flex;align-items:center;justify-content:flex-end;gap:10px}.app-modal-actions.wrap{flex-wrap:wrap}@media(max-width:640px){.app-modal-actions{width:100%}.app-modal-actions :deep(.button){flex:1 1 140px}}
</style>
