<script setup lang="ts">
export interface AnalyticsFilters {
  academicYear: string
  batchId: string
  majorId: string
  gradeYear: string
  degreeLevel: string
  studentCategory: string
  campusId: string
  buildingId: string
  roomType: string
}
const props = defineProps<{ modelValue: AnalyticsFilters; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: AnalyticsFilters]; apply: []; reset: [] }>()
function update<K extends keyof AnalyticsFilters>(key: K, value: AnalyticsFilters[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <div class="analytics-filter-bar">
    <label><span>学年</span><input class="input" type="number" :value="modelValue.academicYear" @input="update('academicYear', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>批次编号</span><input class="input" :value="modelValue.batchId" @input="update('batchId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>专业编号</span><input class="input" :value="modelValue.majorId" @input="update('majorId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>年级</span><input class="input" :value="modelValue.gradeYear" @input="update('gradeYear', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>培养层次</span><select class="input" :value="modelValue.degreeLevel" @change="update('degreeLevel', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="UNDERGRADUATE">本科</option><option value="MASTER">硕士</option><option value="DOCTOR">博士</option></select></label>
    <label><span>学生类别</span><select class="input" :value="modelValue.studentCategory" @change="update('studentCategory', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select></label>
    <label><span>校区编号</span><input class="input" :value="modelValue.campusId" @input="update('campusId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>楼栋编号</span><input class="input" :value="modelValue.buildingId" @input="update('buildingId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>房型</span><input class="input" :value="modelValue.roomType" @input="update('roomType', ($event.target as HTMLInputElement).value)" /></label>
    <div class="button-row analytics-actions"><button class="button ghost" type="button" :disabled="busy" @click="emit('reset')">重置</button><button class="button primary" type="button" :disabled="busy" @click="emit('apply')">{{ busy ? '分析中…' : '应用筛选' }}</button></div>
  </div>
</template>

<style scoped>
.analytics-filter-bar{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.analytics-filter-bar label{display:grid;gap:6px}.analytics-filter-bar label>span{font-size:12px;color:var(--text-muted);font-weight:700}.analytics-actions{align-self:end;justify-content:flex-end}@media(max-width:1100px){.analytics-filter-bar{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:700px){.analytics-filter-bar{grid-template-columns:1fr 1fr}}@media(max-width:480px){.analytics-filter-bar{grid-template-columns:1fr}}
</style>
