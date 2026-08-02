<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const welcomeMessage = ref('')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const welcomeSaving = ref(false)
const welcomeError = ref('')
const welcomeSuccess = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [dashboardResponse, batchesResponse, auditResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/dashboard'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/audit-logs?limit=8'),
    ])
    dashboard.value = (dashboardResponse.data.data ?? {}) as DataObject
    batches.value = (batchesResponse.data.data ?? []) as DataObject[]
    auditLogs.value = (auditResponse.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '管理数据加载失败'
  } finally {
    loading.value = false
  }
  await loadWelcomeSetting()
}

async function loadWelcomeSetting() {
  welcomeError.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome')
    const data = (response.data.data ?? {}) as DataObject
    welcomeMessage.value = String(data.message ?? '')
    welcomeVersion.value = Number(data.version ?? 0)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
  } catch (reason) {
    welcomeError.value = reason instanceof Error ? reason.message : '欢迎语配置加载失败'
  }
}

async function saveWelcomeSetting() {
  const normalized = welcomeMessage.value.trim()
  welcomeError.value = ''
  welcomeSuccess.value = ''
  if (!normalized || normalized.length > 1000) {
    welcomeError.value = '欢迎语长度必须为1至1000个字符。'
    return
  }
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>(
      '/api/v1/admin/settings/student-welcome',
      { message: normalized, expectedVersion: welcomeVersion.value },
    )
    const data = (response.data.data ?? {}) as DataObject
    welcomeMessage.value = String(data.message ?? normalized)
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    welcomeSuccess.value = '新生欢迎语已保存。'
  } catch (reason) {
    welcomeError.value = reason instanceof Error ? reason.message : '欢迎语保存失败'
  } finally {
    welcomeSaving.value = false
  }
}

const stats = [
  ['studentCount', '学生总数', '人'],
  ['roomCount', '宿舍房间', '间'],
  ['bedCount', '启用床位', '个'],
  ['activeAssignmentCount', '已完成分配', '人'],
]
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">OPERATIONS OVERVIEW</span>
        <h2>第一阶段运行概览</h2>
        <p>学生、宿舍、批次和最终分配的当前数据库统计。</p>
      </div>
      <button class="button ghost" @click="load">刷新数据</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="loading" class="panel empty-state">正在汇总管理数据…</p>

    <template v-else>
      <div class="stat-grid">
        <article v-for="stat in stats" :key="stat[0]" class="panel stat-card">
          <span>{{ stat[1] }}</span>
          <strong>{{ dashboard[stat[0]] ?? 0 }}</strong>
          <small>{{ stat[2] }}</small>
        </article>
      </div>

      <section class="panel welcome-setting-card">
        <div class="section-head split-title">
          <div>
            <span class="eyebrow">FIRST LOGIN WELCOME</span>
            <h3>新生欢迎语</h3>
            <p>学生激活账号后首次登录时显示。已经确认过欢迎信息的学生不会重复弹出。</p>
          </div>
          <span class="welcome-character-count">{{ welcomeMessage.length }}/1000</span>
        </div>
        <textarea
          v-model="welcomeMessage"
          class="input welcome-message-input"
          rows="4"
          maxlength="1000"
          placeholder="请输入首次登录欢迎文本"
        />
        <div class="welcome-setting-meta">
          <span v-if="welcomeUpdatedAt">最后修改：{{ welcomeUpdatedAt }}</span>
          <span v-if="welcomeUpdatedBy">修改人：{{ welcomeUpdatedBy }}</span>
        </div>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
        <p v-if="welcomeSuccess" class="alert success">{{ welcomeSuccess }}</p>
        <div class="button-row welcome-setting-actions">
          <button class="button ghost" :disabled="welcomeSaving" @click="loadWelcomeSetting">重新加载</button>
          <button class="button primary" :disabled="welcomeSaving" @click="saveWelcomeSetting">
            {{ welcomeSaving ? '正在保存…' : '保存欢迎语' }}
          </button>
        </div>
      </section>

      <div class="admin-grid">
        <section class="panel span-2">
          <div class="section-head">
            <div><span class="eyebrow">BATCHES</span><h3>最近选寝批次</h3></div>
            <RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead>
              <tbody>
                <tr v-for="batch in batches.slice(0, 6)" :key="String(batch.id)">
                  <td><strong>{{ batch.batch_name }}</strong><small>{{ batch.batch_code }}</small></td>
                  <td><span class="status-chip compact">{{ batch.batch_status }}</span></td>
                  <td>{{ batch.eligible_count }}</td>
                  <td>{{ batch.assigned_count }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <div class="section-head"><div><span class="eyebrow">AUDIT</span><h3>最近操作</h3></div></div>
          <div class="audit-list">
            <article v-for="log in auditLogs" :key="String(log.id)">
              <span class="audit-dot" />
              <div><strong>{{ log.action_type }}</strong><p>{{ log.resource_type }} · {{ log.resource_id || '-' }}</p></div>
            </article>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>
