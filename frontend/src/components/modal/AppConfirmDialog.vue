<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppModal from './AppModal.vue'

export type ConfirmDialogVariant = 'default' | 'warning' | 'danger'
export interface ConfirmDialogPayload {
  reason: string
  confirmationWord: string
}

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description?: string
  variant?: ConfirmDialogVariant
  showSymbol?: boolean
  confirmText?: string
  cancelText?: string
  confirmationWord?: string
  confirmationLabel?: string
  requireReason?: boolean
  reasonLabel?: string
  reasonPlaceholder?: string
  busy?: boolean
  error?: string
  action?: (payload: ConfirmDialogPayload) => Promise<void> | void
}>(), {
  description: '',
  variant: 'default',
  showSymbol: true,
  confirmText: '确认',
  cancelText: '取消',
  confirmationWord: '',
  confirmationLabel: '请输入确认词',
  requireReason: false,
  reasonLabel: '操作原因',
  reasonPlaceholder: '请说明执行该操作的原因',
  busy: false,
  error: '',
  action: undefined,
})

const emit = defineEmits<{
  close: []
  confirm: [payload: ConfirmDialogPayload]
  confirmed: [payload: ConfirmDialogPayload]
}>()

const reason = ref('')
const typedConfirmation = ref('')
const internalBusy = ref(false)
const internalError = ref('')
const submitting = computed(() => props.busy || internalBusy.value)
const displayedError = computed(() => props.error || internalError.value)
const displaySymbol = computed(() => props.showSymbol && !props.title.endsWith('已完成发布准备'))
const confirmationMatches = computed(() => !props.confirmationWord
  || typedConfirmation.value.trim() === props.confirmationWord)
const reasonReady = computed(() => !props.requireReason || reason.value.trim().length >= 2)
const canConfirm = computed(() => confirmationMatches.value && reasonReady.value && !submitting.value)
const confirmButtonClass = computed(() => props.variant === 'danger' ? 'danger' : 'primary')

watch(() => props.open, (open) => {
  if (!open) return
  reason.value = ''
  typedConfirmation.value = ''
  internalError.value = ''
})

async function submit() {
  if (!canConfirm.value) return
  const payload = {
    reason: reason.value.trim(),
    confirmationWord: typedConfirmation.value.trim(),
  }
  internalError.value = ''
  emit('confirm', payload)
  if (!props.action) return
  internalBusy.value = true
  try {
    await props.action(payload)
    emit('confirmed', payload)
    emit('close')
  } catch (cause) {
    internalError.value = cause instanceof Error ? cause.message : '操作失败，请稍后重试。'
  } finally {
    internalBusy.value = false
  }
}
</script>

<template>
  <AppModal
    :open="open"
    :title="title"
    :description="description"
    :busy="submitting"
    :prevent-close="submitting"
    size="compact"
    @close="emit('close')"
  >
    <div class="confirm-dialog-content" :class="`confirm-dialog--${variant}`">
      <div v-if="displaySymbol" class="confirm-dialog-symbol" aria-hidden="true">
        {{ variant === 'danger' ? '!' : variant === 'warning' ? '△' : '✓' }}
      </div>

      <label v-if="confirmationWord" class="form-stack">
        <span>{{ confirmationLabel }}</span>
        <input
          v-model="typedConfirmation"
          class="input"
          data-modal-autofocus
          :placeholder="`请输入：${confirmationWord}`"
          autocomplete="off"
        />
        <small>必须完整输入“{{ confirmationWord }}”后才能继续。</small>
      </label>

      <label v-if="requireReason" class="form-stack">
        <span>{{ reasonLabel }}</span>
        <textarea
          v-model="reason"
          class="input"
          rows="4"
          maxlength="500"
          :placeholder="reasonPlaceholder"
          :data-modal-autofocus="!confirmationWord || undefined"
        />
        <small>至少填写2个字符，原因将进入审计记录。</small>
      </label>

      <p v-if="displayedError" class="alert error" role="alert">{{ displayedError }}</p>
      <slot />
    </div>

    <template #footer>
      <button class="button ghost" type="button" :disabled="submitting" @click="emit('close')">
        {{ cancelText }}
      </button>
      <button class="button" :class="confirmButtonClass" type="button" :disabled="!canConfirm" @click="submit">
        {{ submitting ? '正在处理…' : confirmText }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.confirm-dialog-content{display:grid;gap:18px}.confirm-dialog-symbol{display:grid;place-items:center;width:48px;height:48px;border-radius:16px;background:#eaf2ff;color:#2057a4;font-size:24px;font-weight:900}.confirm-dialog--warning .confirm-dialog-symbol{background:#fff4d6;color:#9a5a00}.confirm-dialog--danger .confirm-dialog-symbol{background:#ffebed;color:#a92f3a}.form-stack{display:grid;gap:8px}.form-stack>span{font-weight:800}.form-stack small{color:var(--muted,#64748b)}
</style>
