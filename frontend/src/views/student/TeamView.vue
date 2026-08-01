<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const teams = ref<DataObject[]>([])
const invitations = ref<DataObject[]>([])
const inviteStudentNumber = ref('')
const error = ref('')
const message = ref('')
const loading = ref(true)
const submitting = ref(false)

const currentTeam = computed(() => teams.value[0] ?? null)
const canInvite = computed(() => {
  if (!currentTeam.value) return true
  return currentTeam.value.member_role === 'LEADER' && currentTeam.value.team_status === 'FORMING'
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
    error.value = reason instanceof Error ? reason.message : '小组信息加载失败'
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
    error.value = reason instanceof Error ? reason.message : '邀请发送失败'
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
    error.value = reason instanceof Error ? reason.message : '邀请处理失败'
  }
}

async function lock(teamId: unknown) {
  error.value = ''
  message.value = ''
  try {
    await api.post(`/api/v1/student/teams/${Number(teamId)}/lock`)
    message.value = '小组成员已确认，可以开始整体选择床位。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '成员确认失败'
  }
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
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">TEAM SELECTION</span>
      <h2>邀请队友一起选寝</h2>
      <p>输入同学的12位学号发送邀请。对方接受后即可成为小组成员，成员确认后由邀请发起人整体选择床位。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="invitations.length" class="panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">INVITATIONS</span>
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
            <button class="button ghost" @click="respond(invitation.invitation_token, false)">拒绝</button>
            <button class="button primary" @click="respond(invitation.invitation_token, true)">接受</button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="canInvite" class="panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">INVITE A ROOMMATE</span>
          <h3>邀请队友</h3>
          <p v-if="!currentTeam">首次发送邀请时，系统会自动建立当前选寝小组。</p>
          <p v-else>可以继续邀请同批次、同性别且尚未加入其他小组的同学。</p>
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

    <div v-else class="team-grid">
      <article v-for="team in teams" :key="String(team.id)" class="panel team-card">
        <div class="section-head">
          <div>
            <span class="status-chip compact">{{ teamStatusText(team.team_status) }}</span>
            <h3>我的小组</h3>
            <p>当前共 {{ team.member_count }} 名成员</p>
          </div>
        </div>

        <div class="team-member-list" aria-label="小组成员">
          <article
            v-for="member in (team.members as DataObject[])"
            :key="String(member.student_number)"
            class="team-member-item"
          >
            <div class="team-member-identity">
              <strong>{{ member.student_name }}</strong>
              <span>{{ member.student_number }}</span>
            </div>
            <div class="button-row">
              <span class="status-chip compact">{{ memberRoleText(member.member_role) }}</span>
              <span class="status-chip compact">{{ memberStatusText(member.member_status) }}</span>
            </div>
          </article>
        </div>

        <div class="button-row">
          <button
            v-if="team.member_role === 'LEADER' && team.team_status === 'FORMING'"
            class="button primary"
            @click="lock(team.id)"
          >确认小组成员</button>
          <RouterLink
            v-if="team.member_role === 'LEADER' && team.team_status === 'LOCKED'"
            class="button primary"
            :to="`/student/batches/${team.batch_id}/rooms?teamId=${team.id}&memberCount=${team.member_count}`"
          >整体选择床位</RouterLink>
        </div>
      </article>
    </div>
  </div>
</template>
