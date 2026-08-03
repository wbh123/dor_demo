<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions } from '../../utils/countries'
import { useI18n } from '../../i18n'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const welcomeMessages = reactive<Record<string, string>>({
  'zh-CN': '',
  'en-US': '',
})
const countryMessages = reactive<Record<string, string>>({})
const newCountryCode = ref('US')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const welcomeSaving = ref(false)
const welcomeError = ref('')
const welcomeSuccess = ref('')
const { subtitle, translateError } = useI18n()

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
    error.value = translateError(reason)
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
    const messages = (data.messages ?? {}) as Record<string, string>
    welcomeMessages['zh-CN'] = String(messages['zh-CN'] ?? data.message ?? '')
    welcomeMessages['en-US'] = String(messages['en-US'] ?? '')
    Object.keys(countryMessages).forEach((key) => delete countryMessages[key])
    Object.assign(countryMessages, (data.countryMessages ?? {}) as Record<string, string>)
    welcomeVersion.value = Number(data.version ?? 0)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
  } catch (reason) {
    welcomeError.value = translateError(reason)
  }
}

async function saveWelcomeSetting() {
  const normalized = {
    'zh-CN': welcomeMessages['zh-CN'].trim(),
    'en-US': welcomeMessages['en-US'].trim(),
  }
  welcomeError.value = ''
  welcomeSuccess.value = ''
  if (Object.values(normalized).some((message) => !message || message.length > 1000)) {
    welcomeError.value = '中文和英文欢迎语均不能为空，且长度必须为1至1000个字符。'
    return
  }
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>(
      '/api/v1/admin/settings/student-welcome',
      { messages: normalized, countryMessages: { ...countryMessages }, expectedVersion: welcomeVersion.value },
    )
    const data = (response.data.data ?? {}) as DataObject
    const messages = (data.messages ?? normalized) as Record<string, string>
    welcomeMessages['zh-CN'] = String(messages['zh-CN'] ?? normalized['zh-CN'])
    welcomeMessages['en-US'] = String(messages['en-US'] ?? normalized['en-US'])
    Object.keys(countryMessages).forEach((key) => delete countryMessages[key])
    Object.assign(countryMessages, (data.countryMessages ?? countryMessages) as Record<string, string>)
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    welcomeSuccess.value = '多语言新生欢迎语已保存。'
  } catch (reason) {
    welcomeError.value = translateError(reason)
  } finally {
    welcomeSaving.value = false
  }
}


function addCountryWelcome() {
  const code = newCountryCode.value.trim().toUpperCase()
  if (!code || countryMessages[code] !== undefined) return
  countryMessages[code] = ''
}

function removeCountryWelcome(code: string) {
  delete countryMessages[code]
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
        <span class="eyebrow">{{ subtitle('运行概览', 'OPERATIONS OVERVIEW') }}</span>
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
            <span class="eyebrow">{{ subtitle('首次登录欢迎', 'FIRST LOGIN WELCOME') }}</span>
            <h3>新生欢迎语</h3>
            <p>可按学生国籍设置专属欢迎语；未配置该国家/地区时自动显示英文欢迎语。</p>
          </div>
        </div>
        <div class="multilingual-welcome-grid">
          <label>
            <span>中文欢迎语</span>
            <small>{{ welcomeMessages['zh-CN'].length }}/1000</small>
            <textarea
              v-model="welcomeMessages['zh-CN']"
              class="input welcome-message-input"
              rows="5"
              maxlength="1000"
              placeholder="请输入中文首次登录欢迎文本"
            />
          </label>
          <label>
            <span>英文欢迎语</span>
            <small>{{ welcomeMessages['en-US'].length }}/1000</small>
            <textarea
              v-model="welcomeMessages['en-US']"
              class="input welcome-message-input"
              rows="5"
              maxlength="1000"
              placeholder="Enter the English welcome message"
            />
          </label>
        </div>
        <section class="country-welcome-section"><div class="section-head split-title"><div><strong>按国家/地区设置欢迎语</strong><p>国籍匹配优先于界面语言，未匹配时回退英文。</p></div><div class="button-row"><select v-model="newCountryCode" class="input"><option v-for="country in countryOptions" :key="country.code" :value="country.code">{{ country.name }}</option></select><button class="button secondary" @click="addCountryWelcome">添加</button></div></div><div v-if="Object.keys(countryMessages).length" class="country-welcome-list"><label v-for="(_, code) in countryMessages" :key="code"><span>{{ countryLabel(code) }}</span><textarea v-model="countryMessages[code]" class="input" rows="3" maxlength="1000" :placeholder="`请输入${countryLabel(code)}学生的欢迎语`" /><button class="text-button danger-text" type="button" @click="removeCountryWelcome(String(code))">删除该国家欢迎语</button></label></div><p v-else class="empty-state">尚未设置国家专属欢迎语。</p></section>
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
            <div><span class="eyebrow">{{ subtitle('选寝批次', 'BATCHES') }}</span><h3>最近选寝批次</h3></div>
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
          <div class="section-head"><div><span class="eyebrow">{{ subtitle('审计记录', 'AUDIT') }}</span><h3>最近操作</h3></div></div>
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

<style scoped>
.country-welcome-section { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--border); }
.country-welcome-list { display: grid; grid-template-columns: repeat(auto-fit,minmax(300px,1fr)); gap: 12px; }
.country-welcome-list label { display: grid; gap: 7px; padding: 14px; border: 1px solid var(--border); border-radius: 13px; }
.danger-text { color: #b91c1c; justify-self: start; }
</style>
