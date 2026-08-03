<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions } from '../../utils/countries'
import { useI18n } from '../../i18n'

interface BrowserTranslator {
  translate(text: string): Promise<string>
}

interface BrowserTranslatorFactory {
  create(options: { sourceLanguage: string; targetLanguage: string }): Promise<BrowserTranslator>
}

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const welcomeMessages = reactive<Record<string, string>>({ 'zh-CN': '', 'en-US': '' })
const countryMessages = reactive<Record<string, string>>({})
const newCountryCode = ref('US')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const welcomeSaving = ref(false)
const welcomeError = ref('')
const welcomeSuccess = ref('')
const autoTranslate = ref(true)
const translating = ref(false)
const activeEditor = ref<HTMLTextAreaElement | null>(null)
const activeEditorKind = ref<'message' | 'country'>('message')
const activeEditorKey = ref('zh-CN')
let translationTimer: number | undefined
const { subtitle, translateError } = useI18n()

const placeholders = ['学生姓名', '学号', '专业名称', '年级', '培养层次', '国家或地区']
const stats = [
  ['studentCount', '学生总数', '人'],
  ['roomCount', '宿舍房间', '间'],
  ['bedCount', '启用床位', '个'],
  ['activeAssignmentCount', '已完成分配', '人'],
]

onMounted(load)

watch(() => welcomeMessages['zh-CN'], () => {
  if (!autoTranslate.value || translating.value) return
  if (translationTimer) window.clearTimeout(translationTimer)
  translationTimer = window.setTimeout(() => void translateChinese(false), 700)
})

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

async function translateChinese(force: boolean) {
  const source = welcomeMessages['zh-CN'].trim()
  if (!source || translating.value || (!force && !autoTranslate.value)) return
  welcomeError.value = ''
  const factory = (window as Window & { Translator?: BrowserTranslatorFactory }).Translator
  if (!factory?.create) {
    if (force) welcomeError.value = '当前浏览器未提供本地翻译能力，请直接修改英文欢迎语。'
    return
  }
  translating.value = true
  try {
    const translator = await factory.create({ sourceLanguage: 'zh', targetLanguage: 'en' })
    welcomeMessages['en-US'] = await translator.translate(source)
    welcomeSuccess.value = '英文欢迎语已根据中文更新，你仍可单独修改。'
  } catch {
    if (force) welcomeError.value = '自动翻译暂时不可用，请稍后重试或直接修改英文欢迎语。'
  } finally {
    translating.value = false
  }
}

function markEnglishEdited() {
  if (!translating.value) autoTranslate.value = false
}

function setActiveEditor(event: FocusEvent, kind: 'message' | 'country', key: string) {
  activeEditor.value = event.target as HTMLTextAreaElement
  activeEditorKind.value = kind
  activeEditorKey.value = key
}

async function insertPlaceholder(name: string) {
  const token = `{{${name}}}`
  const editor = activeEditor.value
  if (!editor) {
    welcomeMessages['zh-CN'] += token
    return
  }
  const current = activeEditorKind.value === 'country'
    ? String(countryMessages[activeEditorKey.value] ?? '')
    : String(welcomeMessages[activeEditorKey.value] ?? '')
  const start = editor.selectionStart ?? current.length
  const end = editor.selectionEnd ?? start
  const next = `${current.slice(0, start)}${token}${current.slice(end)}`
  if (activeEditorKind.value === 'country') countryMessages[activeEditorKey.value] = next
  else welcomeMessages[activeEditorKey.value] = next
  await nextTick()
  editor.focus()
  editor.setSelectionRange(start + token.length, start + token.length)
}

async function saveWelcomeSetting() {
  const normalized = { 'zh-CN': welcomeMessages['zh-CN'].trim(), 'en-US': welcomeMessages['en-US'].trim() }
  welcomeError.value = ''
  welcomeSuccess.value = ''
  if (Object.values(normalized).some((message) => !message || message.length > 1000)) {
    welcomeError.value = '中文和英文欢迎语均不能为空，且长度必须为1至1000个字符。'
    return
  }
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome', {
      messages: normalized,
      countryMessages: { ...countryMessages },
      expectedVersion: welcomeVersion.value,
    })
    const data = (response.data.data ?? {}) as DataObject
    const messages = (data.messages ?? normalized) as Record<string, string>
    welcomeMessages['zh-CN'] = String(messages['zh-CN'] ?? normalized['zh-CN'])
    welcomeMessages['en-US'] = String(messages['en-US'] ?? normalized['en-US'])
    Object.keys(countryMessages).forEach((key) => delete countryMessages[key])
    Object.assign(countryMessages, (data.countryMessages ?? countryMessages) as Record<string, string>)
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    welcomeSuccess.value = '新生欢迎语已保存。'
  } catch (reason) {
    welcomeError.value = translateError(reason)
  } finally {
    welcomeSaving.value = false
  }
}

