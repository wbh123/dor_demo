<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  page: number
  pageSize: number
  total: number
  pageSizes?: number[]
}>(), {
  pageSizes: () => [10, 20, 30, 50],
})

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
  change: []
}>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const start = computed(() => props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1)
const end = computed(() => Math.min(props.total, props.page * props.pageSize))

function changePage(value: number) {
  const next = Math.min(Math.max(1, value), pageCount.value)
  if (next === props.page) return
  emit('update:page', next)
  emit('change')
}

function changePageSize(event: Event) {
  emit('update:pageSize', Number((event.target as HTMLSelectElement).value))
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <nav class="pagination-bar" aria-label="分页">
    <span class="pagination-summary">第 {{ start }}—{{ end }} 条，共 {{ total }} 条</span>
    <label class="pagination-size">
      <span>每页</span>
      <select class="input" :value="pageSize" @change="changePageSize">
        <option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条</option>
      </select>
    </label>
    <div class="pagination-actions">
      <button class="button ghost small" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ pageCount }}</span>
      <button class="button ghost small" type="button" :disabled="page >= pageCount" @click="changePage(page + 1)">下一页</button>
    </div>
  </nav>
</template>

<style scoped>
.pagination-bar{display:flex;align-items:center;justify-content:flex-end;gap:16px;flex-wrap:wrap;padding-top:14px}.pagination-summary{margin-right:auto;color:var(--muted);font-size:13px}.pagination-size{display:flex;align-items:center;gap:7px;color:var(--muted);font-size:13px}.pagination-size .input{width:96px;min-height:36px;padding-block:5px}.pagination-actions{display:flex;align-items:center;gap:9px}.pagination-actions>span{min-width:58px;text-align:center;color:var(--muted);font-size:13px}@media(max-width:620px){.pagination-bar{justify-content:space-between}.pagination-summary{width:100%;margin:0}.pagination-size{order:2}.pagination-actions{order:3}}
</style>
