<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MultiSelectDropdown from '../common/MultiSelectDropdown.vue'
import RemoteEntitySelect from '../common/RemoteEntitySelect.vue'
import type { EntityOption } from '../common/entitySelectTypes'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse } from '../../api/types'

export interface RecipientCriteria {
  studentIds: number[]
  batchIds: number[]
  majorIds: number[]
  buildingIds: number[]
  gradeYears: number[]
  degreeLevels: string[]
  studentCategories: string[]
  unselectedOnly: boolean
  pendingReviewOnly: boolean
}

const props = defineProps<{ modelValue: RecipientCriteria; recipientCount?: number; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: RecipientCriteria]; preflight: [] }>()
const studentOptions = ref<EntityOption[]>([])
const batchOptions = ref<EntityOption[]>([])
const majorOptions = ref<EntityOption[]>([])
const buildingOptions = ref<EntityOption[]>([])
const gradeOptions = ref<EntityOption[]>([])
const degreeOptions = ref<EntityOption[]>([])
const categoryOptions = ref<EntityOption[]>([])
const loadingOptions = ref(false)
const optionError = ref('')
let studentSearchTimer: number | undefined

onMounted(loadFixedOptions)
function update<K extends keyof RecipientCriteria>(key: K, value: RecipientCriteria[K]) { emit('update:modelValue', { ...props.modelValue, [key]: value }) }
function asOptions(items: DataObject[], numeric = true): EntityOption[] { return items.map(item => ({ value:numeric ? Number(item.value) : String(item.value), label:String(item.label ?? item.value), description:item.description ? String(item.description) : undefined })) }
async function loadFixedOptions() {
  loadingOptions.value = true
  optionError.value = ''
  try {
    const [batches, majors, buildings, grades, degrees, categories] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/majors'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/buildings'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/grade-years'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/degree-levels'),
      api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/student-categories'),
    ])
    batchOptions.value=asOptions((batches.data.data??[]) as DataObject[])
    majorOptions.value=asOptions((majors.data.data??[]) as DataObject[])
    buildingOptions.value=asOptions((buildings.data.data??[]) as DataObject[])
    gradeOptions.value=asOptions((grades.data.data??[]) as DataObject[])
    degreeOptions.value=asOptions((degrees.data.data??[]) as DataObject[], false)
    categoryOptions.value=asOptions((categories.data.data??[]) as DataObject[], false)
  } catch (cause) {
    optionError.value = cause instanceof Error ? cause.message : '接收范围选项加载失败'
  } finally { loadingOptions.value=false }
}
function searchStudents(keyword:string) {
  if(studentSearchTimer)window.clearTimeout(studentSearchTimer)
  if(keyword.trim().length<2){studentOptions.value=[];return}
  studentSearchTimer=window.setTimeout(async()=>{
    try {
      const response=await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/options/students',{params:{keyword:keyword.trim()}})
      studentOptions.value=asOptions((response.data.data??[]) as DataObject[])
    } catch (cause) {
      optionError.value = cause instanceof Error ? cause.message : '学生选项加载失败'
    }
  },250)
}
</script>

<template>
  <div class="recipient-selector">
    <p v-if="optionError" class="alert error compact-alert">{{ optionError }}</p>
    <div class="recipient-grid">
      <label class="span-3"><span>精确选择学生</span><RemoteEntitySelect :model-value="modelValue.studentIds" :options="studentOptions" multiple search-placeholder="输入姓名或学号，至少2个字符" empty-text="输入姓名或学号搜索学生" :disabled="busy" @remote-search="searchStudents" @update:model-value="update('studentIds', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>选寝批次</span><MultiSelectDropdown :model-value="modelValue.batchIds" :options="batchOptions" placeholder="不限批次" :loading="loadingOptions" :disabled="busy" @update:model-value="update('batchIds', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>专业</span><MultiSelectDropdown :model-value="modelValue.majorIds" :options="majorOptions" placeholder="不限专业" :loading="loadingOptions" :disabled="busy" @update:model-value="update('majorIds', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>楼栋</span><MultiSelectDropdown :model-value="modelValue.buildingIds" :options="buildingOptions" placeholder="不限楼栋" :loading="loadingOptions" :disabled="busy" @update:model-value="update('buildingIds', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>年级</span><MultiSelectDropdown :model-value="modelValue.gradeYears" :options="gradeOptions" placeholder="不限年级" :loading="loadingOptions" :disabled="busy" @update:model-value="update('gradeYears', ($event as Array<string|number>).map(Number))" /></label>
      <label><span>培养层次</span><MultiSelectDropdown :model-value="modelValue.degreeLevels" :options="degreeOptions" placeholder="不限培养层次" :loading="loadingOptions" :disabled="busy" @update:model-value="update('degreeLevels', ($event as Array<string|number>).map(String))" /></label>
      <label><span>学生类别</span><MultiSelectDropdown :model-value="modelValue.studentCategories" :options="categoryOptions" placeholder="不限学生类别" :loading="loadingOptions" :disabled="busy" @update:model-value="update('studentCategories', ($event as Array<string|number>).map(String))" /></label>
    </div>
    <div class="recipient-flags"><label><input type="checkbox" :checked="modelValue.unselectedOnly" @change="update('unselectedOnly', ($event.target as HTMLInputElement).checked)" />仅未选学生</label><label><input type="checkbox" :checked="modelValue.pendingReviewOnly" @change="update('pendingReviewOnly', ($event.target as HTMLInputElement).checked)" />仅待审核学生</label></div>
    <div class="recipient-preflight"><div><span>预检人数</span><strong>{{ recipientCount ?? '尚未预检' }}</strong></div><button class="button secondary" type="button" :disabled="busy" @click="emit('preflight')">{{ busy ? '预检中…' : '预检接收人数' }}</button></div>
  </div>
</template>

<style scoped>
.recipient-selector{display:grid;gap:14px}.compact-alert{margin:0}.recipient-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.recipient-grid label{display:grid;align-content:start;gap:6px;min-width:0}.recipient-grid label>span{font-size:12px;font-weight:700}.span-3{grid-column:1/-1}.recipient-flags{display:flex;gap:18px;flex-wrap:wrap}.recipient-flags label{display:flex;align-items:center;gap:7px}.recipient-preflight{display:flex;align-items:center;justify-content:space-between;padding:14px;border-radius:12px;background:var(--surface-soft)}.recipient-preflight div{display:grid;gap:3px}.recipient-preflight span{font-size:12px;color:var(--text-muted)}.recipient-preflight strong{font-size:24px}@media(max-width:820px){.recipient-grid{grid-template-columns:1fr 1fr}.span-3{grid-column:1/-1}}@media(max-width:560px){.recipient-grid{grid-template-columns:1fr}.span-3{grid-column:auto}.recipient-preflight{align-items:flex-start;flex-direction:column;gap:12px}}
</style>
