<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { useI18n } from '../../i18n'

type RecommendationStrategy = 'BEST_MATCH' | 'TRUE_RANDOM' | 'MATCH_WEIGHTED_RANDOM'
interface RecommendationOption { value: RecommendationStrategy; label: string }

const route = useRoute()
const router = useRouter()
const { hasFeature } = useFeatureAccess()
const batchId = Number(route.params.batchId)
const teamId = route.query.teamId ? Number(route.query.teamId) : null
const memberCount = route.query.memberCount ? Number(route.query.memberCount) : 0
const isTeamMode = computed(() => Boolean(teamId))
const rooms = ref<DataObject[]>([])
const activePersonalTeam = ref<DataObject | null>(null)
const showPersonalExitConfirm = ref(false)
const roomSelectionTarget = ref<DataObject | null>(null)
const selectionSuccess = ref<DataObject | null>(null)
const selectionReadiness = ref<DataObject>({ preferenceCompleted: false, allowWithoutQuestionnaire: false })
const preferencePromptVisible = ref(false)
const pendingPreferenceAction = ref<null | (() => void)>(null)
const loading = ref(true)
const preparingPersonalSelection = ref(false)
const selectingRoomId = ref<number | null>(null)
const recommending = ref(false)
const error = ref('')
const message = ref('')
const recommendationResult = ref<DataObject | null>(null)
const recommendationStrategy = ref<RecommendationStrategy>('BEST_MATCH')
const recommendationRequestId = ref('')
const keyword = ref('')
const floorFilter = ref('')
const minimumAvailableBeds = ref(0)
const roomPage = ref(1)
const roomColumnCount = ref(3)
const ROOM_ROWS_PER_PAGE = 4
const { subtitle, translateError } = useI18n()

const selectionMode = computed(() => String(rooms.value[0]?.selectionMode ?? 'BED'))
const isRoomMode = computed(() => selectionMode.value === 'ROOM')
const batchAllowedRecommendationStrategies = computed<RecommendationStrategy[]>(() => {
  const configured = rooms.value[0]?.allowedRecommendationStrategies
  if (!Array.isArray(configured) || configured.length === 0) return ['BEST_MATCH', 'TRUE_RANDOM', 'MATCH_WEIGHTED_RANDOM']
  return configured.map(String).filter((value): value is RecommendationStrategy => ['BEST_MATCH','TRUE_RANDOM','MATCH_WEIGHTED_RANDOM'].includes(value))
})
const recommendationOptions = computed<RecommendationOption[]>(() => {
  const allowed = new Set(batchAllowedRecommendationStrategies.value)
  const options: RecommendationOption[] = []
  if (allowed.has('BEST_MATCH') && hasFeature('P2_ROOM_RECOMMENDATION')) options.push({ value:'BEST_MATCH', label:'最匹配' })
  if (allowed.has('TRUE_RANDOM') && hasFeature('P1_RANDOM_RECOMMENDATION')) options.push({ value:'TRUE_RANDOM', label:'随机看看' })
  if (allowed.has('MATCH_WEIGHTED_RANDOM') && hasFeature('P2_ROOM_RECOMMENDATION')) options.push({ value:'MATCH_WEIGHTED_RANDOM', label:'按匹配度随机' })
  return options
})
const recommendationEnabled = computed(() => recommendationOptions.value.length > 0)
const recommendationButtonLabel = computed(() => recommendationOptions.value.find(item => item.value === recommendationStrategy.value)?.label ?? '帮我推荐一个')
const floorOptions = computed(() => [...new Set(rooms.value.map(room => Number(room.floor_number)))].filter(Number.isFinite).sort((a,b)=>a-b))
const sortedFilteredRooms = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rooms.value.filter(room => {
    if (isTeamMode.value && Number(room.availableCount) < memberCount) return false
    if (floorFilter.value && Number(room.floor_number) !== Number(floorFilter.value)) return false
    if (Number(room.availableCount) < minimumAvailableBeds.value) return false
    return !term || `${room.building_name} ${room.room_number}`.toLowerCase().includes(term)
  }).sort((left,right) => Number(right.matchScore??0)-Number(left.matchScore??0)
    || String(left.building_name??'').localeCompare(String(right.building_name??''),'zh-CN')
    || String(left.room_number??'').localeCompare(String(right.room_number??''),'zh-CN',{numeric:true}))
})
const roomPageSize = computed(() => ROOM_ROWS_PER_PAGE * roomColumnCount.value)
const totalRoomPages = computed(() => Math.max(1, Math.ceil(sortedFilteredRooms.value.length / roomPageSize.value)))
const pagedRooms = computed(() => sortedFilteredRooms.value.slice((roomPage.value-1)*roomPageSize.value, roomPage.value*roomPageSize.value))
const currentRoomStart = computed(() => sortedFilteredRooms.value.length ? (roomPage.value-1)*roomPageSize.value+1 : 0)
const currentRoomEnd = computed(() => Math.min(roomPage.value*roomPageSize.value, sortedFilteredRooms.value.length))

