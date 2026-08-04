<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { phoneCodeOptions } from '../../utils/phoneCodes'

const props = defineProps<{ modelValue: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const root = ref<HTMLElement | null>(null)
const open = ref(false)
const keyword = ref('')
const selectedDialCode = computed(() => props.modelValue || '+86')
const filteredOptions = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return phoneCodeOptions
  return phoneCodeOptions.filter((option) =>
    option.countryName.toLowerCase().includes(query)
      || option.countryCode.toLowerCase().includes(query)
      || option.dialCode.includes(query),
  )
})

watch(() => props.disabled, (disabled) => { if (disabled) open.value = false })

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (!open.value) keyword.value = ''
}

function select(value: string) {
  emit('update:modelValue', value)
  open.value = false
  keyword.value = ''
}

function closeOnOutside(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) {
    open.value = false
    keyword.value = ''
  }
}

function closeOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    open.value = false
    keyword.value = ''
  }
}

onMounted(() => {
  document.addEventListener('mousedown', closeOnOutside)
  document.addEventListener('keydown', closeOnEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeOnOutside)
  document.removeEventListener('keydown', closeOnEscape)
})
</script>

<template>
  <div ref="root" class="dial-code-select" :class="{ open, disabled }">
    <button
      type="button"
      class="input dial-code-trigger"
      :disabled="disabled"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click="toggle"
    >
      <span>{{ selectedDialCode }}</span><span class="dial-code-arrow">⌄</span>
    </button>
    <div v-if="open" class="dial-code-popover">
      <input v-model.trim="keyword" class="input dial-code-search" placeholder="搜索国家、地区或地区码" autofocus />
      <div class="dial-code-options" role="listbox">
        <button
          v-for="option in filteredOptions"
          :key="option.countryCode"
          type="button"
          class="dial-code-option"
          :class="{ selected: option.dialCode === modelValue }"
          @click="select(option.dialCode)"
        >
          <span>{{ option.countryName }}</span><strong>{{ option.dialCode }}</strong>
        </button>
        <p v-if="filteredOptions.length === 0" class="dial-code-empty">没有匹配的国家或地区</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dial-code-select{position:relative;width:94px;min-width:94px;max-width:94px}.dial-code-trigger{display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;white-space:nowrap;text-align:left;background:var(--panel,#fff)}.dial-code-arrow{color:var(--text-muted);font-size:14px}.dial-code-popover{position:absolute;z-index:1400;top:calc(100% + 6px);left:0;width:min(330px,calc(100vw - 32px));padding:9px;border:1px solid var(--border);border-radius:14px;background:var(--panel,#fff);box-shadow:0 18px 45px rgba(15,23,42,.18)}.dial-code-search{height:40px;min-height:40px}.dial-code-options{max-height:280px;overflow:auto;margin-top:7px}.dial-code-option{display:flex;align-items:center;justify-content:space-between;gap:14px;width:100%;padding:9px 10px;border:0;border-radius:9px;background:transparent;color:inherit;text-align:left;cursor:pointer}.dial-code-option:hover,.dial-code-option.selected{background:var(--surface-soft)}.dial-code-option strong{color:var(--primary);white-space:nowrap}.dial-code-empty{margin:8px;padding:10px;color:var(--text-muted);text-align:center}.disabled{opacity:.65}
</style>
