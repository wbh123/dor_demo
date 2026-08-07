<script setup lang="ts">
import { onMounted, ref } from 'vue'
import RemoteEntitySelect, { type EntityOption } from '../common/RemoteEntitySelect.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse } from '../../api/types'

export interface RecipientCriteria {
  studentIds: number[]
  batchId: string
  majorId: string
  gradeYear: string
  degreeLevel: string
  studentCategory: string
  buildingId: string
  unselectedOnly: boolean
  pendingReviewOnly: boolean
}

const props = defineProps<{ modelValue: RecipientCriteria; recipientCount?: number; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: RecipientCriteria]; preflight: [] }>()
const studentOptions = ref<EntityOption[]>([])
const batchOptions = ref<EntityOption[]>([])
const majorOptions = ref<EntityOption[]>([])
const buildingOptions = ref<EntityOption[]>([])
const loadingOptions = ref(false)
let studentSearchTimer: number | undefined

onMounted(loadFixedOptions)

function update<K extends keyof RecipientCriteria>(key: K, value: RecipientCriteria[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function asOptions(items: DataObject[]): EntityOption[] {
  return items.map(item => ({
    value: Number(item.value),
    label: String(item.label ?? item.value),
    description: item.description ? String(item.description) : undefined,
  }))
}

async function loadFixedOptions() {
  loadingOptions.value = true
  try {
    const [batches, majors, buildings] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/majors'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/buildings'),
    ])
    batchOptions.value = asOptions((batches.data.data ?? []) as DataObject[])
    majorOptions.value = asOptions((majors.data.data ?? []) as DataObject[])
    buildingOptions.value = asOptions((buildings.data.data ?? []) as DataObject[])
  } finally {
    loadingOptions.value = false
  }
}

function searchStudents(keyword: string) {
  if (studentSearchTimer) window.clearTimeout(studentSearchTimer)
  if (keyword.trim().length < 2) {
    studentOptions.value = []
    return
  }
  studentSearchTimer = window.setTimeout(async () => {
    const response = await api.get<ListSuccessResponse>(
      '/api/v1/admin/governance/notifications/options/students',
      { params: { keyword: keyword.trim() } },
    )
    studentOptions.value = asOptions((response.data.data ?? []) as DataObject[])
  }, 250)
}
</script>

<template>
  <div class="recipient-selector">
    <div class="recipient-grid">
      <label class="span-3"><span>精确选择学生</span><RemoteEntitySelect :model-value="modelValue.studentIds" :options="studentOptions" multiple search-placeholder="输入姓名或学号，至少2个字符" empty-text="输入姓名或学号搜索学生" :disabled="busy" @remote-search="searchStudents" @update:model-value="update('studentIds', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>选寝批次</span><RemoteEntitySelect :model-value="modelValue.batchId" :options="batchOptions" placeholder="不限批次" search-placeholder="搜索批次名称" :loading="loadingOptions" :disabled="busy" @update:model-value="update('batchId', $event == null ? '' : String($event))" /></label>
      <label><span>专业</span><RemoteEntitySelect :model-value="modelValue.majorId" :options="majorOptions" placeholder="不限专业" search-placeholder="搜索专业" :loading="loadingOptions" :disabled="busy" @update:model-value="update('majorId', $event == null ? '' : String($event))" /></label>
      <label><span>楼栋</span><RemoteEntitySelect :model-value="modelValue.buildingId" :options="buildingOptions" placeholder="不限楼栋" search-placeholder="搜索楼栋" :loading="loadingOptions" :disabled="busy" @update:model-value="update('buildingId', $event == null ? '' : String($event))" /></label>
      <label><span>年级</span><input class="input" type="number" min="2000" max="2100" :value="modelValue.gradeYear" placeholder="不限" @input="update('gradeYear', ($event.target as HTMLInputElement).value)" /></label>
      <label><span>培养层次</span><select class="input" :value="modelValue.degreeLevel" @change="update('degreeLevel', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option></select></label>
      <label><span>学生类别</span><select class="input" :value="modelValue.studentCategory" @change="update('studentCategory', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select></label>
    </div>
    <div class="recipient-flags"><label><input type="checkbox" :checked="modelValue.unselectedOnly" @change="update('unselectedOnly', ($event.target as HTMLInputElement).checked)" />仅未选学生</label><label><input type="checkbox" :checked="modelValue.pendingReviewOnly" @change="update('pendingReviewOnly', ($event.target as HTMLInputElement).checked)" />仅待审核学生</label></div>
    <div class="recipient-preflight"><div><span>预检人数</span><strong>{{ recipientCount ?? '尚未预检' }}</strong></div><button class="button secondary" type="button" :disabled="busy" @click="emit('preflight')">{{ busy ? '预检中…' : '人数与内容预检' }}</button></div>
  </div>
</template>

<style scoped>
.recipient-selector{display:grid;gap:14px}.recipient-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.recipient-grid label{display:grid;align-content:start;gap:6px;min-width:0}.recipient-grid label>span{font-size:12px;font-weight:700}.span-3{grid-column:1/-1}.recipient-flags{display:flex;gap:18px;flex-wrap:wrap}.recipient-flags label{display:flex;align-items:center;gap:7px}.recipient-preflight{display:flex;align-items:center;justify-content:space-between;padding:14px;border-radius:12px;background:var(--surface-soft)}.recipient-preflight div{display:grid;gap:3px}.recipient-preflight span{font-size:12px;color:var(--text-muted)}.recipient-preflight strong{font-size:24px}@media(max-width:820px){.recipient-grid{grid-template-columns:1fr 1fr}.span-3{grid-column:1/-1}}@media(max-width:560px){.recipient-grid{grid-template-columns:1fr}.span-3{grid-column:auto}.recipient-preflight{align-items:flex-start;flex-direction:column;gap:12px}}
</style>
