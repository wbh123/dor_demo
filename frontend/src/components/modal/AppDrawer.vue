<script setup lang="ts">
import AppModal from './AppModal.vue'

withDefaults(defineProps<{
  open: boolean
  title?: string
  description?: string
  side?: 'left' | 'right'
  busy?: boolean
  preventClose?: boolean
}>(), {
  title: '',
  description: '',
  side: 'right',
  busy: false,
  preventClose: false,
})

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    :description="description"
    :busy="busy"
    :prevent-close="preventClose"
    size="fullscreen"
    @close="emit('close')"
  >
    <div class="app-drawer-content" :class="`app-drawer--${side}`"><slot /></div>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </AppModal>
</template>

<style scoped>
.app-drawer-content{min-height:100%}
</style>
