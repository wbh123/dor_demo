<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppModal from '../../components/modal/AppModal.vue'
import RemoteEntitySelect, { type EntityOption } from '../../components/common/RemoteEntitySelect.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

type Strategy = 'CHANGE' | 'EXCHANGE'
const activeStrategy = ref<Strategy>('CHANGE')
const policy = ref<DataObject>({ mode:'DISABLED', enabled:false, requiresApproval:false })
const exchangePolicy = ref<DataObject>({ mode:'DISABLED', enabled:false, requiresApproval:false })
const candidates = ref<DataObject[]>([])
const requests = ref<DataObject[]>([])
const profile = ref<DataObject>({})
const exchangeCandidates = ref<DataObject[]>([])
const exchangeRequests = ref<DataObject[]>([])
const buildingOptions = ref<EntityOption[]>([])
const target = ref<DataObject|null>(null)
const exchangeTarget = ref<DataObject|null>(null)
const submitting = ref(false)
const loading = ref(true)
const exchangeSearching = ref(false)
const error = ref('')
const message = ref('')
const candidateKeyword = ref('')
const form = reactive({ reason:'' })
const exchangeForm = reactive({ reason:'' })
const exchangeQuery = reactive({ studentNumber:'', studentName:'', buildingId:null as string|number|null, roomNumber:'' })
const { subtitle, translateError } = useI18n()

const modeText = computed(() => ({DISABLED:'未开放',FREE:'自由换寝',APPROVAL_REQUIRED:'管理员审批'} as Record<string,string>)[String(policy.value.mode)]??'未开放')
const exchangeModeText = computed(() => ({DISABLED:'未开放',ENABLED:'已开放',MUTUAL_CONFIRMATION:'双方确认后直接交换',APPROVAL_REQUIRED:'双方确认后由管理员审批'} as Record<string,string>)[String(exchangePolicy.value.mode)]??'已开放')
const filteredCandidates = computed(() => {
  const term=candidateKeyword.value.trim().toLowerCase()
  return candidates.value.filter(room=>!term||`${room.building_name??''} ${room.room_number??''}`.toLowerCase().includes(term)).sort((a,b)=>`${a.building_name}${a.room_number}`.localeCompare(`${b.building_name}${b.room_number}`,'zh-CN',{numeric:true}))
})
const incomingExchanges = computed(() => exchangeRequests.value.filter(item=>String(item.target_student_number)===String(profile.value.student_number)&&String(item.request_status)==='WAITING_TARGET'))

