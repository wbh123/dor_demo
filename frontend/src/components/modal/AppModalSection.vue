<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  compact?: boolean
}>(), {
  title: '',
  description: '',
  compact: false,
})
</script>

<template>
  <section class="app-modal-section" :class="{ compact }">
    <header v-if="title || description || $slots.header" class="app-modal-section-header">
      <slot name="header">
        <div>
          <h3 v-if="title">{{ title }}</h3>
          <p v-if="description">{{ description }}</p>
        </div>
      </slot>
    </header>
    <div class="app-modal-section-body"><slot /></div>
  </section>
</template>

<style scoped>
.app-modal-section{display:grid;gap:14px}.app-modal-section+.app-modal-section{margin-top:22px;padding-top:22px;border-top:1px solid var(--line,#e5e7eb)}.app-modal-section.compact{gap:9px}.app-modal-section-header h3{margin:0;font-size:17px}.app-modal-section-header p{margin:5px 0 0;color:var(--muted,#64748b);line-height:1.55}.app-modal-section-body{min-width:0}
</style>
