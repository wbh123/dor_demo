<script setup lang="ts">
withDefaults(defineProps<{ padded?: boolean }>(), { padded: false })
</script>

<template><div class="app-modal-body-content" :class="{ padded }"><slot /></div></template>

<style scoped>
.app-modal-body-content{min-width:0}.app-modal-body-content.padded{padding:4px}
</style>
