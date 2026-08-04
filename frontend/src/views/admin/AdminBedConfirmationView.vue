<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const rooms = ref<DataObject[]>([])
const selectedRoom = ref<DataObject | null>(null)
const keyword = ref('')
const reviewFilter = ref('ALL')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')
const approvalReason = ref('现场核查寝室实际床位无误')
const students = computed(() => ((selectedRoom.value?.students ?? []) as DataObject[]))
const beds = computed(() => ((selectedRoom.value?.beds ?? []) as DataObject[]))
const readyCount = computed(() => students.value.filter(item => item.review_state === 'READY').length)
const filteredRooms = computed(() => rooms.value.filter((room) => {
  if (reviewFilter.value === 'CONFLICT') return Number(room.conflict_count ?? 0) > 0
  if (reviewFilter.value === 'READY') return Number(room.pending_count ?? 0) > Number(room.conflict_count ?? 0)
  if (reviewFilter.value === 'EMPTY') return Number(room.pending_count ?? 0) === 0
  return true
}))

onMounted(loadRooms)
async function loadRooms(){loading.value=true;error.value='';try{const response=await api.get<ListSuccessResponse>('/api/v1/admin/bed-confirmations/rooms',{params:{keyword:keyword.value||undefined}});rooms.value=(response.data.data??[]) as DataObject[]}catch(cause){error.value=cause instanceof Error?cause.message:'加载核查寝室失败'}finally{loading.value=false}}
async function openRoom(room:DataObject){busy.value=true;error.value='';try{const response=await api.get<ObjectSuccessResponse>(`/api/v1/admin/bed-confirmations/rooms/${room.room_id}`);selectedRoom.value=(response.data.data??{}) as DataObject}catch(cause){error.value=cause instanceof Error?cause.message:'加载寝室核查详情失败'}finally{busy.value=false}}
async function approveRoom(){if(!selectedRoom.value||!approvalReason.value.trim()||busy.value)return;busy.value=true;error.value='';message.value='';try{const response=await api.post<ObjectSuccessResponse>(`/api/v1/admin/bed-confirmations/rooms/${selectedRoom.value.room_id}/approve`,{reason:approvalReason.value.trim()});const data=(response.data.data??{}) as DataObject;message.value=`已通过 ${data.approvedCount??0} 条无冲突申报，保留 ${data.conflictCount??0} 条冲突申报待处理。`;await openRoom(selectedRoom.value);await loadRooms()}catch(cause){error.value=cause instanceof Error?cause.message:'按寝室核查失败'}finally{busy.value=false}}
async function reject(item:DataObject){const reason=window.prompt(`驳回 ${item.student_name} 的实际床位申报`,item.review_state==='DUPLICATE'?'多人申报同一床位，请重新核对':'申报床位与现场不一致');if(!reason?.trim()||busy.value)return;busy.value=true;try{await api.post(`/api/v1/admin/bed-confirmations/requests/${item.request_id}/reject`,{reason:reason.trim()});message.value='已驳回该申报。';if(selectedRoom.value)await openRoom(selectedRoom.value);await loadRooms()}catch(cause){error.value=cause instanceof Error?cause.message:'驳回失败'}finally{busy.value=false}}
function reviewText(value:unknown){return({NONE:'未申报',READY:'可通过',OCCUPIED:'申报床位已被占用',DUPLICATE:'多人申报同一床位'} as Record<string,string>)[String(value)]??String(value)}
function closeRoom(){selectedRoom.value=null}
</script>

