<script setup lang="ts">
import AppModal from '../modal/AppModal.vue'

withDefaults(defineProps<{
  open: boolean
  title: string
  message: string
  detail?: string
  confirmText?: string
  cancelText?: string
  busy?: boolean
  danger?: boolean
  busyText?: string
}>(), {
  detail: '',
  confirmText: '确认',
  cancelText: '取消',
  busy: false,
  danger: false,
  busyText: '正在处理，请勿重复操作…',
})

const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    size="default"
    :busy="busy"
    :busy-text="busyText"
    @close="!busy && emit('cancel')"
  >
    <div class="action-confirm-content">
      <p>{{ message }}</p>
      <small v-if="detail">{{ detail }}</small>
    </div>
    <template #footer>
      <button class="button ghost" type="button" :disabled="busy" @click="emit('cancel')">{{ cancelText }}</button>
      <button class="button" :class="danger ? 'danger' : 'primary'" type="button" :disabled="busy" @click="emit('confirm')">
        {{ busy ? '正在处理…' : confirmText }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.action-confirm-content{display:grid;gap:8px}.action-confirm-content p{margin:0;line-height:1.7}.action-confirm-content small{color:var(--text-muted);line-height:1.6}
</style>