watch(recommendationStrategy,()=>{recommendationRequestId.value='';recommendationResult.value=null})
watch([keyword,floorFilter,minimumAvailableBeds],()=>{roomPage.value=1})
watch([sortedFilteredRooms,roomPageSize],()=>{roomPage.value=Math.min(roomPage.value,totalRoomPages.value)})
onMounted(()=>{updateRoomColumnCount();window.addEventListener('resize',updateRoomColumnCount);void initialize()})
onBeforeUnmount(()=>window.removeEventListener('resize',updateRoomColumnCount))

function updateRoomColumnCount(){const width=window.innerWidth;roomColumnCount.value=width>=1440?4:width>=900?3:width>=640?2:1;roomPage.value=Math.min(roomPage.value,totalRoomPages.value)}
async function initialize(){if(isTeamMode.value)return load();loading.value=true;try{const response=await api.get<ListSuccessResponse>('/api/v1/student/teams');const teams=(response.data.data??[]) as DataObject[];activePersonalTeam.value=teams.find(team=>Number(team.batch_id)===batchId)??null;if(activePersonalTeam.value){showPersonalExitConfirm.value=true;loading.value=false;return}await load()}catch(reason){error.value=translateError(reason);loading.value=false}}
async function confirmPersonalSelection(){preparingPersonalSelection.value=true;error.value='';try{await api.post(`/api/v1/student/batches/${batchId}/personal-selection/prepare`);showPersonalExitConfirm.value=false;activePersonalTeam.value=null;await load()}catch(reason){error.value=translateError(reason)}finally{preparingPersonalSelection.value=false}}
async function load(){loading.value=true;error.value='';try{const [roomResponse,readinessResponse]=await Promise.all([api.get<ListSuccessResponse>(`/api/v1/student/batches/${batchId}/rooms`),api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/selection-readiness`)]);rooms.value=(roomResponse.data.data??[]) as DataObject[];selectionReadiness.value=(readinessResponse.data.data??{}) as DataObject;synchronizeRecommendationStrategy()}catch(reason){error.value=translateError(reason)}finally{loading.value=false}}
function synchronizeRecommendationStrategy(){const options=recommendationOptions.value;if(!options.length)return;const configured=String(rooms.value[0]?.defaultRecommendationStrategy??'') as RecommendationStrategy;if(options.some(item=>item.value===configured)){recommendationStrategy.value=configured;return}if(!options.some(item=>item.value===recommendationStrategy.value))recommendationStrategy.value=options[0].value}
async function requestRecommendation(){if(!recommendationEnabled.value||recommending.value)return;error.value='';recommending.value=true;const clientRequestId=recommendationRequestId.value||crypto.randomUUID();recommendationRequestId.value=clientRequestId;try{const response=await api.post<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/recommendations`,{strategy:recommendationStrategy.value,clientRequestId});recommendationResult.value=(response.data.data??{}) as DataObject;recommendationRequestId.value=''}catch(reason){error.value=translateError(reason)}finally{recommending.value=false}}
function withPreferenceCheck(action:()=>void){if(Boolean(selectionReadiness.value.preferenceCompleted)){action();return}pendingPreferenceAction.value=action;preferencePromptVisible.value=true}
function continueWithoutPreference(){if(!Boolean(selectionReadiness.value.allowWithoutQuestionnaire)){void router.push('/student/preferences');return}const action=pendingPreferenceAction.value;preferencePromptVisible.value=false;pendingPreferenceAction.value=null;action?.()}
function requestRoomSelection(room:DataObject){withPreferenceCheck(()=>{roomSelectionTarget.value=room;selectionSuccess.value=null;error.value='';message.value=''})}
function closeRoomSelectionConfirm(){if(selectingRoomId.value!==null)return;roomSelectionTarget.value=null}
async function confirmRoomSelection(){const room=roomSelectionTarget.value;if(!room||selectingRoomId.value!==null)return;selectingRoomId.value=Number(room.id);error.value='';try{const response=isTeamMode.value?await api.post<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/rooms/${room.id}/select-team`,{teamId}):await api.post<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/rooms/${room.id}/select`);selectionSuccess.value=(response.data.data??{}) as DataObject;roomSelectionTarget.value=null;message.value='寝室选择已确认。';await load()}catch(reason){error.value=translateError(reason)}finally{selectingRoomId.value=null}}
function enterRoom(room:DataObject){if(isRoomMode.value){requestRoomSelection(room);return}withPreferenceCheck(()=>void router.push({path:`/student/batches/${batchId}/rooms/${room.id}`,query:isTeamMode.value?{teamId:String(teamId),memberCount:String(memberCount)}:{}}))}
function recommendationRoom(){const roomId=Number(recommendationResult.value?.roomId??0);return rooms.value.find(room=>Number(room.id)===roomId)??null}
function rankLabel(index:number){return index===0?'最匹配':index===1?'较匹配':index===2?'可参考':''}
function scoreText(room:DataObject){return `${Math.round(Number(room.matchScore??0))}`}
function highRecommendationWarning(room:DataObject){const score=Number(room.matchScore??0);const conflicts=(room.conflictReasons??[]) as unknown[];return score>=80&&conflicts.length>0?'匹配度较高，但仍存在需要沟通的偏好差异':''}
</script>

