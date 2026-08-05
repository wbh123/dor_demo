<script setup lang="ts">
withDefaults(defineProps<{
  eyebrow?: string
  title: string
  description?: string
}>(), { eyebrow: '', description: '' })
</script>

<template>
  <div class="app-modal-custom-header">
    <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
  </div>
</template>

<style scoped>
.app-modal-custom-header{min-width:0}.app-modal-custom-header h2{margin:4px 0 0;font-size:clamp(20px,2vw,27px)}.app-modal-custom-header p{margin:7px 0 0;color:var(--muted,#64748b);line-height:1.6}
</style>
