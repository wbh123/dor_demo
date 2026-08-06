<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import AppModal from '../../../components/modal/AppModal.vue'

type SelectOption = { id: string; label: string }

const props = defineProps<{
  open: boolean
  scopeBatch: DataObject | null
  scopeLoading: boolean
  scopeSaving: boolean
  selectedStudentIds: number[]
  selectedRoomIds: number[]
  studentFilter: string
  studentGenderFilter: string
  studentCategoryFilter: string
  studentDegreeFilter: string
  studentMajorFilter: string
  studentGradeFilter: string
  roomFilter: string
  roomGenderFilter: string
  roomScopeFilter: string
  roomBuildingFilter: string
  roomFloorFilter: string
  scopeMajorOptions: SelectOption[]
  scopeGradeOptions: string[]
  scopeBuildingOptions: SelectOption[]
  scopeFloorOptions: string[]
  filteredStudents: DataObject[]
  filteredRooms: DataObject[]
  publishAfterScope: boolean
  publishFlowState: string
  publishFlowBusy: boolean
  runningPreflight: boolean
}>()

const emit = defineEmits<{
  close: []
  'toggle-student': [studentId: number]
  'toggle-room': [roomId: number]
  'select-all-students': []
  'clear-students': []
  'select-all-rooms': []
  'clear-rooms': []
  'update:student-filter': [value: string]
  'update:student-gender-filter': [value: string]
  'update:student-category-filter': [value: string]
  'update:student-degree-filter': [value: string]
  'update:student-major-filter': [value: string]
  'update:student-grade-filter': [value: string]
  'update:room-filter': [value: string]
  'update:room-gender-filter': [value: string]
  'update:room-scope-filter': [value: string]
  'update:room-building-filter': [value: string]
  'update:room-floor-filter': [value: string]
  save: []
  'save-and-publish': []
}>()
</script>

<template>
  <AppModal
    :open="open"
    :title="`${scopeBatch?.batch_name ?? '批次'} · 参与范围`"
    description="筛选器固定在标题下方，结果列表独立滚动；保存并发布会在当前窗口内连续执行。"
    size="large"
    max-height="94dvh"
    :busy="scopeLoading"
    :prevent-close="publishFlowBusy || publishFlowState === 'WAITING_CONFIRMATION'"
    @close="emit('close')"
  >
    <template v-if="!scopeLoading">
      <div class="scope-summary">
        <article><span>已选学生</span><strong>{{ selectedStudentIds.length }}</strong></article>
        <article><span>已选宿舍</span><strong>{{ selectedRoomIds.length }}</strong></article>
      </div>

      <div class="scope-grid">
        <section class="scope-column">
          <header class="scope-column-header">
            <div><strong>参与学生</strong><small>按学号、姓名、专业和学生属性筛选</small></div>
            <div class="button-row">
              <button class="button ghost small" type="button" @click="emit('select-all-students')">全选当前结果</button>
              <button class="button ghost small" type="button" @click="emit('clear-students')">清空</button>
            </div>
          </header>
          <div class="scope-filter-panel scope-filter-grid">
            <input :value="studentFilter" class="input span-2" placeholder="搜索学号、姓名或专业" @input="emit('update:student-filter', ($event.target as HTMLInputElement).value.trim())" />
            <select :value="studentGenderFilter" class="input" @change="emit('update:student-gender-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部性别</option><option value="M">男生</option><option value="F">女生</option></select>
            <select :value="studentCategoryFilter" class="input" @change="emit('update:student-category-filter', ($event.target as HTMLSelectElement).value)"><option value="">国内外不限</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select>
            <select :value="studentDegreeFilter" class="input" @change="emit('update:student-degree-filter', ($event.target as HTMLSelectElement).value)"><option value="">培养层次不限</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select>
            <select :value="studentMajorFilter" class="input" @change="emit('update:student-major-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部专业</option><option v-for="major in scopeMajorOptions" :key="major.id" :value="major.id">{{ major.label }}</option></select>
            <select :value="studentGradeFilter" class="input span-2" @change="emit('update:student-grade-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部年级</option><option v-for="grade in scopeGradeOptions" :key="grade" :value="grade">{{ grade }}级</option></select>
          </div>
          <div class="scope-result-summary"><span>筛选结果 {{ filteredStudents.length }} 人</span><span>已选择 {{ selectedStudentIds.length }} 人</span></div>
          <div class="scope-result-list">
            <label v-for="student in filteredStudents" :key="String(student.id)" class="scope-option">
              <input type="checkbox" :checked="selectedStudentIds.includes(Number(student.id))" @change="emit('toggle-student', Number(student.id))" />
              <div><strong>{{ student.student_number }} · {{ student.student_name }}</strong><span>{{ student.major_name }} · {{ student.gender === 'M' ? '男' : '女' }} · {{ student.student_category === 'INTERNATIONAL' ? '国际生' : '国内生' }}</span></div>
            </label>
            <p v-if="filteredStudents.length === 0" class="empty-state compact">没有符合当前条件的学生，筛选器位置不会随结果数量改变。</p>
          </div>
        </section>

        <section class="scope-column">
          <header class="scope-column-header">
            <div><strong>可选宿舍</strong><small>停用、维护或不符合批次规则的宿舍不可选择</small></div>
            <div class="button-row">
              <button class="button ghost small" type="button" @click="emit('select-all-rooms')">全选当前可用</button>
              <button class="button ghost small" type="button" @click="emit('clear-rooms')">清空</button>
            </div>
          </header>
          <div class="scope-filter-panel scope-filter-grid">
            <input :value="roomFilter" class="input span-2" placeholder="搜索楼栋、楼层或房间号" @input="emit('update:room-filter', ($event.target as HTMLInputElement).value.trim())" />
            <select :value="roomGenderFilter" class="input" @change="emit('update:room-gender-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部性别</option><option value="M">男寝</option><option value="F">女寝</option></select>
            <select :value="roomScopeFilter" class="input" @change="emit('update:room-scope-filter', ($event.target as HTMLSelectElement).value)"><option value="">国内外不限</option><option value="DOMESTIC_ONLY">国内生宿舍</option><option value="INTERNATIONAL_ONLY">国际生宿舍</option><option value="MIXED">混住宿舍</option></select>
            <select :value="roomBuildingFilter" class="input" @change="emit('update:room-building-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部楼栋</option><option v-for="building in scopeBuildingOptions" :key="building.id" :value="building.id">{{ building.label }}</option></select>
            <select :value="roomFloorFilter" class="input" @change="emit('update:room-floor-filter', ($event.target as HTMLSelectElement).value)"><option value="">全部楼层</option><option v-for="floor in scopeFloorOptions" :key="floor" :value="floor">{{ floor }}层</option></select>
          </div>
          <div class="scope-result-summary"><span>筛选结果 {{ filteredRooms.length }} 间</span><span>已选择 {{ selectedRoomIds.length }} 间</span></div>
          <div class="scope-result-list">
            <label v-for="room in filteredRooms" :key="String(room.id)" class="scope-option" :class="{ disabled: !room.selectable }">
              <input type="checkbox" :disabled="!room.selectable" :checked="selectedRoomIds.includes(Number(room.id))" @change="emit('toggle-room', Number(room.id))" />
              <div><strong>{{ room.building_name }} {{ room.room_number }}</strong><span>{{ room.floor_number }}层 · 容量{{ room.capacity }} · {{ room.gender_restriction === 'M' ? '男寝' : '女寝' }} · {{ room.operational_status }}</span></div>
            </label>
            <p v-if="filteredRooms.length === 0" class="empty-state compact">没有符合当前条件的宿舍，筛选器仍保持顶部对齐。</p>
          </div>
        </section>
      </div>
    </template>

    <template #footer>
      <div class="scope-footer-status">
        <strong>{{ publishAfterScope ? '需要补齐范围后继续发布' : '当前状态：' + publishFlowState }}</strong>
        <span>范围保存成功后即使发布失败也不会丢失。</span>
      </div>
      <button class="button ghost" type="button" :disabled="publishFlowBusy" @click="emit('close')">关闭</button>
      <button class="button secondary" type="button" :disabled="scopeLoading || publishFlowBusy" @click="emit('save')">{{ scopeSaving ? '保存中…' : '仅保存范围' }}</button>
      <button class="button primary" type="button" :disabled="scopeLoading || publishFlowBusy" @click="emit('save-and-publish')">{{ scopeSaving || runningPreflight ? '正在准备发布…' : '保存并发布' }}</button>
    </template>
  </AppModal>