<template>
  <div class="content-column room-list-page">
    <div class="page-title split-title"><div><span class="eyebrow">{{ subtitle('选择寝室','ROOM SELECTION') }}</span><h2>{{ isTeamMode?'组队选择寝室':'选择寝室' }}</h2><p>匹配度根据当前学生或已确认队友的偏好计算，仅用于排序参考，不代表所有生活习惯完全一致。</p></div><button class="button ghost" @click="router.back()">返回</button></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <section v-if="recommendationEnabled" class="panel recommendation-panel"><div><span class="eyebrow">智能推荐</span><h3>快速找到更合适的寝室</h3><p>可按最高匹配度、完全随机或按匹配度加权随机推荐。</p></div><div class="recommendation-actions"><select v-model="recommendationStrategy" class="input"><option v-for="option in recommendationOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select><button class="button primary" :disabled="recommending" @click="withPreferenceCheck(requestRecommendation)">{{ recommending?'推荐中…':recommendationButtonLabel }}</button></div><article v-if="recommendationResult&&recommendationRoom()" class="recommendation-result"><div><strong>{{ recommendationRoom()?.building_name }} {{ recommendationRoom()?.room_number }}室</strong><span>匹配度 {{ scoreText(recommendationRoom()!) }}分 · 可用{{ recommendationRoom()?.availableCount }}床</span></div><button class="button secondary" @click="enterRoom(recommendationRoom()!)">查看并选择</button></article></section>
    <section class="panel room-filter-panel"><div class="room-filter-grid"><label><span>楼栋或寝室号</span><input v-model.trim="keyword" class="input" placeholder="输入关键词" /></label><label><span>楼层</span><select v-model="floorFilter" class="input"><option value="">全部楼层</option><option v-for="floor in floorOptions" :key="floor" :value="String(floor)">{{ floor }}层</option></select></label><label><span>最低剩余床位</span><input v-model.number="minimumAvailableBeds" class="input" type="number" min="0" max="8" /></label></div></section>
    <p v-if="loading" class="panel empty-state">正在同步可选寝室和匹配结果…</p>
    <section v-else class="room-card-grid"><article v-for="(room,index) in pagedRooms" :key="String(room.id)" class="panel room-selection-card"><header class="room-card-header"><div><span class="eyebrow">{{ room.building_name }} · {{ room.floor_number }}层</span><h3>{{ room.room_number }}室</h3><small v-if="rankLabel(index)">{{ rankLabel(index) }}</small></div><div class="score-ring-with-label"><strong>{{ scoreText(room) }}</strong><span>匹配度</span></div></header><div class="room-fact-row"><span>可用 {{ room.availableCount }} 床</span><span>在住 {{ room.activeResidentCount??0 }} 人</span><span>{{ isRoomMode?'选择寝室':'选择床位' }}</span></div><p v-if="Number(room.missingPreferenceCount??0)>0" class="room-warning">有 {{ room.missingPreferenceCount }} 名队友尚未完成个人偏好，当前匹配分仅供参考。</p><p v-if="highRecommendationWarning(room)" class="room-warning high-recommendation-warning">{{ highRecommendationWarning(room) }}</p><ul v-if="Array.isArray(room.conflictReasons)&&room.conflictReasons.length" class="conflict-reason-list"><li v-for="reason in room.conflictReasons" :key="String(reason)">{{ reason }}</li></ul><div v-if="Array.isArray(room.memberScores)&&room.memberScores.length" class="roommate-preference-list"><span v-for="member in room.memberScores" :key="String(member.studentId??member.studentNumber)">{{ member.studentName??member.studentNumber }}：{{ Math.round(Number(member.score??0)) }}分</span></div><p v-else class="roommate-preference-explanation">个人模式显示你的匹配度；组队模式会分别计算每名已确认队友的匹配分。</p><button class="button primary" :disabled="Number(room.availableCount)<(isTeamMode?memberCount:1)" @click="enterRoom(room)">{{ isRoomMode?'确认选择此寝室':'进入寝室选择床位' }}</button></article><p v-if="!pagedRooms.length" class="panel empty-state">没有符合当前筛选条件的寝室。</p></section>
    <div v-if="sortedFilteredRooms.length" class="room-pagination"><button class="button ghost small" :disabled="roomPage<=1" @click="roomPage--">上一页</button><span>第 {{ roomPage }}/{{ totalRoomPages }} 页 · 显示 {{ currentRoomStart }}-{{ currentRoomEnd }} / {{ sortedFilteredRooms.length }}</span><button class="button ghost small" :disabled="roomPage>=totalRoomPages" @click="roomPage++">下一页</button></div>
    <div v-if="roomSelectionTarget" class="modal-overlay room-selection-overlay" @click.self="closeRoomSelectionConfirm"><section class="modal-card room-selection-dialog"><span class="eyebrow">确认选择寝室</span><h3>{{ roomSelectionTarget.building_name }} {{ roomSelectionTarget.room_number }}室</h3><p>当前批次采用寝室选择模式，确认后具体床位待寝室成员协商或由管理员后续确认。</p><div class="button-row"><button class="button ghost" :disabled="selectingRoomId!==null" @click="closeRoomSelectionConfirm">取消</button><button class="button primary" :disabled="selectingRoomId!==null" @click="confirmRoomSelection">{{ selectingRoomId!==null?'正在确认…':'确认选择此寝室' }}</button></div></section></div>
    <AppModal :open="Boolean(selectionSuccess)" title="寝室选择成功" size="default" @close="selectionSuccess=null"><div v-if="selectionSuccess" class="selection-success-content"><strong>{{ selectionSuccess.buildingName }} {{ selectionSuccess.roomNumber }}室</strong><p>寝室已确定，具体床位待寝室成员协商或由管理员确认。</p></div><template #footer><button class="button primary" @click="router.push('/student')">返回学生首页</button></template></AppModal>
    <AppModal :open="preferencePromptVisible" title="个人偏好尚未完成" size="default" @close="preferencePromptVisible=false"><p>填写个人偏好后，系统才能提供更可靠的匹配度与推荐结果。</p><template #footer><button class="button ghost" @click="preferencePromptVisible=false">取消</button><button class="button secondary" @click="router.push('/student/preferences')">先填写偏好</button><button v-if="selectionReadiness.allowWithoutQuestionnaire" class="button primary" @click="continueWithoutPreference">仍然继续</button></template></AppModal>
    <ActionConfirmDialog :open="showPersonalExitConfirm" title="确认退出队伍并个人选寝" message="你当前已加入本批次队伍。继续后将退出队伍，并取消与该队伍相关的待处理邀请。" detail="退出完成后才能以个人身份选择寝室和床位。" confirm-text="退出队伍并继续" cancel-text="返回" danger :busy="preparingPersonalSelection" @cancel="router.back()" @confirm="confirmPersonalSelection" />
  </div>