onMounted(load)
async function load(){
  loading.value=true;error.value=''
  try{
    const [profileResponse,policyResponse,historyResponse,exchangeHistoryResponse,buildingResponse]=await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),api.get<ObjectSuccessResponse>('/api/v1/student/room-change/policy'),api.get<ListSuccessResponse>('/api/v1/student/room-change/requests'),api.get<ListSuccessResponse>('/api/v1/student/room-exchanges'),api.get<ListSuccessResponse>('/api/v1/student/room-exchanges/candidate-buildings'),
    ])
    profile.value=(profileResponse.data.data??{}) as DataObject
    policy.value=(policyResponse.data.data??{}) as DataObject
    requests.value=(historyResponse.data.data??[]) as DataObject[]
    exchangeRequests.value=(exchangeHistoryResponse.data.data??[]) as DataObject[]
    buildingOptions.value=((buildingResponse.data.data??[]) as DataObject[]).map(item=>({value:Number(item.value),label:String(item.label)}))
    if(Boolean(policy.value.enabled)){const response=await api.get<ListSuccessResponse>('/api/v1/student/room-change/candidates');candidates.value=(response.data.data??[]) as DataObject[]}else candidates.value=[]
    const latestMode=String(exchangeRequests.value[0]?.policy_mode??'ENABLED')
    exchangePolicy.value={mode:latestMode,enabled:latestMode!=='DISABLED',requiresApproval:latestMode==='APPROVAL_REQUIRED'}
  }catch(reason){error.value=translateError(reason)}finally{loading.value=false}
}
async function searchExchangeCandidates(){
  if(!/^\d{12}$/.test(exchangeQuery.studentNumber.trim())||!exchangeQuery.studentName.trim()||!exchangeQuery.buildingId||!exchangeQuery.roomNumber.trim()){error.value='请完整填写12位学号、姓名、楼栋和寝室号。';return}
  exchangeSearching.value=true;error.value='';exchangeCandidates.value=[]
  try{
    const response=await api.get<ObjectSuccessResponse>('/api/v1/student/room-exchanges/exact-candidate',{params:{studentNumber:exchangeQuery.studentNumber.trim(),studentName:exchangeQuery.studentName.trim(),buildingId:Number(exchangeQuery.buildingId),roomNumber:exchangeQuery.roomNumber.trim()}})
    exchangeCandidates.value=[(response.data.data??{}) as DataObject]
  }catch(reason){error.value=translateError(reason)}finally{exchangeSearching.value=false}
}
function requestChange(room:DataObject){target.value=room;form.reason='';error.value='';message.value=''}
async function submit(){if(!target.value||submitting.value)return;submitting.value=true;error.value='';try{const response=await api.post<ObjectSuccessResponse>('/api/v1/student/room-change/requests',{targetRoomId:Number(target.value.id),reason:form.reason.trim()});const result=(response.data.data??{}) as DataObject;message.value=String(result.request_status)==='EXECUTED'?'换寝已执行，当前住宿信息已更新。':'换寝申请已提交，请等待管理员审批。';target.value=null;await load()}catch(reason){error.value=translateError(reason)}finally{submitting.value=false}}
async function cancelRequest(item:DataObject){try{await api.post(`/api/v1/student/room-change/requests/${item.id}/cancel`,{reason:'学生主动取消待审核换寝申请'});message.value='换寝申请已取消。';await load()}catch(cause){error.value=translateError(cause)}}
function openExchange(candidate:DataObject){exchangeTarget.value=candidate;exchangeForm.reason='';error.value='';message.value=''}
async function submitExchange(){if(!exchangeTarget.value||submitting.value)return;submitting.value=true;error.value='';try{await api.post('/api/v1/student/room-exchanges',{targetStudentId:Number(exchangeTarget.value.target_student_id),reason:exchangeForm.reason.trim()});exchangeTarget.value=null;message.value='交换邀请已发送，需对方学生确认后继续。';await load()}catch(cause){error.value=translateError(cause)}finally{submitting.value=false}}
async function respondExchange(item:DataObject,accepted:boolean){const reason=window.prompt(accepted?'请填写双方已达成一致的说明':'请填写拒绝原因',accepted?'双方已线下沟通并确认交换':'暂不同意本次交换');if(!reason?.trim())return;submitting.value=true;try{const response=await api.post<ObjectSuccessResponse>(`/api/v1/student/room-exchanges/${item.id}/respond`,{accepted,reason:reason.trim()});const result=(response.data.data??{}) as DataObject;message.value=!accepted?'已拒绝本次交换邀请。':String(result.request_status)==='EXECUTED'?'双方寝室床位已完成交换。':'已接受交换，申请已转交管理员审批。';await load()}catch(cause){error.value=translateError(cause)}finally{submitting.value=false}}
async function cancelExchange(item:DataObject){const reason=window.prompt('请填写取消交换的原因','双方计划有变，取消本次交换');if(!reason?.trim())return;try{await api.post(`/api/v1/student/room-exchanges/${item.id}/cancel`,{reason:reason.trim()});message.value='寝室交换申请已取消。';await load()}catch(cause){error.value=translateError(cause)}}
function statusText(value:unknown){return({PENDING:'待审批',APPROVED:'已批准',REJECTED:'已驳回',EXECUTED:'已完成',CANCELLED:'已取消',FAILED:'执行失败',WAITING_TARGET:'等待对方确认',PENDING_ADMIN:'等待管理员审批'} as Record<string,string>)[String(value)]??String(value)}
</script>

