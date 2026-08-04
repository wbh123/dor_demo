<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

const props = withDefaults(defineProps<{
  message: string
  type?: 'success' | 'warning' | 'info'
  duration?: number
}>(), {
  type: 'success',
  duration: 3000,
})
const emit = defineEmits<{ close: [] }>()
let timer: number | undefined

function close() {
  if (timer) window.clearTimeout(timer)
  timer = undefined
  emit('close')
}

watch(() => props.message, (message) => {
  if (timer) window.clearTimeout(timer)
  timer = undefined
  if (message) timer = window.setTimeout(close, props.duration)
}, { immediate: true })

onBeforeUnmount(() => { if (timer) window.clearTimeout(timer) })
</script>

<template>
  <Teleport to="body">
    <Transition name="notice">
      <aside v-if="message" class="transient-notice" :class="type" role="status" aria-live="polite">
        <span class="notice-mark">{{ type === 'success' ? '✓' : type === 'warning' ? '!' : 'i' }}</span>
        <p>{{ message }}</p>
        <button type="button" class="notice-close" aria-label="关闭提示" @click="close">×</button>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.transient-notice{position:fixed;z-index:2200;top:22px;right:22px;display:grid;grid-template-columns:auto minmax(180px,1fr) auto;align-items:start;gap:11px;width:min(440px,calc(100vw - 32px));padding:14px 15px;border:1px solid #bde5d5;border-radius:15px;background:#f2fcf7;color:#145c43;box-shadow:0 20px 55px rgba(15,23,42,.2)}.transient-notice.warning{border-color:#f3d29b;background:#fff9ed;color:#8a4b08}.transient-notice.info{border-color:#bfd8fa;background:#f2f7ff;color:#24568f}.notice-mark{display:grid;place-items:center;width:25px;height:25px;border-radius:50%;background:currentColor;color:white;font-weight:800}.transient-notice p{margin:2px 0 0;line-height:1.55}.notice-close{width:28px;height:28px;border:0;border-radius:8px;background:transparent;color:currentColor;font-size:20px;line-height:1;cursor:pointer}.notice-close:hover{background:rgba(15,23,42,.08)}.notice-enter-active,.notice-leave-active{transition:.2s ease}.notice-enter-from,.notice-leave-to{transform:translateY(-10px);opacity:0}@media(max-width:620px){.transient-notice{top:12px;right:12px}}
</style>
