import { computed, ref, type Ref } from 'vue'

export type SortDirection = '' | 'asc' | 'desc'

function normalizedValue(value: unknown): string | number | null {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'boolean') return value ? 1 : 0
  const numeric = Number(value)
  if (typeof value !== 'string' || value.trim() === '') return String(value)
  if (Number.isFinite(numeric) && /^-?\d+(\.\d+)?$/.test(value.trim())) return numeric
  return value.trim().toLocaleLowerCase('zh-CN')
}

export function compareTableValues(left: unknown, right: unknown): number {
  const a = normalizedValue(left)
  const b = normalizedValue(right)
  if (a === null && b === null) return 0
  if (a === null) return 1
  if (b === null) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  return String(a).localeCompare(String(b), 'zh-CN', { numeric: true, sensitivity: 'base' })
}

export function useTableSort<T extends Record<string, unknown>>(
  source: Ref<T[]>,
  initialField = '',
  initialDirection: SortDirection = '',
) {
  const sortField = ref(initialField)
  const sortDirection = ref<SortDirection>(initialDirection)

  const sortedRows = computed(() => {
    if (!sortField.value || !sortDirection.value) return [...source.value]
    const direction = sortDirection.value === 'asc' ? 1 : -1
    const field = sortField.value
    return source.value
      .map((row, index) => ({ row, index }))
      .sort((left, right) => {
        const result = compareTableValues(left.row[field], right.row[field])
        return result === 0 ? left.index - right.index : result * direction
      })
      .map(({ row }) => row)
  })

  function setSort(field: string, direction: SortDirection) {
    sortField.value = direction ? field : ''
    sortDirection.value = direction
  }

  function resetSort() {
    sortField.value = ''
    sortDirection.value = ''
  }

  return { sortField, sortDirection, sortedRows, setSort, resetSort }
}