<template>
  <div class="content-column room-exchange-page">
    <header class="page-title split-title"><div><span class="eyebrow">{{ subtitle('住宿调整','ROOM EXCHANGE') }}</span><h2>寝室交换</h2><p>双方需自主联系并在线下商议，达成一致后再通过系统发起和确认操作。</p></div><button class="button secondary" @click="load">刷新</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <div class="exchange-strategy-grid"><button class="exchange-strategy-card" :class="{active:activeStrategy==='CHANGE'}" type="button" @click="activeStrategy='CHANGE'"><span>策略一</span><strong>空余资源换寝</strong><small>{{ modeText }} · 迁入有空位的寝室</small></button><button class="exchange-strategy-card" :class="{active:activeStrategy==='EXCHANGE'}" type="button" @click="activeStrategy='EXCHANGE'"><span>策略二</span><strong>双方寝室交换</strong><small>{{ exchangeModeText }} · 需双方线下沟通</small></button></div>

    <template v-if="activeStrategy==='CHANGE'">
      <section v-if="policy.enabled" class="panel"><div class="section-head"><div><span class="eyebrow">空余资源换寝</span><h3>符合条件的可用寝室</h3></div><input v-model="candidateKeyword" class="input compact-search" placeholder="输入楼栋或寝室号" /></div><p v-if="loading" class="empty-state">正在加载…</p><div v-else-if="filteredCandidates.length" class="change-room-grid"><article v-for="room in filteredCandidates" :key="String(room.id)" class="change-room-card"><div><span class="eyebrow">{{ room.building_name }}</span><h3>{{ room.room_number }}室</h3><p>{{ room.floor_number }}层 · 剩余{{ room.available_count }}个名额</p></div><button class="button primary" @click="requestChange(room)">选择此寝室</button></article></div><p v-else class="empty-state">当前没有符合条件的空余寝室。</p></section><section v-else class="panel empty-state"><h3>学校当前未开放空余资源换寝</h3></section>
      <section class="panel"><div class="section-head"><h3>我的换寝记录</h3></div><div class="table-wrap"><table><thead><tr><th>申请时间</th><th>原寝室</th><th>目标寝室</th><th>状态</th><th>原因/意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in requests" :key="String(item.id)"><td>{{ item.created_at }}</td><td>{{ item.source_building_name }} {{ item.source_room_number }}</td><td>{{ item.target_building_name }} {{ item.target_room_number }}</td><td><span class="status-pill">{{ statusText(item.request_status) }}</span></td><td>{{ item.review_reason||item.reason }}</td><td><button v-if="item.request_status==='PENDING'" class="button ghost small" @click="cancelRequest(item)">取消申请</button></td></tr></tbody></table></div></section>
    </template>

    <template v-else>
      <section v-if="exchangePolicy.enabled" class="panel"><div class="section-head"><div><span class="eyebrow">隐私保护精确查询</span><h3>通过双方已知信息查找交换对象</h3><p>必须同时精确匹配学号、姓名、楼栋和寝室号。系统不会列出或模糊搜索其他学生。</p></div></div><form class="exact-exchange-form" @submit.prevent="searchExchangeCandidates"><label><span>12位学号</span><input v-model.trim="exchangeQuery.studentNumber" class="input" maxlength="12" inputmode="numeric" required /></label><label><span>姓名</span><input v-model.trim="exchangeQuery.studentName" class="input" maxlength="128" required /></label><label><span>楼栋</span><RemoteEntitySelect v-model="exchangeQuery.buildingId" :options="buildingOptions" placeholder="请选择楼栋" search-placeholder="搜索楼栋" /></label><label><span>寝室号</span><input v-model.trim="exchangeQuery.roomNumber" class="input" maxlength="32" required /></label><button class="button secondary" :disabled="exchangeSearching">{{ exchangeSearching?'正在精确匹配…':'精确查询' }}</button></form><article v-for="candidate in exchangeCandidates" :key="String(candidate.target_student_id)" class="exchange-match-card"><div><strong>{{ candidate.student_name }}</strong><span>{{ candidate.student_number }}</span><small>{{ candidate.building_name }} {{ candidate.room_number }}室 · {{ candidate.bed_code||'床位待确认' }}</small></div><button class="button primary" @click="openExchange(candidate)">发起寝室交换</button></article></section><section v-else class="panel empty-state"><h3>学校当前未开放寝室交换</h3></section>
      <section v-if="incomingExchanges.length" class="panel incoming-panel"><div class="section-head"><h3>收到的寝室交换邀请</h3></div><article v-for="item in incomingExchanges" :key="String(item.id)" class="incoming-exchange-card"><div><strong>{{ item.initiator_student_name }}（{{ item.initiator_student_number }}）</strong><p>希望用 {{ item.initiator_building_name }} {{ item.initiator_room_number }}室 {{ item.initiator_bed_code||'未确认床位' }} 与你交换。</p><small>原因：{{ item.reason }}</small></div><div class="button-row"><button class="button primary" :disabled="submitting" @click="respondExchange(item,true)">接受交换</button><button class="button ghost" :disabled="submitting" @click="respondExchange(item,false)">拒绝</button></div></article></section>
      <section class="panel"><div class="section-head"><h3>寝室交换记录</h3></div><div class="table-wrap"><table><thead><tr><th>对方学生</th><th>交换方向</th><th>状态</th><th>申请原因</th><th>处理意见</th><th>操作</th></tr></thead><tbody><tr v-for="item in exchangeRequests" :key="String(item.id)"><td>{{ item.initiator_student_number===profile.student_number?item.target_student_name:item.initiator_student_name }}</td><td>{{ item.initiator_room_number }} ↔ {{ item.target_room_number }}</td><td><span class="status-pill">{{ statusText(item.request_status) }}</span></td><td>{{ item.reason }}</td><td>{{ item.review_reason||item.target_response_reason||'-' }}</td><td><button v-if="item.initiator_student_number===profile.student_number&&['WAITING_TARGET','PENDING_ADMIN'].includes(String(item.request_status))" class="button ghost small" @click="cancelExchange(item)">取消交换</button></td></tr></tbody></table></div></section>
    </template>

    <AppModal :open="Boolean(target)" title="确认换寝" size="default" :busy="submitting" @close="!submitting&&(target=null)"><div v-if="target" class="dialog-content"><strong>{{ target.building_name }} {{ target.room_number }}室</strong><p>{{ policy.requiresApproval?'提交后由管理员审批。':'确认后将立即更新住宿记录。' }}</p><label><span>换寝原因</span><textarea v-model.trim="form.reason" class="input" rows="5" maxlength="500" required /></label></div><template #footer><button class="button ghost" @click="target=null">取消</button><button class="button primary" :disabled="form.reason.trim().length<2||submitting" @click="submit">{{ submitting?'正在提交…':'确认提交' }}</button></template></AppModal>
    <AppModal :open="Boolean(exchangeTarget)" title="发起寝室交换" size="default" :busy="submitting" @close="!submitting&&(exchangeTarget=null)"><div v-if="exchangeTarget" class="dialog-content"><strong>邀请 {{ exchangeTarget.student_name }}</strong><p>请确认双方已线下沟通：{{ exchangeTarget.building_name }} {{ exchangeTarget.room_number }}室 {{ exchangeTarget.bed_code||'床位待确认' }}</p><label><span>交换原因与双方意向说明</span><textarea v-model.trim="exchangeForm.reason" class="input" rows="5" maxlength="500" required /></label></div><template #footer><button class="button ghost" @click="exchangeTarget=null">取消</button><button class="button primary" :disabled="exchangeForm.reason.trim().length<2||submitting" @click="submitExchange">{{ submitting?'正在发送…':'发送交换邀请' }}</button></template></AppModal>
  </div>
