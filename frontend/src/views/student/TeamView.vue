<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const router = useRouter()
const teams = ref<DataObject[]>([])
const invitations = ref<DataObject[]>([])
const inviteStudentNumber = ref('')
const error = ref('')
const message = ref('')
const loading = ref(true)
const submitting = ref(false)
const assigned = ref(false)
const showStartSelectionConfirm = ref<DataObject | null>(null)
const showLeaveConfirm = ref<DataObject | null>(null)
const removeCandidate = ref<{ team: DataObject; member: DataObject } | null>(null)
const { t, subtitle, translateError } = useI18n()

const currentTeam = computed(() => teams.value[0] ?? null)
const currentMembers = computed(() => ((currentTeam.value?.members ?? []) as DataObject[]))
const canInvite = computed(() => {
  if (assigned.value) return false
  if (!currentTeam.value) return true
  return currentTeam.value.member_role === 'LEADER'
    && currentTeam.value.team_status === 'FORMING'
    && currentMembers.value.length < 5
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [teamResponse, invitationResponse, residencyResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/student/teams'),
      api.get<ListSuccessResponse>('/api/v1/student/team-invitations'),
      api.get<ObjectSuccessResponse>('/api/v1/student/residency'),
    ])
    teams.value = (teamResponse.data.data ?? []) as DataObject[]
    assigned.value = Boolean((residencyResponse.data.data as DataObject | undefined)?.resident)
    invitations.value = assigned.value ? [] : (invitationResponse.data.data ?? []) as DataObject[]
  } catch (reason) { error.value = translateError(reason) }
  finally { loading.value = false }
}

async function invite() {
  const studentNumber = inviteStudentNumber.value.trim()
  if (!/^\d{12}$/.test(studentNumber) || submitting.value) return
  submitting.value = true; error.value = ''; message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/student/team-invitations', { studentNumber })
    const invited = (response.data.data ?? {}) as DataObject
    inviteStudentNumber.value = ''
    message.value = `已向 ${String(invited.studentName)} 发送邀请。首次邀请已自动建立队伍。`
    await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}

async function respond(token: unknown, accepted: boolean) {
  try { await api.post('/api/v1/student/team-invitations/respond', { invitationToken: String(token), accepted }); message.value = accepted ? '已加入小组。' : '已拒绝邀请。'; await load() }
  catch (reason) { error.value = translateError(reason) }
}

