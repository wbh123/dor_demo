<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const languageMessages = reactive<Record<string, string>>({ 'zh-CN': '', 'en-US': '' })
const newLocale = ref('ja-JP')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const welcomeSaving = ref(false)
const welcomeError = ref('')
const welcomeSuccess = ref('')
const activeEditor = ref<HTMLTextAreaElement | null>(null)
const activeLocale = ref('zh-CN')
const { subtitle, translateError } = useI18n()

const placeholders = ['学生姓名', '学号', '专业名称', '年级', '培养层次', '国家或地区']
const requiredLocales = new Set(['zh-CN', 'en-US'])
const welcomeLanguageOptions = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
  { value: 'ja-JP', label: '日本語' },
  { value: 'ko-KR', label: '한국어' },
  { value: 'fr-FR', label: 'Français' },
  { value: 'de-DE', label: 'Deutsch' },
  { value: 'es-ES', label: 'Español' },
  { value: 'ru-RU', label: 'Русский' },
]
const stats = [
  ['studentCount', '学生总数', '人'],
  ['roomCount', '宿舍房间', '间'],
  ['bedCount', '启用床位', '个'],
  ['activeAssignmentCount', '已完成分配', '人'],
]
const orderedLocales = computed(() => Object.keys(languageMessages).sort((left, right) => {
  const priority = (value: string) => value === 'zh-CN' ? 0 : value === 'en-US' ? 1 : 2
  return priority(left) - priority(right) || left.localeCompare(right)
}))

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
    Object.keys(languageMessages).forEach((key) => delete languageMessages[key])
    Object.assign(languageMessages, messages)
    languageMessages['zh-CN'] = String(languageMessages['zh-CN'] ?? data.message ?? '')
    languageMessages['en-US'] = String(languageMessages['en-US'] ?? '')
    welcomeVersion.value = Number(data.version ?? 0)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
  } catch (reason) {
    welcomeError.value = translateError(reason)
  }
}

function normalizeLocale(value: string) {
  const parts = value.trim().replaceAll('_', '-').split('-').filter(Boolean)
  if (!/^[A-Za-z]{2,3}$/.test(parts[0] ?? '')) return ''
  return parts.map((part, index) => {
    if (index === 0) return part.toLowerCase()
    if (part.length === 4) return `${part[0].toUpperCase()}${part.slice(1).toLowerCase()}`
    return part.toUpperCase()
  }).join('-')
}

function addWelcomeLanguage() {
  const locale = normalizeLocale(newLocale.value)
  welcomeError.value = ''
  if (!locale || !/^[a-z]{2,3}(?:-[A-Za-z0-9]{2,4}){0,2}$/.test(locale)) {
    welcomeError.value = '请输入类似 ja-JP、fr-FR 的语言代码。'
    return
  }
  if (languageMessages[locale] !== undefined) {
    welcomeError.value = '该语言版本已经存在。'
    return
  }
  languageMessages[locale] = languageMessages['en-US']
  newLocale.value = ''
}

function removeWelcomeLanguage(locale: string) {
  if (requiredLocales.has(locale)) return
  delete languageMessages[locale]
}

function localeLabel(locale: string) {
  return welcomeLanguageOptions.find((item) => item.value === locale)?.label ?? locale
}

function setActiveEditor(event: FocusEvent, locale: string) {
  activeEditor.value = event.target as HTMLTextAreaElement
  activeLocale.value = locale
}

async function insertPlaceholder(name: string) {
  const token = `{{${name}}}`
  const editor = activeEditor.value
  const locale = activeLocale.value
  if (!editor) {
    languageMessages['zh-CN'] += token
    return
  }
  const current = String(languageMessages[locale] ?? '')
  const start = editor.selectionStart ?? current.length
  const end = editor.selectionEnd ?? start
  languageMessages[locale] = `${current.slice(0, start)}${token}${current.slice(end)}`
  await nextTick()
  editor.focus()
  editor.setSelectionRange(start + token.length, start + token.length)
}

async function saveWelcomeSetting() {
  welcomeError.value = ''
  welcomeSuccess.value = ''
  const normalized = Object.fromEntries(
    Object.entries(languageMessages).map(([locale, message]) => [locale, message.trim()]),
  )
  if (!normalized['zh-CN'] || !normalized['en-US']) {
    welcomeError.value = '中文和英文是必填的基础版本。'
    return
  }
  if (Object.values(normalized).some((message) => !message || message.length > 1000)) {
    welcomeError.value = '每个语言版本长度必须为1至1000个字符。'
    return
  }
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome', {
      messages: normalized,
      expectedVersion: welcomeVersion.value,
    })
    const data = (response.data.data ?? {}) as DataObject
    const savedMessages = (data.messages ?? normalized) as Record<string, string>
    Object.keys(languageMessages).forEach((key) => delete languageMessages[key])
    Object.assign(languageMessages, savedMessages)
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    welcomeSuccess.value = '新生欢迎语语言版本已保存。未配置的外文语言将展示英文版本。'
  } catch (reason) {
    welcomeError.value = translateError(reason)
  } finally {
    welcomeSaving.value = false
  }
}

