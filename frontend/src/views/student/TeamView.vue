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
const showStartSelectionConfirm = ref<DataObject | null>(null)
const showLeaveConfirm = ref<DataObject | null>(null)
const removeCandidate = ref<{ team: DataObject; member: DataObject } | null>(null)

const { t, subtitle, translateError } = useI18n()

const currentTeam = computed(() => teams.value[0] ?? null)
const currentMembers = computed(() => ((currentTeam.value?.members ?? []) as DataObject[]))
const canInvite = computed(() => {
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
    const [teamResponse, invitationResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/student/teams'),
      api.get<ListSuccessResponse>('/api/v1/student/team-invitations'),
    ])
    teams.value = (teamResponse.data.data ?? []) as DataObject[]
    invitations.value = (invitationResponse.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

async function invite() {
  const studentNumber = inviteStudentNumber.value.trim()
  if (!/^\d{12}$/.test(studentNumber) || submitting.value) return
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(
      '/api/v1/student/team-invitations',
      { studentNumber },
    )
    const invited = (response.data.data ?? {}) as DataObject
    inviteStudentNumber.value = ''
    message.value = `已向 ${String(invited.studentName)} 发送邀请。`
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    submitting.value = false
  }
}

async function respond(token: unknown, accepted: boolean) {
  error.value = ''
  message.value = ''
  try {
    await api.post('/api/v1/student/team-invitations/respond', {
      invitationToken: String(token),
      accepted,
    })
    message.value = accepted ? '已加入小组。' : '已拒绝邀请。'
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function requestStartSelection(team: DataObject) {
  if (Number(team.pending_invitation_count ?? 0) > 0) {
    showStartSelectionConfirm.value = team
    return
  }
  void startSelection(team)
}

async function startSelection(team: DataObject) {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(
      `/api/v1/student/teams/${Number(team.id)}/lock`,
    )
    const result = (response.data.data ?? {}) as DataObject
    showStartSelectionConfirm.value = null
    await router.push({
      path: `/student/batches/${Number(result.batchId ?? team.batch_id)}/rooms`,
      query: {
        teamId: String(team.id),
        memberCount: String(result.memberCount ?? team.confirmed_member_count),
      },
    })
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    submitting.value = false
  }
}

async function leaveTeam(team: DataObject) {
  submitting.value = true
  error.value = ''
  try {
    await api.post(`/api/v1/student/teams/${Number(team.id)}/leave`)
    showLeaveConfirm.value = null
    message.value = team.member_role === 'LEADER' ? '小组已解散。' : '你已退出小组。'
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    submitting.value = false
  }
}

async function removeMember() {
  if (!removeCandidate.value) return
  submitting.value = true
  error.value = ''
  const { team, member } = removeCandidate.value
  try {
    await api.delete(
      `/api/v1/student/teams/${Number(team.id)}/members/${Number(member.student_id)}`,
    )
    message.value = `已将 ${String(member.student_name)} 移出小组。`
    removeCandidate.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    submitting.value = false
  }
}

function memberSlots(team: DataObject) {
  const members = (team.members ?? []) as DataObject[]
  return Array.from({ length: 5 }, (_, index) => members[index] ?? null)
}

function canRemoveMember(team: DataObject, member: DataObject) {
  return team.member_role === 'LEADER'
    && member.member_role !== 'LEADER'
    && ['JOINED', 'LOCKED'].includes(String(member.member_status))
}

function teamStatusText(status: unknown) {
  return {
    FORMING: '邀请成员中',
    LOCKED: '成员已确认',
    SELECTING: '选寝中',
    COMPLETED: '已完成',
    DISSOLVED: '已结束',
  }[String(status)] ?? String(status)
}

function memberStatusText(status: unknown) {
  return {
    INVITED: '等待接受',
    JOINED: '已加入',
    LOCKED: '成员已确认',
    LEFT: '已退出',
    REMOVED: '已移除',
    REJECTED: '已拒绝',
  }[String(status)] ?? String(status)
}

function memberRoleText(role: unknown) {
  return String(role) === 'LEADER' ? '邀请发起人' : '小组成员'
}
</script>

<template>
  <div class="content-column team-page-refined">
    <div class="page-title team-page-heading">
      <span class="eyebrow">{{ subtitle('组队选寝', 'TEAM SELECTION') }}</span>
      <h2>邀请队友一起选寝</h2>
      <p>{{ t('team.maxHint') }}</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="invitations.length" class="panel team-invitation-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">{{ subtitle('待处理邀请', 'INVITATIONS') }}</span>
          <h3>待处理邀请</h3>
        </div>
      </div>
      <div class="invitation-list">
        <article
          v-for="invitation in invitations"
          :key="String(invitation.invitation_token)"
          class="invitation-item"
        >
          <div>
            <strong>{{ invitation.inviter_name }} 邀请你一起选寝</strong>
            <p>邀请人学号：{{ invitation.inviter_student_number }}</p>
          </div>
          <div class="button-row">
            <button class="button ghost" @click="respond(invitation.invitation_token, false)">{{ t('common.reject') }}</button>
            <button class="button primary" @click="respond(invitation.invitation_token, true)">{{ t('common.accept') }}</button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="canInvite" class="panel compact-team-invite-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">{{ subtitle('邀请队友', 'INVITE A ROOMMATE') }}</span>
          <h3>邀请队友</h3>
          <p>请输入同批次、同性别同学的12位学号。待确认邀请也会占用一个小组名额。</p>
        </div>
      </div>
      <form class="team-invite-panel" @submit.prevent="invite">
        <label>
          <span>队友学号</span>
          <input
            v-model.trim="inviteStudentNumber"
            class="input"
            required
            inputmode="numeric"
            autocomplete="off"
            pattern="\d{12}"
            maxlength="12"
            placeholder="请输入12位学号"
          />
        </label>
        <button class="button primary" :disabled="submitting">
          {{ submitting ? '正在发送…' : '发送邀请' }}
        </button>
      </form>
    </section>

    <p v-if="loading" class="panel empty-state">正在加载小组成员…</p>
    <p v-else-if="teams.length === 0" class="panel empty-state">
      你还没有加入小组，可以直接邀请一名队友开始组队。
    </p>

    <div v-else class="team-grid refined-team-grid">
      <article v-for="team in teams" :key="String(team.id)" class="panel team-card refined-team-card">
        <div class="section-head split-title">
          <div>
            <span class="status-chip compact">{{ teamStatusText(team.team_status) }}</span>
            <h3>我的小组</h3>
            <p>
              已确认 {{ team.confirmed_member_count }} 人
              <template v-if="Number(team.pending_invitation_count) > 0">
                · 待确认 {{ team.pending_invitation_count }} 人
              </template>
            </p>
          </div>
          <button class="button ghost" type="button" @click="showLeaveConfirm = team">退出队伍</button>
        </div>

        <div class="team-member-slot-grid" aria-label="小组成员">
          <article
            v-for="(member, index) in memberSlots(team)"
            :key="member ? String(member.student_number) : `empty-${index}`"
            class="team-member-slot-card"
            :class="{ empty: !member, pending: member?.member_status === 'INVITED' }"
          >
            <template v-if="member">
              <div class="team-member-slot-index">{{ index + 1 }}</div>
              <div class="team-member-identity">
                <strong>{{ member.student_name }}</strong>
                <span>{{ member.student_number }}</span>
              </div>
              <div class="team-member-slot-status">
                <span>{{ memberRoleText(member.member_role) }}</span>
                <b>{{ memberStatusText(member.member_status) }}</b>
              </div>
              <button
                v-if="canRemoveMember(team, member)"
                class="team-member-remove-button"
                type="button"
                @click="removeCandidate = { team, member }"
              >删除队友</button>
            </template>
            <template v-else>
              <span class="empty-team-slot-plus">+</span>
              <strong>等待队友</strong>
              <small>可继续邀请</small>
            </template>
          </article>
        </div>

        <div class="button-row team-selection-actions">
          <button
            v-if="team.member_role === 'LEADER' && team.team_status === 'FORMING'"
            class="button primary"
            :disabled="Number(team.confirmed_member_count) < 2 || submitting"
            @click="requestStartSelection(team)"
          >开始组队选寝</button>
          <RouterLink
            v-if="team.member_role === 'LEADER' && team.team_status === 'LOCKED'"
            class="button primary"
            :to="`/student/batches/${team.batch_id}/rooms?teamId=${team.id}&memberCount=${team.confirmed_member_count}`"
          >继续整体选择床位</RouterLink>
        </div>
      </article>
    </div>

    <div v-if="showStartSelectionConfirm" class="modal-overlay" role="presentation">
      <section class="modal-card confirmation-dialog" role="dialog" aria-modal="true">
        <h3>{{ t('team.pendingInvalidation.title') }}</h3>
        <p>{{ t('team.pendingInvalidation.message') }}</p>
        <div class="button-row">
          <button class="button ghost" @click="showStartSelectionConfirm = null">{{ t('common.cancel') }}</button>
          <button class="button primary" :disabled="submitting" @click="startSelection(showStartSelectionConfirm)">{{ t('common.confirm') }}</button>
        </div>
      </section>
    </div>

    <div v-if="showLeaveConfirm" class="modal-overlay" role="presentation">
      <section class="modal-card confirmation-dialog" role="dialog" aria-modal="true">
        <h3>{{ t('team.leave.title') }}</h3>
        <p>{{ showLeaveConfirm.member_role === 'LEADER' ? '邀请发起人退出后，小组会解散，已接受成员将收到系统通知。' : '退出后你可以加入其他队伍或进行个人选寝。' }}</p>
        <div class="button-row">
          <button class="button ghost" @click="showLeaveConfirm = null">{{ t('common.cancel') }}</button>
          <button class="button danger" :disabled="submitting" @click="leaveTeam(showLeaveConfirm)">{{ t('common.leave') }}</button>
        </div>
      </section>
    </div>

    <div v-if="removeCandidate" class="modal-overlay" role="presentation">
      <section class="modal-card confirmation-dialog" role="dialog" aria-modal="true">
        <h3>{{ t('team.remove.title') }}</h3>
        <p>移除 {{ removeCandidate.member.student_name }} 后，对方会收到系统通知，并可立即进行个人选寝。</p>
        <div class="button-row">
          <button class="button ghost" @click="removeCandidate = null">{{ t('common.cancel') }}</button>
          <button class="button danger" :disabled="submitting" @click="removeMember">{{ t('common.remove') }}</button>
        </div>
      </section>
    </div>
  </div>
</template>