function requestStartSelection(team: DataObject) {
  if (Number(team.pending_invitation_count ?? 0) > 0) showStartSelectionConfirm.value = team
  else void startSelection(team)
}
async function startSelection(team: DataObject) {
  submitting.value = true
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/student/teams/${Number(team.id)}/lock`)
    const result = (response.data.data ?? {}) as DataObject
    showStartSelectionConfirm.value = null
    await router.push({ path: `/student/batches/${Number(result.batchId ?? team.batch_id)}/rooms`, query: { teamId: String(team.id), memberCount: String(result.memberCount ?? team.confirmed_member_count) } })
  } catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
async function leaveTeam(team: DataObject) {
  submitting.value = true
  try { await api.post(`/api/v1/student/teams/${Number(team.id)}/leave`); showLeaveConfirm.value = null; message.value = team.member_role === 'LEADER' ? '小组已解散。' : '你已退出小组。'; await load() }
  catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
async function removeMember() {
  if (!removeCandidate.value) return
  const { team, member } = removeCandidate.value; submitting.value = true
  try { await api.delete(`/api/v1/student/teams/${Number(team.id)}/members/${Number(member.student_id)}`); removeCandidate.value = null; await load() }
  catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
function memberSlots(team: DataObject) { const members = (team.members ?? []) as DataObject[]; return Array.from({ length: 5 }, (_, index) => members[index] ?? null) }
function canRemoveMember(team: DataObject, member: DataObject) { return team.member_role === 'LEADER' && member.member_role !== 'LEADER' && ['JOINED','LOCKED'].includes(String(member.member_status)) }
function teamStatusText(status: unknown) { return ({ FORMING:'邀请成员中',LOCKED:'成员已确认',SELECTING:'选寝中',COMPLETED:'已完成',DISSOLVED:'已结束' } as Record<string,string>)[String(status)] ?? String(status) }
</script>

<template>
  <div class="content-column team-page-refined">
    <div class="page-title"><span class="eyebrow">{{ subtitle('组队选寝','TEAM SELECTION') }}</span><h2>直接邀请队友一起选寝</h2><p>无需手动建立队伍。发送第一条邀请时系统会自动组队，每组最多5人。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <section v-if="!assigned && invitations.length" class="panel"><div class="section-head"><div><span class="eyebrow">待处理邀请</span><h3>同学邀请</h3></div></div><article v-for="invitation in invitations" :key="String(invitation.invitation_token)" class="invitation-item"><div><strong>{{ invitation.inviter_name }} 邀请你一起选寝</strong><p>邀请人学号：{{ invitation.inviter_student_number }}</p></div><div class="button-row"><button class="button ghost" @click="respond(invitation.invitation_token,false)">{{ t('common.reject') }}</button><button class="button primary" @click="respond(invitation.invitation_token,true)">{{ t('common.accept') }}</button></div></article></section>
    <section v-if="canInvite" class="panel compact-team-invite-panel"><div class="section-head"><div><span class="eyebrow">邀请队友</span><h3>{{ currentTeam ? '继续邀请队友' : '邀请第一名队友' }}</h3><p>请输入同批次、同性别同学的12位学号，首次邀请将自动建立队伍。</p></div></div><form class="team-invite-panel" @submit.prevent="invite"><label><span>队友学号</span><input v-model.trim="inviteStudentNumber" class="input" required inputmode="numeric" pattern="\d{12}" maxlength="12" placeholder="请输入12位学号" /></label><button class="button primary" :disabled="submitting">{{ submitting ? '正在发送…' : '发送邀请' }}</button></form></section>
    <p v-if="loading" class="panel empty-state">正在加载小组成员…</p><p v-else-if="assigned" class="panel empty-state">你已经确定住宿结果，不能再参与组队。</p><p v-else-if="teams.length===0" class="panel empty-state">尚未发出邀请，直接输入同学学号即可自动建立队伍。</p>
    <div v-else class="team-grid"><article v-for="team in teams" :key="String(team.id)" class="panel team-card"><div class="section-head split-title"><div><span class="status-chip compact">{{ teamStatusText(team.team_status) }}</span><h3>我的小组</h3><p>已确认 {{ team.confirmed_member_count }} 人 · 待确认 {{ team.pending_invitation_count ?? 0 }} 人</p></div><button class="button ghost" @click="showLeaveConfirm=team">退出队伍</button></div><div class="team-member-slot-grid"><article v-for="(member,index) in memberSlots(team)" :key="member?String(member.student_number):`empty-${index}`" class="team-member-slot-card" :class="{empty:!member}"><template v-if="member"><strong>{{ member.student_name }}</strong><span>{{ member.student_number }}</span><button v-if="canRemoveMember(team,member)" class="text-button" @click="removeCandidate={team,member}">删除队友</button></template><template v-else><span class="empty-team-slot-plus">+</span><strong>等待队友</strong></template></article></div><button v-if="team.member_role==='LEADER'&&team.team_status==='FORMING'" class="button primary" :disabled="Number(team.confirmed_member_count)<2||submitting" @click="requestStartSelection(team)">开始组队选寝</button></article></div>
    <div v-if="showStartSelectionConfirm" class="modal-overlay"><section class="modal-card confirmation-dialog"><h3>{{ t('team.pendingInvalidation.title') }}</h3><p>{{ t('team.pendingInvalidation.message') }}</p><div class="button-row"><button class="button ghost" @click="showStartSelectionConfirm=null">取消</button><button class="button primary" @click="startSelection(showStartSelectionConfirm)">确认</button></div></section></div>
    <div v-if="showLeaveConfirm" class="modal-overlay"><section class="modal-card confirmation-dialog"><h3>确认退出队伍</h3><p>邀请发起人退出后队伍将解散。</p><div class="button-row"><button class="button ghost" @click="showLeaveConfirm=null">取消</button><button class="button danger" @click="leaveTeam(showLeaveConfirm)">退出</button></div></section></div>
    <div v-if="removeCandidate" class="modal-overlay"><section class="modal-card confirmation-dialog"><h3>确认删除队友</h3><p>对方将收到系统通知。</p><div class="button-row"><button class="button ghost" @click="removeCandidate=null">取消</button><button class="button danger" @click="removeMember">删除</button></div></section></div>
  </div>
</template>

<style scoped>.invitation-item{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px;border:1px solid var(--line);border-radius:14px}.team-member-slot-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}.team-member-slot-card{display:grid;gap:5px;padding:12px;border:1px solid var(--line);border-radius:14px}.team-member-slot-card.empty{place-items:center;color:var(--muted)}.team-member-slot-card span{display:block;color:var(--muted);font-size:12px}@media(max-width:760px){.invitation-item{display:grid}.team-member-slot-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.team-invite-panel{grid-template-columns:1fr}}</style>
