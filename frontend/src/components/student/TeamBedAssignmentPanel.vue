<script setup lang="ts">
import { computed } from 'vue'
import type { DataObject } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'
import type { TeamMemberAssignment } from './teamSelectionTypes'

const props = defineProps<{
  members: DataObject[]
  beds: DataObject[]
  memberAssignments: TeamMemberAssignment[]
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:memberAssignments': [value: TeamMemberAssignment[]] }>()
const assignmentMap = computed(() => new Map(props.memberAssignments.map(item => [Number(item.studentId), Number(item.bedId)])))
function bedTakenByOther(studentId:number,bedId:number){return props.memberAssignments.some(item=>Number(item.studentId)!==studentId&&Number(item.bedId)===bedId)}
function assign(studentId:number,event:Event){const bedId=Number((event.target as HTMLSelectElement).value||0);emit('update:memberAssignments',props.members.map(member=>({studentId:Number(member.studentId),bedId:Number(member.studentId)===studentId?bedId:Number(assignmentMap.value.get(Number(member.studentId))??0)})))}
</script>

<template>
  <section class="team-bed-assignment-panel"><header><div><strong>队友床位安排</strong><p>所有床位由队长统一确定。每名已确认成员必须选择一张不同床位。</p></div><span>{{ memberAssignments.filter(item=>item.bedId).length }}/{{ members.length }}人已安排</span></header><div class="team-member-bed-grid"><article v-for="member in members" :key="String(member.studentId)" class="team-member-bed-card"><div><strong>{{ member.studentName }}</strong><span>{{ member.studentNumber }} · {{ member.majorName ?? '未设置专业' }}</span><small>{{ member.memberRole==='LEADER'?'队长':'已确认队友' }}</small></div><label><span>确定床位</span><select class="input" :disabled="disabled" :value="assignmentMap.get(Number(member.studentId))??0" @change="assign(Number(member.studentId),$event)"><option :value="0">请选择床位</option><option v-for="bed in beds" :key="String(bed.id)" :value="Number(bed.id)" :disabled="bedTakenByOther(Number(member.studentId),Number(bed.id))">{{ bed.bed_code }} · {{ bedTypeLabel(bed.bed_type) }}{{ bedTakenByOther(Number(member.studentId),Number(bed.id))?'（已分配给队友）':'' }}</option></select></label></article></div></section>
</template>

<style scoped>
.team-bed-assignment-panel{display:grid;gap:12px;padding:14px;border:1px solid var(--border);border-radius:16px;background:var(--surface)}.team-bed-assignment-panel>header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.team-bed-assignment-panel header p{margin:4px 0 0}.team-bed-assignment-panel header>span{flex:0 0 auto;padding:5px 9px;border-radius:999px;background:var(--surface-soft);font-size:12px;font-weight:800}.team-member-bed-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:10px}.team-member-bed-card{display:grid;gap:10px;min-width:0;padding:12px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.team-member-bed-card>div{display:grid;gap:2px;min-width:0}.team-member-bed-card span,.team-member-bed-card small{overflow:hidden;color:var(--text-muted);text-overflow:ellipsis;white-space:nowrap}.team-member-bed-card label{display:grid;gap:5px}.team-member-bed-card label>span{font-size:12px;font-weight:700}@media(max-width:620px){.team-bed-assignment-panel>header{flex-direction:column}.team-member-bed-grid{grid-template-columns:1fr}}
</style>
