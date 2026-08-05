<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  focusableElements,
  isTopModal,
  modalDepth,
  registerModal,
  unregisterModal,
  updateModalRoot,
} from './modalStack'

export type AppModalSize = 'default' | 'wide' | 'large' | 'fullscreen'
export type AppModalRole = 'dialog' | 'alertdialog'

let modalSequence = 0
const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  description?: string
  size?: AppModalSize
  role?: AppModalRole
  maxHeight?: string
  closeOnBackdrop?: boolean
  closeOnEscape?: boolean
  busy?: boolean
  preventClose?: boolean
  labelledBy?: string
  describedBy?: string
}>(), {
  title: '',
  description: '',
  size: 'default',
  role: 'dialog',
  maxHeight: 'min(88dvh, 900px)',
  closeOnBackdrop: true,
  closeOnEscape: true,
  busy: false,
  preventClose: false,
  labelledBy: '',
  describedBy: '',
})

const emit = defineEmits<{
  close: []
  'after-open': []
  'after-close': []
}>()

const root = ref<HTMLElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const id = `app-modal-${++modalSequence}`
const titleId = `${id}-title`
const descriptionId = `${id}-description`
const closable = computed(() => !props.busy && !props.preventClose)
const sizeClass = computed(() => `app-modal--${props.size}`)
const layerStyle = computed(() => ({ zIndex: String(1500 + modalDepth(id) * 20) }))
const surfaceStyle = computed(() => ({ '--app-modal-max-height': props.maxHeight }))

watch(() => props.open, async (open) => {
  if (open) {
    registerModal(id, root.value)
    await nextTick()
    updateModalRoot(id, root.value)
    focusInitialElement()
    emit('after-open')
  } else {
    await unregisterModal(id)
    emit('after-close')
  }
}, { immediate: true })

watch(root, (element) => updateModalRoot(id, element))
onBeforeUnmount(() => { void unregisterModal(id) })

function requestClose() {
  if (!closable.value || !isTopModal(id)) return
  emit('close')
}

function onBackdrop(event: MouseEvent) {
  if (event.target !== event.currentTarget || !props.closeOnBackdrop) return
  requestClose()
}

