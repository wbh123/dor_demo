<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
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
const buildingFilter = ref('')
const floorFilter = ref('')
const roomKeyword = ref('')
const minimumAvailableBeds = ref(0)
const roomPage = ref(1)
const roomColumnCount = ref(4)
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
const buildingOptions = computed(() => [...new Set(rooms.value.map(room => String(room.building_name ?? '').trim()).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'zh-CN',{numeric:true})))
const floorOptions = computed(() => [...new Set(rooms.value.filter(room=>!buildingFilter.value||String(room.building_name??'')===buildingFilter.value).map(room=>Number(room.floor_number)))].filter(Number.isFinite).sort((a,b)=>a-b))
const minimumAvailableBedOptions = computed(() => {const maximum=Math.max(0,...rooms.value.map(room=>Number(room.availableCount??0)).filter(Number.isFinite));return Array.from({length:maximum},(_,index)=>index+1)})
const roomNumberSuggestions = computed(() => {const term=roomKeyword.value.trim().toLowerCase();return [...new Set(rooms.value.filter(room=>!buildingFilter.value||String(room.building_name??'')===buildingFilter.value).filter(room=>!floorFilter.value||Number(room.floor_number)===Number(floorFilter.value)).map(room=>String(room.room_number??'').trim()).filter(Boolean))].filter(value=>!term||value.toLowerCase().includes(term)).sort((a,b)=>a.localeCompare(b,'zh-CN',{numeric:true})).slice(0,50)})
const sortedFilteredRooms = computed(() => {const term=roomKeyword.value.trim().toLowerCase();return rooms.value.filter(room=>{if(isTeamMode.value&&Number(room.availableCount)<memberCount)return false;if(buildingFilter.value&&String(room.building_name??'')!==buildingFilter.value)return false;if(floorFilter.value&&Number(room.floor_number)!==Number(floorFilter.value))return false;if(Number(room.availableCount)<minimumAvailableBeds.value)return false;return !term||String(room.room_number??'').toLowerCase().includes(term)}).sort((a,b)=>Number(b.matchScore??0)-Number(a.matchScore??0)||String(a.building_name??'').localeCompare(String(b.building_name??''),'zh-CN')||String(a.room_number??'').localeCompare(String(b.room_number??''),'zh-CN',{numeric:true}))})
const roomPageSize = computed(() => ROOM_ROWS_PER_PAGE * roomColumnCount.value)
const totalRoomPages = computed(() => Math.max(1, Math.ceil(sortedFilteredRooms.value.length / roomPageSize.value)))
const pagedRooms = computed(() => sortedFilteredRooms.value.slice((roomPage.value-1)*roomPageSize.value,roomPage.value*roomPageSize.value))
const currentRoomStart = computed(() => sortedFilteredRooms.value.length?(roomPage.value-1)*roomPageSize.value+1:0)
const currentRoomEnd = computed(() => Math.min(roomPage.value*roomPageSize.value,sortedFilteredRooms.value.length))

watch(recommendationStrategy,()=>{recommendationRequestId.value='';recommendationResult.value=null})
watch(buildingFilter,()=>{if(floorFilter.value&&!floorOptions.value.includes(Number(floorFilter.value)))floorFilter.value=''})
watch([buildingFilter,floorFilter,roomKeyword,minimumAvailableBeds],()=>{roomPage.value=1})
watch([sortedFilteredRooms,roomPageSize],()=>{roomPage.value=Math.min(roomPage.value,totalRoomPages.value)})
onMounted(()=>{updateRoomColumnCount();window.addEventListener('resize',updateRoomColumnCount);void initialize()})
onBeforeUnmount(()=>window.removeEventListener('resize',updateRoomColumnCount))

