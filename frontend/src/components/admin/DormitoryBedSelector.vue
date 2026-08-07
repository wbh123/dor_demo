<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DataObject } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

const props = withDefaults(defineProps<{
  beds: DataObject[]
  modelValue: number
  disabled?: boolean
  allowOccupied?: boolean
}>(), {
  disabled: false,
  allowOccupied: true,
})
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

const buildingId = ref<number | null>(null)
const roomId = ref<number | null>(null)
const buildings = computed(() => {
  const seen = new Map<number, string>()
  for (const bed of props.beds) seen.set(Number(bed.building_id), String(bed.building_name))
  return [...seen.entries()].map(([id, name]) => ({ id, name }))
})
const rooms = computed(() => {
  const seen = new Map<number, DataObject>()
  for (const bed of props.beds) {
    if (buildingId.value && Number(bed.building_id) !== buildingId.value) continue
    seen.set(Number(bed.room_id), bed)
  }
  return [...seen.values()]
})
const visibleBeds = computed(() => props.beds.filter(bed =>
  (!buildingId.value || Number(bed.building_id) === buildingId.value)
  && (!roomId.value || Number(bed.room_id) === roomId.value),
))
const selectedBed = computed(() => props.beds.find(bed => Number(bed.bed_id ?? bed.id) === props.modelValue) ?? null)

watch(() => props.beds, () => {
  const current = selectedBed.value
  if (current) {
    buildingId.value = Number(current.building_id)
    roomId.value = Number(current.room_id)
  } else {
    buildingId.value = buildings.value[0]?.id ?? null
    roomId.value = rooms.value[0] ? Number(rooms.value[0].room_id) : null
  }
}, { immediate: true })
watch(buildingId, () => {
  if (!rooms.value.some(room => Number(room.room_id) === roomId.value)) {
    roomId.value = rooms.value[0] ? Number(rooms.value[0].room_id) : null
  }
})

function bedId(bed: DataObject) { return Number(bed.bed_id ?? bed.id) }
function occupied(bed: DataObject) { return Boolean(Number(bed.occupied ?? 0)) }
function selectable(bed: DataObject) { return Boolean(Number(bed.selectable ?? 1)) && (!occupied(bed) || props.allowOccupied) }
function occupancyText(bed: DataObject) {
  const source = String(bed.occupancy_source ?? bed.occupancySource ?? 'AVAILABLE')
  if (source === 'AVAILABLE') return '空闲可用'
  const sourceText = ({ RESIDENCY:'正式在住', ALLOCATION:'有效分配', PENDING_CONFIRMATION:'待核查', ROOM_EXCHANGE:'交换锁定' } as Record<string,string>)[source] ?? source
  const student = String(bed.occupant_student_name ?? bed.occupantStudentName ?? '')
  return student ? `${sourceText} · ${student}` : sourceText
}
function choose(bed: DataObject) { if (!props.disabled && selectable(bed)) emit('update:modelValue', bedId(bed)) }
</script>

<template>
  <div class="dormitory-bed-selector">
    <div class="selector-filters"><label><span>楼栋</span><select v-model.number="buildingId" class="input" :disabled="disabled"><option v-for="building in buildings" :key="building.id" :value="building.id">{{ building.name }}</option></select></label><label><span>寝室</span><select v-model.number="roomId" class="input" :disabled="disabled"><option v-for="room in rooms" :key="String(room.room_id)" :value="Number(room.room_id)">{{ room.building_name }} {{ room.room_number }} · {{ room.floor_number }}层</option></select></label></div>
    <div class="bed-selector-grid">
      <button v-for="bed in visibleBeds" :key="bedId(bed)" type="button" class="bed-selector-card" :class="{selected:modelValue===bedId(bed),occupied:occupied(bed),blocked:!selectable(bed)}" :disabled="disabled || !selectable(bed)" :title="String(bed.blocking_reason ?? bed.blockingReason ?? '')" @click="choose(bed)"><div><strong>{{ bed.bed_code }}</strong><span>{{ bedTypeLabel(bed.bed_type) }}</span></div><small>{{ occupancyText(bed) }}</small><em v-if="Number(bed.swap_required ?? 0)===1">选择后交换</em><em v-else-if="modelValue===bedId(bed)">已选择</em></button>
      <p v-if="!visibleBeds.length" class="empty-state compact">当前筛选范围没有可展示床位。</p>
    </div>
    <p v-if="selectedBed && Number(selectedBed.swap_required ?? 0)===1" class="alert warning selector-warning">该床位已有学生。提交后系统会再次确认，确认无变化后交换两名学生的床位。</p>
    <p v-else-if="selectedBed && (selectedBed.blocking_reason || selectedBed.blockingReason)" class="selector-note">{{ selectedBed.blocking_reason ?? selectedBed.blockingReason }}</p>
  </div>
</template>

<style scoped>
.dormitory-bed-selector{display:grid;gap:12px}.selector-filters{display:grid;grid-template-columns:1fr 1fr;gap:10px}.selector-filters label{display:grid;gap:5px}.bed-selector-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:9px;max-height:360px;overflow:auto;padding:3px}.bed-selector-card{display:grid;gap:7px;min-width:0;padding:12px;border:1px solid var(--border);border-radius:13px;background:var(--surface);color:inherit;text-align:left;cursor:pointer}.bed-selector-card>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.bed-selector-card span,.bed-selector-card small{color:var(--text-muted)}.bed-selector-card small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bed-selector-card em{color:var(--primary);font-size:11px;font-style:normal;font-weight:800}.bed-selector-card.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 12%,transparent)}.bed-selector-card.occupied{background:#fffaf2}.bed-selector-card.blocked{cursor:not-allowed;opacity:.55}.selector-warning{margin:0}.selector-note{margin:0;color:var(--text-muted);font-size:12px}@media(max-width:620px){.selector-filters{grid-template-columns:1fr}.bed-selector-grid{grid-template-columns:1fr 1fr}}
</style>