function onKeydown(event: KeyboardEvent) {
  if (!isTopModal(id)) return
  if (event.key === 'Escape') {
    if (props.closeOnEscape) {
      event.preventDefault()
      event.stopPropagation()
      requestClose()
    }
    return
  }
  if (event.key !== 'Tab') return
  const focusable = focusableElements(panel.value)
  if (!focusable.length) {
    event.preventDefault()
    panel.value?.focus()
    return
  }
  const first = focusable[0]
  const last = focusable.at(-1)!
  if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function focusInitialElement() {
  const preferred = panel.value?.querySelector<HTMLElement>('[autofocus],[data-modal-autofocus]')
  const first = preferred ?? focusableElements(panel.value)[0] ?? panel.value
  first?.focus({ preventScroll: true })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="app-modal-fade">
      <div
        v-if="open"
        ref="root"
        class="app-modal-backdrop"
        :style="layerStyle"
        data-app-modal-backdrop
        @mousedown="onBackdrop"
        @keydown="onKeydown"
      >
        <section
          ref="panel"
          class="app-modal-surface"
          :class="sizeClass"
          :style="surfaceStyle"
          :role="role"
          aria-modal="true"
          :aria-labelledby="labelledBy || (title ? titleId : undefined)"
          :aria-describedby="describedBy || (description ? descriptionId : undefined)"
          :aria-busy="busy || undefined"
          tabindex="-1"
          @mousedown.stop
        >
          <header v-if="$slots.header || title || description" class="app-modal-header">
            <slot name="header">
              <div class="app-modal-heading">
                <h2 v-if="title" :id="titleId">{{ title }}</h2>
                <p v-if="description" :id="descriptionId">{{ description }}</p>
              </div>
            </slot>
            <button
              v-if="!preventClose"
              class="app-modal-close"
              type="button"
              :disabled="busy"
              aria-label="关闭弹窗"
              @click="requestClose"
            >×</button>
          </header>

          <div class="app-modal-body" data-app-modal-scroll-region>
            <div v-if="busy" class="app-modal-loading" role="status" aria-live="polite">
              <span class="app-modal-spinner" aria-hidden="true" />
              <span>正在处理，请稍候…</span>
            </div>
            <slot />
          </div>

          <footer v-if="$slots.footer" class="app-modal-footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.app-modal-backdrop{position:fixed;inset:0;display:grid;place-items:center;padding:max(18px,env(safe-area-inset-top)) max(18px,env(safe-area-inset-right)) max(18px,env(safe-area-inset-bottom)) max(18px,env(safe-area-inset-left));background:rgba(15,23,42,.58);backdrop-filter:blur(3px);overflow:hidden}.app-modal-surface{display:flex;flex-direction:column;width:min(680px,100%);max-height:var(--app-modal-max-height);border:1px solid rgba(148,163,184,.32);border-radius:24px;background:var(--panel,#fff);color:var(--text,#172033);box-shadow:0 28px 80px rgba(15,23,42,.28);outline:none;overflow:hidden}.app-modal--wide{width:min(960px,100%)}.app-modal--large{width:min(1240px,100%);max-height:var(--app-modal-max-height)}.app-modal--fullscreen{width:100%;height:100%;max-height:none;border-radius:18px}.app-modal-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:22px 24px 18px;border-bottom:1px solid var(--line,#e5e7eb);background:var(--panel,#fff)}.app-modal-heading{min-width:0}.app-modal-heading h2{margin:0;font-size:clamp(20px,2vw,27px);line-height:1.25}.app-modal-heading p{margin:7px 0 0;color:var(--muted,#64748b);line-height:1.6}.app-modal-close{display:grid;place-items:center;flex:0 0 36px;width:36px;height:36px;border:0;border-radius:12px;background:var(--soft,#f1f5f9);font-size:24px;line-height:1;cursor:pointer}.app-modal-close:hover:not(:disabled){background:#e2e8f0}.app-modal-close:disabled{cursor:not-allowed;opacity:.5}.app-modal-body{position:relative;flex:1;min-height:0;padding:22px 24px;overflow:auto;overscroll-behavior:contain}.app-modal-footer{display:flex;justify-content:flex-end;align-items:center;gap:10px;padding:16px 24px 20px;border-top:1px solid var(--line,#e5e7eb);background:var(--panel,#fff)}.app-modal-loading{position:absolute;z-index:3;inset:0;display:flex;align-items:center;justify-content:center;gap:10px;background:rgba(255,255,255,.78);font-weight:700}.app-modal-spinner{width:22px;height:22px;border:3px solid #dbeafe;border-top-color:#2563eb;border-radius:50%;animation:app-modal-spin .8s linear infinite}.app-modal-fade-enter-active,.app-modal-fade-leave-active{transition:opacity .18s ease}.app-modal-fade-enter-active .app-modal-surface,.app-modal-fade-leave-active .app-modal-surface{transition:transform .18s ease,opacity .18s ease}.app-modal-fade-enter-from,.app-modal-fade-leave-to{opacity:0}.app-modal-fade-enter-from .app-modal-surface,.app-modal-fade-leave-to .app-modal-surface{transform:translateY(10px) scale(.985);opacity:0}@keyframes app-modal-spin{to{transform:rotate(360deg)}}@media(max-width:640px){.app-modal-backdrop{place-items:end center;padding:8px max(8px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(8px,env(safe-area-inset-left))}.app-modal-surface,.app-modal--wide,.app-modal--large{width:100%;max-height:min(var(--app-modal-max-height),calc(100dvh - 16px));border-radius:20px}.app-modal--fullscreen{height:calc(100dvh - 16px)}.app-modal-header{padding:18px 18px 14px}.app-modal-body{padding:18px}.app-modal-footer{padding:14px 18px calc(16px + env(safe-area-inset-bottom));flex-wrap:wrap}.app-modal-footer :deep(.button){flex:1 1 140px}}
</style>
