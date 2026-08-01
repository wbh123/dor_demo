<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const teams = ref<DataObject[]>([])
const invitations = ref<DataObject[]>([])
const batches = ref<DataObject[]>([])
const error = ref('')
const message = ref('')
const loading = ref(true)
const createForm = reactive({ batchId: 0, teamName: '' })
const inviteNumbers = reactive<Record<string, string>>({})

const teamEnabledBatches = computed(() =>
  batches.value.filter((batch) => batch.allow_team && ['PUBLISHED', 'OPEN'].includes(String(batch.batch_status))),
)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [teamResponse, invitationResponse, batchResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/student/teams'),
      api.get<ListSuccessResponse>('/api/v1/student/team-invitations'),
      api.get<ListSuccessResponse>('/api/v1/student/batches'),
    ])
    teams.value = (teamResponse.data.data ?? []) as DataObject[]
    invitations.value = (invitationResponse.data.data ?? []) as DataObject[]
    batches.value = (batchResponse.data.data ?? []) as DataObject[]
    if (!createForm.batchId && teamEnabledBatches.value.length) {
      createForm.batchId = Number(teamEnabledBatches.value[0].id)
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '队伍数据加载失败'
  } finally {
    loading.value = false
  }
}

async function createTeam() {
  error.value = ''
  message.value = ''
  try {
    await api.post<ObjectSuccessResponse>(
      `/api/v1/student/batches/${createForm.batchId}/teams`,
      { teamName: createForm.teamName },
    )
    createForm.teamName = ''
    message.value = '队伍创建成功。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '创建队伍失败'
  }
}

async function invite(team: DataObject) {
  const teamId = Number(team.id)
  const studentNumber = inviteNumbers[String(teamId)]?.trim()
  if (!studentNumber) return
  error.value = ''
  try {
    await api.post(`/api/v1/student/teams/${teamId}/invitations`, { studentNumber })
    inviteNumbers[String(teamId)] = ''
    message.value = '邀请已发送。'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '邀请失败'
  }
}

async function respond(token: unknown, accepted: boolean) {
  error.value = ''
  try {
    await api.post('/api/v1/student/team-invitations/respond', {
      invitationToken: String(token),
      accepted,
    })
    message.value = accepted ? '已加入队伍。' : '已拒绝邀请。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '处理邀请失败'
  }
}

async function lock(teamId: unknown) {
  error.value = ''
  try {
    await api.post(`/api/v1/student/teams/${Number(teamId)}/lock`)
    message.value = '队伍已锁定，可以开始整体选择床位。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '锁定队伍失败'
  }
}

function statusText(status: unknown) {
  return {
    FORMING: '组建中',
    LOCKED: '已锁定',
    SELECTING: '选寝中',
    COMPLETED: '已完成',
    DISSOLVED: '已解散',
  }[String(status)] ?? String(status)
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">TEAM SELECTION</span>
      <h2>我的选寝队伍</h2>
      <p>队伍锁定后，队长一次选择与成员人数相同的同房间床位，提交时整体成功或整体失败。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="invitations.length" class="panel">
      <div class="section-head"><h3>待处理邀请</h3></div>
      <div class="invitation-list">
        <article v-for="invitation in invitations" :key="String(invitation.invitation_token)" class="invitation-item">
          <div>
            <strong>{{ invitation.team_name }}</strong>
            <p>{{ invitation.inviter_name }} 邀请你加入 · {{ invitation.team_code }}</p>
          </div>
          <div class="button-row">
            <button class="button ghost" @click="respond(invitation.invitation_token, false)">拒绝</button>
            <button class="button primary" @click="respond(invitation.invitation_token, true)">接受</button>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="section-head"><h3>创建新队伍</h3></div>
      <form class="inline-form" @submit.prevent="createTeam">
        <select v-model.number="createForm.batchId" class="input" required>
          <option v-for="batch in teamEnabledBatches" :key="String(batch.id)" :value="Number(batch.id)">
            {{ batch.batch_name }}
          </option>
        </select>
        <input v-model.trim="createForm.teamName" class="input" required maxlength="128" placeholder="队伍名称" />
        <button class="button primary">创建队伍</button>
      </form>
    </section>

    <p v-if="loading" class="panel empty-state">正在加载队伍…</p>
    <p v-else-if="teams.length === 0" class="panel empty-state">你还没有加入任何有效队伍。</p>

    <div v-else class="team-grid">
      <article v-for="team in teams" :key="String(team.id)" class="panel team-card">
        <div class="section-head">
          <div>
            <span class="status-chip compact">{{ statusText(team.team_status) }}</span>
            <h3>{{ team.team_name }}</h3>
            <p>{{ team.team_code }} · {{ team.member_count }} 人</p>
          </div>
        </div>

        <form v-if="team.member_role === 'LEADER' && team.team_status === 'FORMING'" class="inline-form" @submit.prevent="invite(team)">
          <input
            v-model="inviteNumbers[String(team.id)]"
            class="input"
            required
            pattern="\d{12}"
            maxlength="12"
            placeholder="输入12位学号邀请"
          />
          <button class="button secondary">发送邀请</button>
        </form>

        <div class="button-row">
          <button
            v-if="team.member_role === 'LEADER' && team.team_status === 'FORMING'"
            class="button primary"
            @click="lock(team.id)"
          >锁定队伍</button>
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