function updateRoomColumnCount(){const width=window.innerWidth;roomColumnCount.value=width>=1500?5:width>=1180?4:width>=760?3:width>=560?2:1;roomPage.value=Math.min(roomPage.value,totalRoomPages.value)}
async function initialize(){await load()}
async function confirmPersonalSelection(){preparingPersonalSelection.value=true;error.value='';try{await api.post(`/api/v1/student/batches/${batchId}/personal-selection/prepare`);showPersonalExitConfirm.value=false;activePersonalTeam.value=null;await load()}catch(reason){error.value=translateError(reason)}finally{preparingPersonalSelection.value=false}}
async function load(){loading.value=true;error.value='';try{const query=teamId?`?teamId=${encodeURIComponent(String(teamId))}`:'';const response=await api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/selection/bootstrap${query}`);const bootstrap=(response.data.data??{}) as DataObject;activePersonalTeam.value=(bootstrap.activePersonalTeam??null) as DataObject|null;showPersonalExitConfirm.value=!isTeamMode.value&&Boolean(bootstrap.requiresPersonalTeamExit);if(showPersonalExitConfirm.value){rooms.value=[];selectionReadiness.value={preferenceCompleted:false,allowWithoutQuestionnaire:false};return}rooms.value=Array.isArray(bootstrap.rooms)?bootstrap.rooms as DataObject[]:[];selectionReadiness.value=(bootstrap.selectionReadiness??{}) as DataObject;synchronizeRecommendationStrategy()}catch(reason){error.value=translateError(reason)}finally{loading.value=false}}
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
function roomTags(room:DataObject){const tags:string[]=[];for(const value of (room.recommendationReasons??[]) as unknown[]){const text=String(value??'').trim();if(text)tags.push(text)}for(const value of (room.matches??[]) as unknown[]){const text=String(value??'').trim();if(text)tags.push(text)}if(Number(room.availableLoftBedCount??0)>0)tags.push('上床下桌');if(Number(room.availableBunkUpperCount??0)>0||Number(room.availableBunkLowerCount??0)>0)tags.push('上下铺');if(Number(room.availableCount??0)>=4)tags.push('余位充足');return [...new Set(tags)].slice(0,5)}
</script>

<template>
  <div class="content-column room-list-page">
    <div class="page-title split-title"><div><span class="eyebrow">{{ subtitle('选择寝室','ROOM SELECTION') }}</span><h2>{{ isTeamMode?'组队选择寝室':'选择寝室' }}</h2><p>匹配度根据当前学生或已确认队友的偏好计算，仅用于排序参考，不代表所有生活习惯完全一致。</p></div><button class="button ghost" @click="router.back()">返回</button></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="recommendationEnabled" class="panel recommendation-panel">
      <div><span class="eyebrow">智能推荐</span><h3>快速找到更合适的寝室</h3><p>主按钮直接执行，右侧下拉可随时切换推荐策略。</p></div>
      <div class="recommendation-combo" aria-label="快速匹配与推荐策略"><button class="recommendation-main-button" :disabled="recommending" @click="withPreferenceCheck(requestRecommendation)">{{ recommending?'推荐中…':'快速匹配' }}</button><select v-model="recommendationStrategy" class="recommendation-strategy-select" aria-label="推荐策略"><option v-for="option in recommendationOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></div>
      <article v-if="recommendationResult&&recommendationRoom()" class="recommendation-result"><div><strong>{{ recommendationRoom()?.building_name }} {{ recommendationRoom()?.room_number }}室</strong><span>匹配度 {{ scoreText(recommendationRoom()!) }}分 · 可用{{ recommendationRoom()?.availableCount }}床</span></div><button class="button secondary" @click="enterRoom(recommendationRoom()!)">查看并选择</button></article>
    </section>

    <section class="panel room-filter-panel"><div class="room-filter-grid"><label><span>楼栋</span><select v-model="buildingFilter" class="input"><option value="">全部楼栋</option><option v-for="building in buildingOptions" :key="building" :value="building">{{ building }}</option></select></label><label><span>楼层</span><select v-model="floorFilter" class="input"><option value="">全部楼层</option><option v-for="floor in floorOptions" :key="floor" :value="String(floor)">{{ floor }}层</option></select></label><label><span>寝室号</span><input v-model.trim="roomKeyword" class="input" type="search" list="room-number-options" placeholder="输入寝室号关键词" autocomplete="off" /><datalist id="room-number-options"><option v-for="roomNumber in roomNumberSuggestions" :key="roomNumber" :value="roomNumber" /></datalist></label><label><span>最低剩余床位</span><select v-model.number="minimumAvailableBeds" class="input"><option :value="0">不限</option><option v-for="count in minimumAvailableBedOptions" :key="count" :value="count">至少 {{ count }} 床</option></select></label></div></section>

    <p v-if="loading" class="panel empty-state">正在同步可选寝室和匹配结果…</p>
    <section v-else class="room-card-grid">
      <article v-for="(room,index) in pagedRooms" :key="String(room.id)" class="panel room-selection-card" :class="{ 'room-card-compact': isRoomMode }">
        <header class="room-card-header"><div><span class="eyebrow">{{ room.building_name }} · {{ room.floor_number }}层</span><h3>{{ room.room_number }}室</h3><small v-if="rankLabel(index)">{{ rankLabel(index) }}</small></div><div class="score-ring-with-label"><strong>{{ scoreText(room) }}</strong><span>匹配度</span></div></header>
        <div v-if="roomTags(room).length" class="room-tag-cloud"><span v-for="tag in roomTags(room)" :key="tag">{{ tag }}</span></div>
        <div class="room-fact-row"><span>可用 {{ room.availableCount }} 床</span><span v-if="isTeamMode">队伍需 {{ memberCount }} 床</span></div>
        <p v-if="Number(room.missingPreferenceCount??0)>0" class="room-warning">有 {{ room.missingPreferenceCount }} 名队友尚未完成个人偏好，当前匹配分仅供参考。</p>
        <p v-if="highRecommendationWarning(room)" class="room-warning high-recommendation-warning">{{ highRecommendationWarning(room) }}</p>
        <ul v-if="Array.isArray(room.conflictReasons)&&room.conflictReasons.length" class="conflict-reason-list"><li v-for="reason in room.conflictReasons" :key="String(reason)">{{ reason }}</li></ul>
        <div v-if="Array.isArray(room.memberScores)&&room.memberScores.length" class="roommate-preference-list"><span v-for="member in room.memberScores" :key="String(member.studentId??member.studentNumber)">{{ member.studentName??member.studentNumber }}：{{ Math.round(Number(member.score??0)) }}分</span></div>
        <button class="button primary" :disabled="Number(room.availableCount)<(isTeamMode?memberCount:1)" @click="enterRoom(room)">{{ isRoomMode?'确认选择此寝室':'进入寝室选择床位' }}</button>
      </article><p v-if="!pagedRooms.length" class="panel empty-state">没有符合当前筛选条件的寝室。</p>
    </section>

    <div v-if="sortedFilteredRooms.length" class="room-pagination"><button class="button ghost small" :disabled="roomPage<=1" @click="roomPage--">上一页</button><span>第 {{ roomPage }}/{{ totalRoomPages }} 页 · {{ currentRoomStart }}-{{ currentRoomEnd }} / {{ sortedFilteredRooms.length }}</span><button class="button ghost small" :disabled="roomPage>=totalRoomPages" @click="roomPage++">下一页</button></div>

    <div v-if="roomSelectionTarget" class="modal-overlay room-selection-overlay" @click.self="closeRoomSelectionConfirm"><section class="modal-card room-selection-dialog"><span class="eyebrow">确认选择寝室</span><h3>{{ roomSelectionTarget.building_name }} {{ roomSelectionTarget.room_number }}室</h3><p>当前批次采用寝室选择模式，确认后具体床位待寝室成员协商或由管理员后续确认。</p><div class="button-row"><button class="button ghost" :disabled="selectingRoomId!==null" @click="closeRoomSelectionConfirm">取消</button><button class="button primary" :disabled="selectingRoomId!==null" @click="confirmRoomSelection">{{ selectingRoomId!==null?'正在确认…':'确认选择此寝室' }}</button></div></section></div>
    <AppModal :open="Boolean(selectionSuccess)" title="寝室选择成功" size="default" @close="selectionSuccess=null"><div v-if="selectionSuccess" class="selection-success-content"><strong>{{ selectionSuccess.buildingName }} {{ selectionSuccess.roomNumber }}室</strong><p>寝室已确定，具体床位待寝室成员协商或由管理员确认。</p></div><template #footer><button class="button primary" @click="router.push('/student')">返回学生首页</button></template></AppModal>
    <AppModal :open="preferencePromptVisible" title="个人偏好尚未完成" size="default" @close="preferencePromptVisible=false"><p>填写个人偏好后，系统才能提供更可靠的匹配度与推荐结果。</p><template #footer><button class="button ghost" @click="preferencePromptVisible=false">取消</button><button class="button secondary" @click="router.push('/student/preferences')">先填写偏好</button><button v-if="selectionReadiness.allowWithoutQuestionnaire" class="button primary" @click="continueWithoutPreference">仍然继续</button></template></AppModal>
    <ActionConfirmDialog :open="showPersonalExitConfirm" title="确认退出队伍并个人选寝" message="你当前已加入本批次队伍。继续后将退出队伍，并取消与该队伍相关的待处理邀请。" detail="退出完成后才能以个人身份选择寝室和床位。" confirm-text="退出队伍并继续" cancel-text="返回" danger :busy="preparingPersonalSelection" @cancel="router.back()" @confirm="confirmPersonalSelection" />
  </div>
</template>

<style scoped>
.room-filter-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.room-filter-grid label{display:grid;align-content:start;gap:5px}.recommendation-panel{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:18px}.recommendation-panel h3,.recommendation-panel p{margin:4px 0}.recommendation-combo{display:flex;align-items:stretch;min-height:44px;border-radius:12px;overflow:hidden;box-shadow:0 7px 18px color-mix(in srgb,var(--primary) 15%,transparent)}.recommendation-main-button{min-width:126px;padding:0 18px;border:0;color:#fff;background:var(--primary);font-weight:800;cursor:pointer}.recommendation-main-button:disabled{opacity:.6;cursor:wait}.recommendation-strategy-select{min-width:138px;padding:0 34px 0 12px;border:0;border-left:1px solid color-mix(in srgb,#fff 30%,transparent);outline:0;color:#fff;background:color-mix(in srgb,var(--primary) 86%,#000);font-weight:700;cursor:pointer}.recommendation-strategy-select option{color:#172033;background:#fff}.recommendation-result{grid-column:1/-1;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border-radius:12px;background:var(--surface-soft)}.recommendation-result div{display:grid;gap:3px}.room-card-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:14px;align-items:stretch}.room-selection-card{display:grid;align-content:start;gap:12px;min-height:350px;padding:18px}.room-selection-card>.button{margin-top:auto}.room-card-compact{min-height:330px;padding:17px;gap:11px}.room-card-compact .score-ring-with-label{width:58px;height:58px}.room-card-header{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.room-card-header h3{margin:4px 0}.score-ring-with-label{display:grid;place-items:center;width:64px;height:64px;border:5px solid color-mix(in srgb,var(--primary) 62%,#dce5f1);border-radius:50%;background:var(--surface)}.score-ring-with-label strong{font-size:20px;line-height:1}.score-ring-with-label span{font-size:10px;color:var(--text-muted)}.room-tag-cloud{display:flex;gap:6px;flex-wrap:wrap}.room-tag-cloud span{max-width:100%;padding:5px 8px;border:1px solid color-mix(in srgb,var(--primary) 28%,var(--line));border-radius:999px;color:var(--primary);background:color-mix(in srgb,var(--primary) 7%,var(--panel));font-size:11px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.room-fact-row{display:flex;gap:6px;flex-wrap:wrap}.room-fact-row span,.roommate-preference-list span{padding:4px 7px;border-radius:999px;background:var(--surface-soft);font-size:11px}.room-warning{margin:0;padding:9px;border-radius:10px;background:#fff7ed;color:#9a4c0f;font-size:12px}.conflict-reason-list{display:grid;gap:4px;margin:0;padding-left:18px;color:#9a4c0f;font-size:12px}.roommate-preference-list{display:flex;gap:5px;flex-wrap:wrap}.room-pagination{display:flex;align-items:center;justify-content:center;gap:12px;padding:10px 14px;border:1px solid var(--line);border-radius:14px;background:var(--panel);box-shadow:0 5px 16px rgba(15,23,42,.04)}.room-selection-dialog{width:min(520px,calc(100vw - 32px));padding:24px}.room-selection-dialog .button-row{justify-content:flex-end;margin-top:18px}.selection-success-content{display:grid;gap:8px}.selection-success-content strong{font-size:22px}@media(max-width:980px){.room-filter-grid{grid-template-columns:repeat(2,minmax(0,1fr));grid-column:1/-1}.recommendation-panel{grid-template-columns:1fr}.recommendation-combo{width:max-content}}@media(max-width:760px){.room-filter-grid{grid-template-columns:1fr}.recommendation-combo{width:100%}.recommendation-main-button,.recommendation-strategy-select{flex:1;min-width:0}.room-card-grid{grid-template-columns:repeat(auto-fill,minmax(210px,1fr))}}
</style>