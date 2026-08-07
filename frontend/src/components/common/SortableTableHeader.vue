<script setup lang="ts">
export type SortDirection = '' | 'asc' | 'desc'

const props = withDefaults(defineProps<{
  label: string
  field: string
  activeField?: string
  direction?: SortDirection
  title?: string
}>(), {
  activeField: '',
  direction: '',
  title: '点击切换升序、降序和默认顺序',
})

const emit = defineEmits<{
  change: [field: string, direction: SortDirection]
}>()

function nextDirection(): SortDirection {
  if (props.activeField !== props.field || !props.direction) return 'asc'
  if (props.direction === 'asc') return 'desc'
  return ''
}

function toggle() {
  emit('change', props.field, nextDirection())
}
</script>

<template>
  <button
    class="sortable-table-header"
    type="button"
    :title="title"
    :aria-label="`${label}排序`"
    @click="toggle"
  >
    <span>{{ label }}</span>
    <span class="sort-indicator" aria-hidden="true">
      <template v-if="activeField === field && direction === 'asc'">↑</template>
      <template v-else-if="activeField === field && direction === 'desc'">↓</template>
      <template v-else>↕</template>
    </span>
  </button>
</template>

<style scoped>
.sortable-table-header{display:inline-flex;align-items:center;gap:5px;width:100%;border:0;padding:0;background:transparent;color:inherit;font:inherit;font-weight:inherit;text-align:left;cursor:pointer}.sort-indicator{color:var(--text-muted);font-size:12px}.sortable-table-header:hover .sort-indicator{color:var(--primary)}
</style>