</template>

<style scoped>
.room-filter-grid{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px}.room-filter-grid label{display:grid;gap:5px}.recommendation-panel{display:flex;align-items:center;justify-content:space-between;gap:18px}.recommendation-panel h3,.recommendation-panel p{margin:4px 0}.recommendation-actions{display:flex;gap:8px}.recommendation-result{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border-radius:12px;background:var(--surface-soft)}.recommendation-result div{display:grid;gap:3px}.room-card-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px}.room-selection-card{display:grid;align-content:start;gap:12px}.room-card-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.room-card-header h3{margin:4px 0}.score-ring-with-label{display:grid;place-items:center;width:68px;height:68px;border:5px solid color-mix(in srgb,var(--primary) 62%,#dce5f1);border-radius:50%;background:var(--surface)}.score-ring-with-label strong{font-size:20px;line-height:1}.score-ring-with-label span{font-size:10px;color:var(--text-muted)}.room-fact-row{display:flex;gap:6px;flex-wrap:wrap}.room-fact-row span,.roommate-preference-list span{padding:4px 7px;border-radius:999px;background:var(--surface-soft);font-size:11px}.room-warning{margin:0;padding:9px;border-radius:10px;background:#fff7ed;color:#9a4c0f;font-size:12px}.conflict-reason-list{display:grid;gap:4px;margin:0;padding-left:18px;color:#9a4c0f;font-size:12px}.roommate-preference-list{display:flex;gap:5px;flex-wrap:wrap}.roommate-preference-explanation{margin:0;color:var(--text-muted);font-size:12px}.room-pagination{display:flex;align-items:center;justify-content:center;gap:12px}.room-selection-dialog{width:min(520px,calc(100vw - 32px));padding:24px}.room-selection-dialog .button-row{justify-content:flex-end;margin-top:18px}.selection-success-content{display:grid;gap:8px}.selection-success-content strong{font-size:22px}@media(max-width:760px){.room-filter-grid{grid-template-columns:1fr}.recommendation-panel{align-items:stretch;flex-direction:column}.recommendation-actions{display:grid;grid-template-columns:1fr}.room-card-grid{grid-template-columns:1fr}}
</style>
