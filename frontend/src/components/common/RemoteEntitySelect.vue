<script setup lang="ts">
import { computed, ref, watch } from 'vue'

export interface EntityOption {
  value: string | number
  label: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  modelValue: string | number | Array<string | number> | null
  options: EntityOption[]
  placeholder?: string
  searchPlaceholder?: string
  loading?: boolean
  disabled?: boolean
  multiple?: boolean
  emptyText?: string
}>(), {
  placeholder: '请选择',
  searchPlaceholder: '输入关键词搜索',
  loading: false,
  disabled: false,
  multiple: false,
  emptyText: '暂无匹配项',
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | Array<string | number> | null]
  'remote-search': [keyword: string]
}>()

const keyword = ref('')
const selectedValues = computed(() => new Set(
  Array.isArray(props.modelValue)
    ? props.modelValue.map(String)
    : props.modelValue === null || props.modelValue === '' ? [] : [String(props.modelValue)],
))

watch(keyword, (value) => emit('remote-search', value.trim()))

function updateSingle(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (!value) {
    emit('update:modelValue', null)
    return
  }
  const option = props.options.find(item => String(item.value) === value)
  emit('update:modelValue', option?.value ?? value)
}

function toggle(option: EntityOption) {
  if (!props.multiple || option.disabled || props.disabled) return
  const current = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  const index = current.findIndex(item => String(item) === String(option.value))
  if (index >= 0) current.splice(index, 1)
  else current.push(option.value)
  emit('update:modelValue', current)
}
</script>

<template>
  <div class="remote-entity-select" :class="{disabled}">
    <input
      v-model="keyword"
      class="input remote-search"
      type="search"
      :disabled="disabled"
      :placeholder="searchPlaceholder"
      autocomplete="off"
    />

    <template v-if="multiple">
      <div class="remote-option-list" role="listbox" aria-multiselectable="true">
        <button
          v-for="option in options"
          :key="String(option.value)"
          type="button"
          class="remote-option"
          :class="{selected:selectedValues.has(String(option.value))}"
          :disabled="disabled || option.disabled"
          @click="toggle(option)"
        >
          <span><strong>{{ option.label }}</strong><small v-if="option.description">{{ option.description }}</small></span>
          <span class="option-check">{{ selectedValues.has(String(option.value)) ? '✓' : '+' }}</span>
        </button>
        <p v-if="loading" class="remote-empty">正在搜索…</p>
        <p v-else-if="!options.length" class="remote-empty">{{ emptyText }}</p>
      </div>
    </template>

    <select v-else class="input" :disabled="disabled || loading" :value="modelValue == null ? '' : String(modelValue)" @change="updateSingle">
      <option value="">{{ loading ? '正在加载…' : placeholder }}</option>
      <option v-for="option in options" :key="String(option.value)" :value="String(option.value)" :disabled="option.disabled">
        {{ option.label }}{{ option.description ? ` · ${option.description}` : '' }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.remote-entity-select{display:grid;gap:8px}.remote-option-list{display:grid;gap:6px;max-height:240px;overflow:auto;padding:4px}.remote-option{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;border:1px solid var(--border);border-radius:10px;padding:9px 11px;background:var(--surface);color:inherit;text-align:left;cursor:pointer}.remote-option span:first-child{display:grid;gap:2px;min-width:0}.remote-option small{overflow:hidden;color:var(--text-muted);text-overflow:ellipsis;white-space:nowrap}.remote-option.selected{border-color:var(--primary);background:color-mix(in srgb,var(--primary) 8%,var(--surface))}.option-check{font-weight:800;color:var(--primary)}.remote-empty{margin:0;padding:10px;color:var(--text-muted);text-align:center}.disabled{opacity:.68}
</style>