function addCountryWelcome() {
  const code = newCountryCode.value.trim().toUpperCase()
  if (!code || countryMessages[code] !== undefined) return
  countryMessages[code] = welcomeMessages['en-US']
}

function removeCountryWelcome(code: string) {
  delete countryMessages[code]
}

function auditAction(value: unknown) {
  const labels: Record<string, string> = {
    CREATE: '新增了', UPDATE: '修改了', DELETE: '删除了', IMPORT: '导入了',
    LOGIN: '登录了系统', LOGOUT: '退出了系统', BED_ASSIGN_SELF: '确认了学生床位',
    RESIDENCY_ROOM_ASSIGN: '确认了学生寝室', SYSTEM_SETTING_UPDATE: '更新了系统设置',
    QUESTIONNAIRE_SUBMIT: '提交了个人偏好', BATCH_PUBLISH: '发布了选寝批次',
  }
  const key = String(value ?? '')
  return labels[key] ?? (key.includes('UPDATE') ? '修改了' : key.includes('CREATE') ? '新增了' : '完成了一项操作')
}

function auditResource(value: unknown) {
  const labels: Record<string, string> = {
    STUDENT: '学生资料', MAJOR: '专业信息', ROOM: '宿舍房间', BED: '床位信息',
    SELECTION_BATCH: '选寝批次', ROOM_ASSIGNMENT: '寝室分配', BED_ASSIGNMENT: '床位分配',
    SYSTEM_SETTING: '系统设置', QUESTIONNAIRE: '偏好问卷', TEAM: '学生队伍',
  }
  return labels[String(value ?? '')] ?? '业务数据'
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">{{ subtitle('运行概览', 'OPERATIONS OVERVIEW') }}</span>
        <h2>宿舍管理运行概览</h2>
        <p>集中查看学生、宿舍、选寝批次与分配工作的最新情况。</p>
      </div>
      <button class="button ghost" @click="load">刷新数据</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="loading" class="panel empty-state">正在汇总管理数据…</p>

    <template v-else>
      <div class="stat-grid dashboard-stat-grid">
        <article v-for="stat in stats" :key="stat[0]" class="panel stat-card dashboard-stat-card">
          <span>{{ stat[1] }}</span>
          <div class="stat-value-line"><strong>{{ dashboard[stat[0]] ?? 0 }}</strong><small>{{ stat[2] }}</small></div>
        </article>
      </div>

      <section class="panel welcome-setting-card">
        <div class="section-head split-title">
          <div>
            <span class="eyebrow">{{ subtitle('首次登录欢迎', 'FIRST LOGIN WELCOME') }}</span>
            <h3>新生欢迎语</h3>
            <p>中文可自动生成英文版本，也可以分别修改；将光标放在文字中间后点击学生信息即可插入。</p>
          </div>
          <label class="auto-translate-switch"><input v-model="autoTranslate" type="checkbox" /><span>英文跟随中文自动更新</span></label>
        </div>

        <div class="placeholder-toolbar">
          <span>插入学生信息</span>
          <button v-for="name in placeholders" :key="name" type="button" @click="insertPlaceholder(name)">{{ name }}</button>
        </div>

        <div class="multilingual-welcome-grid">
          <label>
            <span>中文欢迎语</span><small>{{ welcomeMessages['zh-CN'].length }}/1000</small>
            <textarea v-model="welcomeMessages['zh-CN']" class="input welcome-message-input" rows="5" maxlength="1000" placeholder="请输入中文首次登录欢迎文本" @focus="setActiveEditor($event, 'message', 'zh-CN')" />
          </label>
          <label>
            <span>英文欢迎语</span><small>{{ welcomeMessages['en-US'].length }}/1000</small>
            <textarea v-model="welcomeMessages['en-US']" class="input welcome-message-input" rows="5" maxlength="1000" placeholder="Enter the English welcome message" @focus="setActiveEditor($event, 'message', 'en-US')" @input="markEnglishEdited" />
            <button class="text-button translate-button" type="button" :disabled="translating" @click="translateChinese(true)">{{ translating ? '正在翻译…' : '根据中文重新翻译' }}</button>
          </label>
        </div>

        <section class="country-welcome-section">
          <div class="section-head split-title">
            <div><strong>按国家或地区设置欢迎语</strong><p>专属欢迎语优先显示；没有专属内容时使用中文或英文通用版本。</p></div>
            <div class="button-row"><select v-model="newCountryCode" class="input"><option v-for="country in countryOptions" :key="country.code" :value="country.code">{{ country.name }}</option></select><button class="button secondary" @click="addCountryWelcome">添加</button></div>
          </div>
          <div v-if="Object.keys(countryMessages).length" class="country-welcome-list">
            <label v-for="(_, code) in countryMessages" :key="code"><span>{{ countryLabel(code) }}</span><textarea v-model="countryMessages[code]" class="input" rows="3" maxlength="1000" :placeholder="`请输入${countryLabel(code)}学生的欢迎语`" @focus="setActiveEditor($event, 'country', String(code))" /><button class="text-button danger-text" type="button" @click="removeCountryWelcome(String(code))">删除该专属欢迎语</button></label>
          </div>
          <p v-else class="empty-state">尚未设置国家或地区专属欢迎语。</p>
        </section>

        <div class="welcome-setting-meta"><span v-if="welcomeUpdatedAt">最后修改：{{ welcomeUpdatedAt }}</span><span v-if="welcomeUpdatedBy">修改人：{{ welcomeUpdatedBy }}</span></div>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
        <p v-if="welcomeSuccess" class="alert success">{{ welcomeSuccess }}</p>
        <div class="button-row welcome-setting-actions"><button class="button ghost" :disabled="welcomeSaving" @click="loadWelcomeSetting">重新加载</button><button class="button primary" :disabled="welcomeSaving" @click="saveWelcomeSetting">{{ welcomeSaving ? '正在保存…' : '保存欢迎语' }}</button></div>
      </section>

      <div class="admin-grid">
        <section class="panel span-2">
          <div class="section-head"><div><span class="eyebrow">{{ subtitle('选寝批次', 'BATCHES') }}</span><h3>最近选寝批次</h3></div><RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink></div>
          <div class="table-wrap"><table><thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead><tbody><tr v-for="batch in batches.slice(0, 6)" :key="String(batch.id)"><td><strong>{{ batch.batch_name }}</strong><small>{{ batch.batch_code }}</small></td><td><span class="status-chip compact">{{ batch.batch_status }}</span></td><td>{{ batch.eligible_count }}</td><td>{{ batch.assigned_count }}</td></tr></tbody></table></div>
        </section>

        <section class="panel">
          <div class="section-head"><div><span class="eyebrow">{{ subtitle('操作记录', 'ACTIVITY') }}</span><h3>最近操作</h3></div></div>
          <div class="audit-list friendly-audit-list"><article v-for="log in auditLogs" :key="String(log.id)"><span class="audit-dot" /><div><strong>{{ auditAction(log.action_type) }}{{ auditResource(log.resource_type) }}</strong><p>{{ log.operator_name || log.operator_display_name || '系统管理员' }} · {{ log.created_at || log.occurred_at || '刚刚' }}</p></div></article></div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard-stat-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.dashboard-stat-card { min-width: 0; }
.stat-value-line { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-top: 12px; }
.stat-value-line strong { font-size: clamp(1.9rem, 3vw, 2.7rem); line-height: 1; }
.stat-value-line small { margin-left: auto; color: var(--muted); font-size: .82rem; font-weight: 700; }
.auto-translate-switch { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: .82rem; }
.placeholder-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; padding: 12px; border-radius: 13px; background: var(--soft); }
.placeholder-toolbar > span { margin-right: 4px; color: var(--muted); font-size: .76rem; font-weight: 700; }
.placeholder-toolbar button { padding: 6px 10px; border: 1px solid #cfdaf0; border-radius: 999px; color: #315c9e; background: #fff; cursor: pointer; }
.translate-button { justify-self: start; margin-top: 4px; }
.country-welcome-section { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--line); }
.country-welcome-list { display: grid; grid-template-columns: repeat(auto-fit,minmax(300px,1fr)); gap: 12px; }
.country-welcome-list label { display: grid; gap: 7px; padding: 14px; border: 1px solid var(--line); border-radius: 13px; }
.danger-text { color: #b91c1c; justify-self: start; }
.friendly-audit-list strong { line-height: 1.45; }
@media (max-width: 1050px) { .dashboard-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .dashboard-stat-grid { grid-template-columns: 1fr; } }
</style>
