<script setup lang="ts">
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

function update<K extends keyof RecipientCriteria>(key: K, value: RecipientCriteria[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function parseStudentIds(value: string) {
  update('studentIds', [...new Set(value.split(/[\s,，;；]+/).map(Number).filter((id) => Number.isInteger(id) && id > 0))])
}
</script>

<template>
  <div class="recipient-selector">
    <div class="recipient-grid">
      <label class="span-2"><span>学生编号列表</span><textarea class="input" rows="3" :value="modelValue.studentIds.join(',')" placeholder="输入内部学生编号，以逗号或换行分隔；填写后优先使用精确列表" @input="parseStudentIds(($event.target as HTMLTextAreaElement).value)" /></label>
      <label><span>批次编号</span><input class="input" :value="modelValue.batchId" @input="update('batchId', ($event.target as HTMLInputElement).value)" /></label>
      <label><span>专业编号</span><input class="input" :value="modelValue.majorId" @input="update('majorId', ($event.target as HTMLInputElement).value)" /></label>
      <label><span>年级</span><input class="input" :value="modelValue.gradeYear" @input="update('gradeYear', ($event.target as HTMLInputElement).value)" /></label>
      <label><span>培养层次</span><select class="input" :value="modelValue.degreeLevel" @change="update('degreeLevel', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option></select></label>
      <label><span>学生类别</span><select class="input" :value="modelValue.studentCategory" @change="update('studentCategory', ($event.target as HTMLSelectElement).value)"><option value="">不限</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select></label>
      <label><span>楼栋编号</span><input class="input" :value="modelValue.buildingId" @input="update('buildingId', ($event.target as HTMLInputElement).value)" /></label>
    </div>
    <div class="recipient-flags"><label><input type="checkbox" :checked="modelValue.unselectedOnly" @change="update('unselectedOnly', ($event.target as HTMLInputElement).checked)" />仅未选学生</label><label><input type="checkbox" :checked="modelValue.pendingReviewOnly" @change="update('pendingReviewOnly', ($event.target as HTMLInputElement).checked)" />仅待审核学生</label></div>
    <div class="recipient-preflight"><div><span>预检人数</span><strong>{{ recipientCount ?? '尚未预检' }}</strong></div><button class="button secondary" type="button" :disabled="busy" @click="emit('preflight')">{{ busy ? '预检中…' : '人数与内容预检' }}</button></div>
  </div>
</template>

<style scoped>
.recipient-selector{display:grid;gap:14px}.recipient-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.recipient-grid label{display:grid;gap:6px}.recipient-grid label>span{font-size:12px;font-weight:700}.span-2{grid-column:span 2}.recipient-flags{display:flex;gap:18px;flex-wrap:wrap}.recipient-flags label{display:flex;align-items:center;gap:7px}.recipient-preflight{display:flex;align-items:center;justify-content:space-between;padding:14px;border-radius:12px;background:var(--surface-soft)}.recipient-preflight div{display:grid;gap:3px}.recipient-preflight span{font-size:12px;color:var(--text-muted)}.recipient-preflight strong{font-size:24px}@media(max-width:820px){.recipient-grid{grid-template-columns:1fr 1fr}}@media(max-width:560px){.recipient-grid{grid-template-columns:1fr}.span-2{grid-column:auto}.recipient-preflight{align-items:flex-start;flex-direction:column;gap:12px}}
</style>