function auditAction(value: unknown) {
  const labels: Record<string, string> = {
    CREATE: '新增了', UPDATE: '修改了', DELETE: '删除了', IMPORT: '导入了',
    LOGIN: '登录了系统', LOGOUT: '退出了系统', SYSTEM_SETTING_UPDATE: '更新了系统设置',
  }
  const key = String(value ?? '')
  return labels[key] ?? (key.includes('UPDATE') ? '修改了' : key.includes('CREATE') ? '新增了' : '完成了一项操作')
}

function auditResource(value: unknown) {
  const labels: Record<string, string> = {
    STUDENT: '学生资料', MAJOR: '专业信息', ROOM: '宿舍房间', BED: '床位信息',
    SELECTION_BATCH: '选寝批次', ROOM_ASSIGNMENT: '寝室分配', SYSTEM_SETTING: '系统设置',
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
            <h3>新生欢迎语语言版本</h3>
            <p>由管理员统一维护。中文和英文为基础版本；学生选择的外文未配置时，系统统一展示英文提示语。</p>
          </div>
        </div>

        <div class="placeholder-toolbar">
          <span>插入学生信息</span>
          <button v-for="name in placeholders" :key="name" type="button" @click="insertPlaceholder(name)">{{ name }}</button>
        </div>

        <div class="language-editor-grid">
          <article v-for="localeCode in orderedLocales" :key="localeCode" class="language-editor-card">
            <header>
              <div><strong>{{ localeLabel(localeCode) }}</strong><small>{{ localeCode }}</small></div>
              <button v-if="!requiredLocales.has(localeCode)" class="text-button danger-text" type="button" @click="removeWelcomeLanguage(localeCode)">删除语言</button>
              <span v-else class="required-chip">必填</span>
            </header>
            <textarea v-model="languageMessages[localeCode]" class="input" rows="5" maxlength="1000" :placeholder="`请输入 ${localeCode} 欢迎语`" @focus="setActiveEditor($event, localeCode)" />
            <small>{{ languageMessages[localeCode]?.length ?? 0 }}/1000</small>
          </article>
        </div>

        <div class="language-add-row">
          <label><span>新增语言代码</span><input v-model.trim="newLocale" class="input" list="welcome-language-list" placeholder="例如 ja-JP" @keyup.enter="addWelcomeLanguage" /></label>
          <datalist id="welcome-language-list"><option v-for="option in welcomeLanguageOptions" :key="option.value" :value="option.value">{{ option.label }}</option></datalist>
          <button class="button secondary" type="button" @click="addWelcomeLanguage">添加语言版本</button>
        </div>

        <div class="welcome-setting-meta"><span v-if="welcomeUpdatedAt">最后修改：{{ welcomeUpdatedAt }}</span><span v-if="welcomeUpdatedBy">修改人：{{ welcomeUpdatedBy }}</span></div>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
        <p v-if="welcomeSuccess" class="alert success">{{ welcomeSuccess }}</p>
        <div class="button-row welcome-setting-actions"><button class="button ghost" :disabled="welcomeSaving" @click="loadWelcomeSetting">重新加载</button><button class="button primary" :disabled="welcomeSaving" @click="saveWelcomeSetting">{{ welcomeSaving ? '正在保存…' : '保存全部语言版本' }}</button></div>
      </section>

      <div class="admin-grid">
        <section class="panel span-2">
          <div class="section-head"><div><span class="eyebrow">{{ subtitle('选寝批次', 'BATCHES') }}</span><h3>最近选寝批次</h3></div><RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink></div>
          <div class="table-wrap"><table><thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead><tbody><tr v-for="batch in batches.slice(0, 6)" :key="String(batch.id)"><td><strong>{{ batch.batch_name }}</strong><small>{{ batch.batch_code }}</small></td><td><span class="status-chip compact">{{ batch.batch_status }}</span></td><td>{{ batch.eligible_count }}</td><td>{{ batch.assigned_count }}</td></tr></tbody></table></div>
        </section>

        <section class="panel">
          <div class="section-head"><div><span class="eyebrow">{{ subtitle('操作记录', 'ACTIVITY') }}</span><h3>最近操作</h3></div></div>
          <div class="audit-list friendly-audit-list"><article v-for="log in auditLogs" :key="String(log.id)"><span class="audit-dot" /><div><strong>{{ auditAction(log.action_type) }}{{ auditResource(log.resource_type) }}</strong><p>{{ log.operator_display_name || '系统' }} · {{ log.created_at }}</p></div></article><p v-if="!auditLogs.length" class="empty-state">暂无操作记录。</p></div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.language-editor-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:14px}.language-editor-card{display:grid;gap:10px;padding:15px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.language-editor-card header{display:flex;align-items:center;justify-content:space-between;gap:10px}.language-editor-card header div{display:grid;gap:2px}.language-editor-card header small,.language-editor-card>small{color:var(--muted)}.required-chip{padding:4px 8px;border-radius:999px;color:#17664f;background:#e8f8f2;font-size:12px}.language-add-row{display:flex;align-items:end;gap:10px;margin-top:15px}.language-add-row label{display:grid;gap:6px;min-width:240px}.welcome-setting-meta{display:flex;gap:18px;margin-top:14px;color:var(--muted);font-size:12px}.welcome-setting-actions{justify-content:flex-end;margin-top:14px}.danger-text{color:var(--danger)}@media(max-width:680px){.language-add-row{display:grid}.language-add-row label{min-width:0}.language-editor-grid{grid-template-columns:1fr}}
</style>