</template>

<style scoped>
.exchange-strategy-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.exchange-strategy-card{display:grid;gap:6px;border:1px solid var(--line);border-radius:17px;padding:18px;background:var(--panel);color:inherit;text-align:left;cursor:pointer}.exchange-strategy-card>span{color:var(--primary);font-size:12px;font-weight:800}.exchange-strategy-card small{color:var(--muted)}.exchange-strategy-card.active{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 12%,transparent)}.compact-search{max-width:280px}.change-room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}.change-room-card,.exchange-match-card,.incoming-exchange-card{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px;border:1px solid var(--line);border-radius:15px;background:var(--soft)}.change-room-card h3,.change-room-card p{margin:4px 0}.exact-exchange-form{display:grid;grid-template-columns:1fr 1fr 1fr 1fr auto;align-items:end;gap:10px}.exact-exchange-form label,.dialog-content label{display:grid;gap:6px}.exchange-match-card{margin-top:16px}.exchange-match-card>div{display:grid;gap:4px}.exchange-match-card span,.exchange-match-card small{color:var(--muted)}.incoming-panel{border-color:#8fb8ef;background:#f7fbff}.incoming-exchange-card p{margin:5px 0}.incoming-exchange-card small{color:var(--muted)}.status-pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#eef4ff;color:#315f9d;font-size:12px}.dialog-content{display:grid;gap:12px}@media(max-width:1000px){.exact-exchange-form{grid-template-columns:1fr 1fr}.exact-exchange-form .button{grid-column:1/-1}}@media(max-width:720px){.exchange-strategy-grid,.exact-exchange-form{grid-template-columns:1fr}.exact-exchange-form .button{grid-column:auto}.change-room-card,.exchange-match-card,.incoming-exchange-card{align-items:flex-start;flex-direction:column}}
</style>
