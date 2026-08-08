<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import TransientNotice from '../../components/common/TransientNotice.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const router = useRouter()
const teams = ref<DataObject[]>([])
const invitations = ref<DataObject[]>([])
const inviteStudentNumber = ref('')
const inviteStudentName = ref('')
const error = ref('')
const notice = ref('')
const loading = ref(true)
const submitting = ref(false)
const assigned = ref(false)
const showStartSelectionConfirm = ref<DataObject | null>(null)
const showLeaveConfirm = ref<DataObject | null>(null)
const removeCandidate = ref<{ team: DataObject; member: DataObject } | null>(null)
const cancelCandidate = ref<{ team: DataObject; member: DataObject } | null>(null)
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
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}
function showNotice(value: string) { notice.value = ''; window.setTimeout(() => { notice.value = value }, 0) }
async function invite() {
  const studentNumber = inviteStudentNumber.value.trim(); const studentName = inviteStudentName.value.trim()
  if (!/^\d{12}$/.test(studentNumber) || !studentName || submitting.value) return
  submitting.value = true; error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>('/api/v1/student/team-invitations', { studentNumber, studentName })
    const invited = (response.data.data ?? {}) as DataObject
    inviteStudentNumber.value = ''; inviteStudentName.value = ''
    showNotice(`已向 ${String(invited.studentName)} 发送邀请。`); await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
async function respond(token: unknown, accepted: boolean) {
  error.value = ''
  try { await api.post('/api/v1/student/team-invitations/respond', { invitationToken: String(token), accepted }); showNotice(accepted ? '已加入小组。' : '已拒绝邀请。'); await load() }
  catch (reason) { error.value = translateError(reason) }
}
async function cancelInvitation() {
  if (!cancelCandidate.value || submitting.value) return
  const { team, member } = cancelCandidate.value
  submitting.value = true; error.value = ''
  try { await api.delete(`/api/v1/student/teams/${Number(team.id)}/members/${Number(member.student_id)}`); cancelCandidate.value = null; showNotice(`已取消对 ${String(member.student_name)} 的邀请。`); await load() }
  catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
function requestStartSelection(team: DataObject) {
  if (Number(team.pending_invitation_count ?? 0) > 0) showStartSelectionConfirm.value = team
  else void startSelection(team)
}
async function startSelection(team: DataObject) {
  submitting.value = true; error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/student/teams/${Number(team.id)}/lock`)
    const result = (response.data.data ?? {}) as DataObject
    showStartSelectionConfirm.value = null
    await router.push({ path: `/student/batches/${Number(result.batchId ?? team.batch_id)}/rooms`, query: { teamId: String(team.id), memberCount: String(result.memberCount ?? team.confirmed_member_count) } })
  } catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
async function leaveTeam(team: DataObject) {
  submitting.value = true; error.value = ''
  try { await api.post(`/api/v1/student/teams/${Number(team.id)}/leave`); showLeaveConfirm.value = null; showNotice(team.member_role === 'LEADER' ? '小组已解散。' : '你已退出小组。'); await load() }
  catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
async function removeMember() {
  if (!removeCandidate.value || submitting.value) return
  const { team, member } = removeCandidate.value
  submitting.value = true; error.value = ''
  try { await api.delete(`/api/v1/student/teams/${Number(team.id)}/members/${Number(member.student_id)}`); removeCandidate.value = null; showNotice(`已将 ${String(member.student_name)} 移出小组。`); await load() }
  catch (reason) { error.value = translateError(reason) }
  finally { submitting.value = false }
}
function canRemoveMember(team: DataObject, member: DataObject) { return team.member_role === 'LEADER' && member.member_role !== 'LEADER' && ['JOINED','LOCKED'].includes(String(member.member_status)) }
function canCancelInvitation(team: DataObject, member: DataObject) { return team.member_role === 'LEADER' && team.team_status === 'FORMING' && member.member_status === 'INVITED' }
function teamStatusText(status: unknown) { return ({ FORMING:'邀请成员中', LOCKED:'成员已确认', SELECTING:'选寝中', COMPLETED:'已完成', DISSOLVED:'已结束' } as Record<string,string>)[String(status)] ?? String(status) }
</script>

<template>
  <div class="content-column team-page-refined">
    <TransientNotice :message="notice" @close="notice = ''" />
    <div class="page-title"><span class="eyebrow">{{ subtitle('组队选寝','TEAM SELECTION') }}</span><h2>直接邀请队友一起选寝</h2><p>邀请时必须同时填写学号和姓名，系统匹配成功后才会发送，避免误邀和骚扰。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p>
    <section v-if="!assigned && invitations.length" class="panel"><div class="section-head"><div><span class="eyebrow">待处理邀请</span><h3>同学邀请</h3></div></div><article v-for="invitation in invitations" :key="String(invitation.invitation_token)" class="invitation-item"><div><strong>{{ invitation.inviter_name }} 邀请你一起选寝</strong><p>邀请人学号：{{ invitation.inviter_student_number }}</p></div><div class="button-row"><button class="button ghost" @click="respond(invitation.invitation_token,false)">{{ t('common.reject') }}</button><button class="button primary" @click="respond(invitation.invitation_token,true)">{{ t('common.accept') }}</button></div></article></section>

    <section v-if="canInvite" class="panel compact-team-invite-panel"><div class="section-head"><div><span class="eyebrow">邀请队友</span><h3>{{ currentTeam ? '继续邀请队友' : '邀请第一名队友' }}</h3><p>学号和姓名必须同时匹配当前选寝批次中的同学。首次邀请会自动建立队伍。</p></div></div><form class="team-invite-panel verified-invite-form" @submit.prevent="invite"><label><span>队友学号</span><input v-model.trim="inviteStudentNumber" class="input" required inputmode="numeric" pattern="\d{12}" maxlength="12" placeholder="请输入12位学号" /></label><label><span>队友姓名</span><input v-model.trim="inviteStudentName" class="input" required maxlength="128" placeholder="请输入与学号对应的姓名" /></label><button class="button primary" :disabled="submitting">{{ submitting ? '正在发送…' : '发送邀请' }}</button></form></section>

    <p v-if="loading" class="panel empty-state">正在加载小组成员…</p><p v-else-if="assigned" class="panel empty-state">你已经确定住宿结果，不能再参与组队。</p><p v-else-if="teams.length===0" class="panel empty-state">尚未发出邀请，填写同学学号和姓名即可自动建立队伍。</p>

    <div v-else class="team-grid team-card-full-width">
      <article v-for="team in teams" :key="String(team.id)" class="panel team-card">
        <div class="section-head split-title"><div><span class="status-chip compact">{{ teamStatusText(team.team_status) }}</span><h3>我的小组</h3><p>已确认 {{ team.confirmed_member_count }} 人 · 待确认 {{ team.pending_invitation_count ?? 0 }} 人</p></div><button class="button ghost" @click="showLeaveConfirm=team">{{ team.member_role==='LEADER'?'解散队伍':'退出队伍' }}</button></div>
        <div class="team-member-slot-grid">
          <article v-for="member in ((team.members??[]) as DataObject[])" :key="String(member.student_number)" class="team-member-slot-card"><div class="member-primary"><strong>{{ member.student_name }}</strong><span>{{ member.student_number }}</span></div><small>{{ member.member_role==='LEADER'?'队长':member.member_status==='INVITED'?'等待对方确认':'已确认队友' }}</small><div class="member-actions"><button v-if="canCancelInvitation(team,member)" class="button ghost small danger-text" type="button" @click="cancelCandidate={team,member}">取消邀请</button><button v-else-if="canRemoveMember(team,member)" class="button ghost small" type="button" @click="removeCandidate={team,member}">删除队友</button></div></article>
        </div>
        <button v-if="team.member_role==='LEADER'&&team.team_status==='FORMING'" class="button primary" :disabled="Number(team.confirmed_member_count)<2||submitting" @click="requestStartSelection(team)">仅与已确认队友开始选寝</button>
      </article>
    </div>

    <ActionConfirmDialog :open="Boolean(showStartSelectionConfirm)" title="确认开始组队选寝" message="系统将只保留已经确认加入的队友，并自动取消所有未确认邀请。" detail="被取消的邀请令牌会立即失效，未确认同学不能再通过原邀请加入本队伍。" confirm-text="确认并开始选寝" :busy="submitting" @cancel="showStartSelectionConfirm=null" @confirm="showStartSelectionConfirm&&startSelection(showStartSelectionConfirm)" />
    <ActionConfirmDialog :open="Boolean(showLeaveConfirm)" title="确认退出队伍" :message="showLeaveConfirm?.member_role==='LEADER'?'队长退出后队伍将解散。':'退出后将不能参与本队伍选寝。'" confirm-text="确认退出" danger :busy="submitting" @cancel="showLeaveConfirm=null" @confirm="showLeaveConfirm&&leaveTeam(showLeaveConfirm)" />
    <ActionConfirmDialog :open="Boolean(removeCandidate)" title="确认删除队友" message="删除后对方将收到系统通知，并失去本队伍选寝资格。" confirm-text="删除队友" danger :busy="submitting" @cancel="removeCandidate=null" @confirm="removeMember" />
    <ActionConfirmDialog :open="Boolean(cancelCandidate)" title="确认取消邀请" :message="`取消后 ${String(cancelCandidate?.member.student_name??'该同学')} 将不能再通过本次邀请加入小组。`" confirm-text="取消邀请" danger :busy="submitting" @cancel="cancelCandidate=null" @confirm="cancelInvitation" />
  </div>
</template>

<style scoped>
.invitation-item{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px;border:1px solid var(--line);border-radius:14px}.verified-invite-form{display:grid;grid-template-columns:1fr 1fr auto;align-items:end;gap:10px}.team-card-full-width{display:grid;width:100%}.team-card{width:100%;min-width:0}.team-member-slot-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px;width:100%;margin-bottom:16px}.team-member-slot-card{display:flex;align-items:center;justify-content:space-between;gap:14px;min-width:0;width:100%;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.member-primary{display:grid;gap:4px;min-width:0;flex:1}.member-primary strong,.member-primary span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.member-primary span,.team-member-slot-card small{color:var(--muted);font-size:12px}.member-actions{flex:0 0 auto}.danger-text{color:var(--danger)}@media(max-width:760px){.invitation-item{display:grid}.verified-invite-form{grid-template-columns:1fr}.team-member-slot-grid{grid-template-columns:1fr}.team-member-slot-card{align-items:flex-start;flex-direction:column}.member-actions{width:100%}.member-actions .button{width:100%}}
</style>