<template>
  <div class="content-column mobile-room-review">
    <header v-if="!embedded" class="page-title split-title"><div><span class="eyebrow">ACTUAL BED REVIEW</span><h2>按寝室核查实际床位</h2><p>学生自主申报不会直接修改正式床位。进入寝室后核对全寝室申报，可一次通过全部无冲突记录。</p></div><button class="button secondary" @click="loadRooms">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <section class="panel room-search"><input v-model.trim="keyword" class="input" placeholder="搜索楼栋或寝室号" @keyup.enter="loadRooms"><select v-model="reviewFilter" class="input"><option value="ALL">全部核查状态</option><option value="READY">存在可直接通过记录</option><option value="CONFLICT">存在冲突记录</option><option value="EMPTY">暂无待核查记录</option></select><button class="button primary" @click="loadRooms">查询</button></section>
    <p v-if="loading" class="panel empty-state">正在加载待核查寝室…</p>
    <div v-else class="review-room-grid"><article v-for="room in filteredRooms" :key="String(room.room_id)" class="panel review-room-card" @click="openRoom(room)"><header><div><strong>{{ room.building_name }} {{ room.room_number }}室</strong><span>{{ room.floor_number }}层 · {{ room.resident_count }}人</span></div><b>{{ room.pending_count }} 条待核查</b></header><div class="room-review-counts"><span>冲突 {{ room.conflict_count??0 }}</span><span>容量 {{ room.capacity }}</span></div><button class="button secondary full">进入寝室核查</button></article><p v-if="!rooms.length" class="panel empty-state">当前没有待核查的寝室。</p></div>

    <div v-if="selectedRoom" class="room-review-overlay" @click.self="closeRoom"><section class="room-review-sheet">
      <header class="room-review-header"><button class="button ghost" @click="closeRoom">返回</button><div><span class="eyebrow">按寝室核查</span><h3>{{ selectedRoom.building_name }} {{ selectedRoom.room_number }}室</h3><p>{{ selectedRoom.floor_number }}层 · {{ students.length }}名在住学生 · {{ readyCount }}条可直接通过</p></div></header>
      <div class="bed-reference-strip"><span v-for="bed in beds" :key="String(bed.bed_id)" :class="{occupied:Number(bed.occupied)===1}"><strong>{{ bed.bed_code }}</strong>{{ bedTypeLabel(bed.bed_type) }}</span></div>
      <div class="student-review-list"><article v-for="student in students" :key="String(student.residency_id)" class="student-review-card" :class="String(student.review_state).toLowerCase()"><header><div><strong>{{ student.student_name }}</strong><span>{{ student.student_number }}</span></div><b>{{ reviewText(student.review_state) }}</b></header><dl><div><dt>系统正式床位</dt><dd>{{ student.current_bed_code||'未确认' }}</dd></div><div><dt>学生申报床位</dt><dd>{{ student.declared_bed_code||'未申报' }}</dd></div></dl><p v-if="student.reason">申报说明：{{ student.reason }}</p><button v-if="student.request_id&&student.review_state!=='READY'" class="button danger full" :disabled="busy" @click="reject(student)">驳回并要求重新申报</button></article></div>
      <footer class="sticky-room-actions"><label><span>寝室核查说明</span><input v-model="approvalReason" class="input" maxlength="500"></label><button class="button primary" :disabled="busy||readyCount===0" @click="approveRoom">{{ busy?'正在处理…':`通过无冲突申请（${readyCount}）` }}</button></footer>
    </section></div>
  </div>
</template>

<style scoped>.room-search{display:grid;grid-template-columns:minmax(220px,1fr) 220px auto;gap:10px}.review-room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px}.review-room-card{display:grid;gap:14px;cursor:pointer}.review-room-card header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.review-room-card header div{display:grid;gap:4px}.review-room-card span{color:var(--muted)}.review-room-card b{color:#245da8}.room-review-counts{display:flex;justify-content:space-between;padding:10px;border-radius:12px;background:var(--soft)}.room-review-overlay{position:fixed;z-index:1350;inset:0;display:grid;place-items:center;padding:18px;background:rgba(8,22,47,.76);backdrop-filter:blur(8px)}.room-review-sheet{position:relative;width:min(980px,100%);max-height:calc(100vh - 36px);overflow:auto;padding:24px 24px 112px;border-radius:26px;background:var(--panel,#fff)}.room-review-header{display:flex;align-items:flex-start;gap:16px}.room-review-header h3{margin:4px 0}.room-review-header p{margin:0;color:var(--muted)}.bed-reference-strip{display:flex;gap:8px;overflow-x:auto;margin:18px 0;padding-bottom:5px}.bed-reference-strip span{display:grid;gap:3px;min-width:110px;padding:10px;border:1px solid var(--line);border-radius:12px;background:var(--soft)}.bed-reference-strip span.occupied{border-color:#f1c5c5}.student-review-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.student-review-card{display:grid;gap:12px;padding:16px;border:1px solid var(--line);border-radius:16px}.student-review-card.ready{border-color:#a7dbc5;background:#f1fbf7}.student-review-card.occupied,.student-review-card.duplicate{border-color:#f2c7c7;background:#fff6f6}.student-review-card header{display:flex;justify-content:space-between;gap:10px}.student-review-card header div{display:grid;gap:4px}.student-review-card header span,.student-review-card p{color:var(--muted)}.student-review-card dl{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:0}.student-review-card dl div{padding:9px;border-radius:10px;background:var(--soft)}.student-review-card dt{color:var(--muted);font-size:12px}.student-review-card dd{margin:3px 0 0;font-weight:700}.sticky-room-actions{position:absolute;right:0;bottom:0;left:0;display:grid;grid-template-columns:1fr auto;align-items:end;gap:12px;padding:14px 24px;border-top:1px solid var(--line);background:rgba(255,255,255,.96);backdrop-filter:blur(10px)}.sticky-room-actions label{display:grid;gap:5px}@media(max-width:700px){.room-search{grid-template-columns:1fr}.room-review-overlay{align-items:end;padding:0}.room-review-sheet{width:100%;max-height:94vh;padding:18px 14px 154px;border-radius:26px 26px 0 0}.room-review-header{display:grid}.student-review-list{grid-template-columns:1fr}.sticky-room-actions{position:absolute;grid-template-columns:1fr;padding:12px 14px calc(12px + env(safe-area-inset-bottom))}.student-review-card dl{grid-template-columns:1fr}}
</style>
