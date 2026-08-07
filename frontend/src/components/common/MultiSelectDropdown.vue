<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EntityOption } from './entitySelectTypes'

const props = withDefaults(defineProps<{
  modelValue: Array<string | number>
  options: EntityOption[]
  placeholder?: string
  disabled?: boolean
  loading?: boolean
}>(), {
  placeholder: '不限',
  disabled: false,
  loading: false,
})
const emit = defineEmits<{ 'update:modelValue': [value: Array<string | number>] }>()
const open = ref(false)
const selected = computed(() => new Set((props.modelValue ?? []).map(String)))
const summary = computed(() => {
  if (!props.modelValue?.length) return props.placeholder
  const labels = props.options.filter(item => selected.value.has(String(item.value))).map(item => item.label)
  return labels.length <= 2 ? labels.join('、') : `已选择 ${labels.length} 项`
})
function toggle(option: EntityOption) {
  if (props.disabled || option.disabled) return
  const next = [...(props.modelValue ?? [])]
  const index = next.findIndex(item => String(item) === String(option.value))
  if (index >= 0) next.splice(index, 1)
  else next.push(option.value)
  emit('update:modelValue', next)
}
function clear() { emit('update:modelValue', []) }
</script>

<template>
  <div class="multi-select-dropdown" :class="{disabled}">
    <button class="input dropdown-trigger" type="button" :disabled="disabled" @click="open=!open"><span>{{ summary }}</span><b>{{ open ? '▴' : '▾' }}</b></button>
    <div v-if="open" class="dropdown-panel">
      <div class="dropdown-tools"><span>{{ modelValue.length ? `已选 ${modelValue.length} 项` : '可多选' }}</span><button v-if="modelValue.length" type="button" @click="clear">清空</button></div>
      <p v-if="loading" class="dropdown-empty">正在加载…</p>
      <p v-else-if="!options.length" class="dropdown-empty">暂无可选项</p>
      <button v-for="option in options" v-else :key="String(option.value)" type="button" class="dropdown-option" :class="{selected:selected.has(String(option.value))}" :disabled="disabled||option.disabled" @click="toggle(option)"><span><strong>{{ option.label }}</strong><small v-if="option.description">{{ option.description }}</small></span><em>{{ selected.has(String(option.value)) ? '✓' : '' }}</em></button>
    </div>
  </div>
</template>

<style scoped>
.multi-select-dropdown{position:relative;min-width:0}.dropdown-trigger{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;text-align:left}.dropdown-trigger span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.dropdown-trigger b{color:var(--text-muted)}.dropdown-panel{position:absolute;z-index:35;top:calc(100% + 6px);left:0;right:0;display:grid;gap:5px;max-height:280px;overflow:auto;padding:8px;border:1px solid var(--border);border-radius:12px;background:var(--surface);box-shadow:0 16px 34px rgba(15,23,42,.14)}.dropdown-tools{display:flex;align-items:center;justify-content:space-between;padding:4px 5px 7px;color:var(--text-muted);font-size:11px}.dropdown-tools button{border:0;background:transparent;color:var(--primary);cursor:pointer}.dropdown-option{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;padding:9px;border:1px solid transparent;border-radius:9px;background:transparent;color:inherit;text-align:left;cursor:pointer}.dropdown-option:hover,.dropdown-option.selected{border-color:var(--border);background:var(--surface-soft)}.dropdown-option>span{display:grid;gap:2px;min-width:0}.dropdown-option small{overflow:hidden;color:var(--text-muted);text-overflow:ellipsis;white-space:nowrap}.dropdown-option em{min-width:18px;color:var(--primary);font-style:normal;font-weight:800}.dropdown-empty{margin:0;padding:12px;color:var(--text-muted);text-align:center}.disabled{opacity:.65}
</style>