</template>

<style scoped>
.scope-summary{display:grid;grid-template-columns:repeat(2,minmax(0,180px));gap:12px;margin-bottom:14px}.scope-summary article{padding:14px;border-radius:12px;background:var(--surface-soft)}.scope-summary strong{display:block;font-size:24px}.scope-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);align-items:start;gap:16px}.scope-column{display:flex;flex-direction:column;justify-content:flex-start;align-self:start;gap:12px;min-width:0;padding:14px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.scope-column-header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;flex:0 0 auto}.scope-column-header small{display:block;margin-top:4px;color:var(--text-muted)}.scope-filter-panel{flex:0 0 auto}.scope-filter-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.scope-filter-grid .span-2{grid-column:span 2}.scope-result-summary{display:flex;justify-content:space-between;gap:10px;flex:0 0 auto;padding:9px 11px;border-radius:10px;background:var(--surface);color:var(--text-muted);font-size:12px;font-weight:700}.scope-result-list{display:grid;gap:8px;min-height:160px;max-height:440px;overflow:auto;overscroll-behavior:contain;padding-right:4px}.scope-option{display:flex;gap:10px;align-items:flex-start;padding:11px;border:1px solid var(--border);border-radius:11px;background:var(--surface)}.scope-option input{margin-top:4px}.scope-option div{display:grid;gap:3px}.scope-option span{color:var(--text-muted);font-size:12px}.scope-option.disabled{opacity:.55;background:var(--surface-soft)}.empty-state.compact{margin:0;padding:20px 12px}.scope-footer-status{display:grid;gap:3px;margin-right:auto;max-width:420px;color:var(--text-muted);font-size:12px}.scope-footer-status strong{color:var(--text)}@media(max-width:900px){.scope-grid{grid-template-columns:1fr}.scope-result-list{max-height:320px}}@media(max-width:720px){.scope-filter-grid{grid-template-columns:1fr}.scope-filter-grid .span-2{grid-column:auto}.scope-summary{grid-template-columns:1fr}.scope-column-header{flex-direction:column}.scope-result-summary{flex-wrap:wrap}.scope-footer-status{width:100%;max-width:none;flex-basis:100%}}
</style>
