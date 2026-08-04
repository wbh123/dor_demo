<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import WelcomeMessageEditor from '../../components/admin/WelcomeMessageEditor.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'
import { countryLabel, countryOptions } from '../../utils/countries'

interface BrowserTranslator {
  translate(text: string): Promise<string>
}
interface BrowserTranslatorFactory {
  create(options: { sourceLanguage: string; targetLanguage: string }): Promise<BrowserTranslator>
}
interface WelcomeEditorExpose {
  insertToken(name: string): void
  focus(): void
}

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const languageMessages = reactive<Record<string, string>>({ 'zh-CN': '', 'en-US': '' })
const countryMessages = reactive<Record<string, string>>({})
const newLocale = ref('ja-JP')
const newCountryCode = ref('JP')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const welcomeSaving = ref(false)
const welcomeError = ref('')
const translating = ref(false)
const activeEditorKey = ref('language:zh-CN')
const toast = ref('')
const editorRefs = new Map<string, WelcomeEditorExpose>()
let toastTimer: number | undefined
const { subtitle, translateError } = useI18n()

const placeholders = [
  { name: '学生姓名', example: '例如：张三' },
  { name: '学号', example: '例如：202600000001' },
  { name: '专业名称', example: '例如：软件工程' },
  { name: '年级', example: '例如：2026级' },
  { name: '培养层次', example: '例如：硕士生' },
  { name: '国家或地区', example: '例如：中国大陆' },
]
const tokenExamples = Object.fromEntries(placeholders.map((item) => [item.name, item.example]))
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
const orderedCountryCodes = computed(() => Object.keys(countryMessages).sort((left, right) =>
  countryLabel(left).localeCompare(countryLabel(right), 'zh-CN'),
))
const availableCountryOptions = computed(() => countryOptions.filter((item) => countryMessages[item.code] === undefined))
const activeEditorLabel = computed(() => {
  const [type, code] = activeEditorKey.value.split(':')
  return type === 'country' ? `${countryLabel(code)}专属欢迎语` : localeLabel(code)
})

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
    const countries = (data.countryMessages ?? {}) as Record<string, string>
    replaceRecord(languageMessages, messages)
    languageMessages['zh-CN'] = String(languageMessages['zh-CN'] ?? data.message ?? '')
    languageMessages['en-US'] = String(languageMessages['en-US'] ?? '')
    replaceRecord(countryMessages, countries)
    welcomeVersion.value = Number(data.version ?? 0)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
  } catch (reason) {
    welcomeError.value = translateError(reason)
  }
}

function replaceRecord(target: Record<string, string>, source: Record<string, string>) {
  Object.keys(target).forEach((key) => delete target[key])
  Object.assign(target, source)
}

function setEditorRef(key: string, value: unknown) {
  if (value) editorRefs.set(key, value as WelcomeEditorExpose)
  else editorRefs.delete(key)
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
    welcomeError.value = '请选择或输入类似 ja-JP、fr-FR 的语言代码。'
    return
  }
  if (languageMessages[locale] !== undefined) {
    welcomeError.value = '该语言版本已经存在。'
    return
  }
  languageMessages[locale] = languageMessages['en-US']
  activeEditorKey.value = `language:${locale}`
}

function removeWelcomeLanguage(locale: string) {
  if (requiredLocales.has(locale)) return
  delete languageMessages[locale]
  if (activeEditorKey.value === `language:${locale}`) activeEditorKey.value = 'language:zh-CN'
}

function addCountryMessage() {
  const code = String(newCountryCode.value || '').toUpperCase()
  welcomeError.value = ''
  if (!code || countryMessages[code] !== undefined) {
    welcomeError.value = '请选择尚未添加的国家或地区。'
    return
  }
  countryMessages[code] = languageMessages['en-US']
  activeEditorKey.value = `country:${code}`
  newCountryCode.value = availableCountryOptions.value.find((item) => item.code !== code)?.code ?? ''
}

function removeCountryMessage(code: string) {
  delete countryMessages[code]
  if (activeEditorKey.value === `country:${code}`) activeEditorKey.value = 'language:zh-CN'
  if (!newCountryCode.value) newCountryCode.value = availableCountryOptions.value[0]?.code ?? ''
}

function localeLabel(locale: string) {
  return welcomeLanguageOptions.find((item) => item.value === locale)?.label ?? locale
}

function insertPlaceholder(name: string) {
  const editor = editorRefs.get(activeEditorKey.value) ?? editorRefs.get('language:zh-CN')
  editor?.insertToken(name)
}

async function translateChinese() {
  const source = String(languageMessages['zh-CN'] ?? '').trim()
  welcomeError.value = ''
  if (!source) {
    welcomeError.value = '请先填写中文欢迎语。'
    return
  }
  if (translating.value) return
  const factory = (window as Window & { Translator?: BrowserTranslatorFactory }).Translator
  if (!factory?.create) {
    welcomeError.value = '当前浏览器未启用本地翻译能力，请使用支持翻译接口的浏览器，或直接编辑英文欢迎语。'
    return
  }
  translating.value = true
  try {
    const translator = await factory.create({ sourceLanguage: 'zh', targetLanguage: 'en' })
    languageMessages['en-US'] = await translator.translate(source)
    activeEditorKey.value = 'language:en-US'
    showToast('英文欢迎语已根据中文重新翻译。')
  } catch {
    welcomeError.value = '自动翻译暂时不可用，请稍后重试或直接编辑英文欢迎语。'
  } finally {
    translating.value = false
  }
}

async function saveWelcomeSetting() {
  welcomeError.value = ''
  const normalizedMessages = Object.fromEntries(
    Object.entries(languageMessages).map(([locale, message]) => [locale, message.trim()]),
  ) as Record<string, string>
  const normalizedCountries = Object.fromEntries(
    Object.entries(countryMessages)
      .map(([code, message]) => [code.toUpperCase(), message.trim()])
      .filter(([, message]) => Boolean(message)),
  ) as Record<string, string>
  if (!normalizedMessages['zh-CN'] || !normalizedMessages['en-US']) {
    welcomeError.value = '中文和英文是必填的基础版本。'
    return
  }
  if (Object.values(normalizedMessages).some((message) => !message || message.length > 1000)
      || Object.values(normalizedCountries).some((message) => message.length > 1000)) {
    welcomeError.value = '每个语言或国家地区版本长度必须为1至1000个字符。'
    return
  }
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome', {
      messages: normalizedMessages,
      countryMessages: normalizedCountries,
      expectedVersion: welcomeVersion.value,
    })
    const data = (response.data.data ?? {}) as DataObject
    replaceRecord(languageMessages, (data.messages ?? normalizedMessages) as Record<string, string>)
    replaceRecord(countryMessages, (data.countryMessages ?? normalizedCountries) as Record<string, string>)
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    showToast('新生欢迎语已保存。国家或地区专属内容优先，未配置时展示所选语言或英文版本。')
  } catch (reason) {
    welcomeError.value = translateError(reason)
  } finally {
    welcomeSaving.value = false
  }
}

function showToast(value: string) {
  toast.value = value
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 3000)
}

function auditAction(value: unknown) {
  const labels: Record<string, string> = {
    CREATE: '新增了', UPDATE: '修改了', DELETE: '删除了', IMPORT: '导入了',
    LOGIN: '登录了系统', LOGOUT: '退出了系统', SYSTEM_SETTING_UPDATE: '更新了系统设置',
    STUDENT_CREATE: '录入了', STUDENT_UPDATE: '修改了', STUDENT_IMPORT: '导入了',
    ASSIGNMENT_ADJUST: '调整了', BATCH_PUBLISH: '发布了',
  }
  const key = String(value ?? '')
  return labels[key] ?? (key.includes('UPDATE') ? '修改了' : key.includes('CREATE') ? '新增了' : '完成了一项操作')
}

function auditResource(value: unknown) {
  const labels: Record<string, string> = {
    STUDENT: '学生资料', MAJOR: '专业信息', ROOM: '宿舍房间', BED: '床位信息',
    SELECTION_BATCH: '选寝批次', ROOM_ASSIGNMENT: '寝室分配', BED_ASSIGNMENT: '床位分配',
    SYSTEM_SETTING: '系统设置', IMPORT_TASK: '导入任务',
  }
  return labels[String(value ?? '')] ?? '业务数据'
}

function batchStatus(value: unknown) {
  return ({ DRAFT: '草稿', PUBLISHED: '已发布', OPEN: '进行中', CLOSED: '已关闭', ALLOCATING: '分配中', FINISHED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[String(value)] ?? String(value ?? '-')
}
</script>

<template>
  <div class="content-column">
    <div v-if="toast" class="floating-toast" role="status"><span>{{ toast }}</span><button type="button" aria-label="关闭提示" @click="toast = ''">×</button></div>

    <div class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('运行概览', 'OPERATIONS OVERVIEW') }}</span><h2>宿舍管理运行概览</h2><p>集中查看学生、宿舍、选寝批次与分配工作的最新情况。</p></div>
      <button class="button ghost" @click="load">刷新数据</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="loading" class="panel empty-state">正在汇总管理数据…</p>

    <template v-else>
      <div class="stat-grid dashboard-stat-grid">
        <article v-for="stat in stats" :key="stat[0]" class="panel stat-card dashboard-stat-card"><span>{{ stat[1] }}</span><div class="stat-value-line"><strong>{{ dashboard[stat[0]] ?? 0 }}</strong><small>{{ stat[2] }}</small></div></article>
      </div>

      <section class="panel welcome-setting-card">
        <div class="section-head split-title compact-section-head">
          <div><span class="eyebrow">{{ subtitle('首次登录欢迎', 'FIRST LOGIN WELCOME') }}</span><h3>新生欢迎语</h3><p>国家或地区专属欢迎语优先；未单独配置时按学生所选语言展示，仍未配置则回退到英文。</p></div>
        </div>

        <h4 class="editor-group-title">按语言设置</h4>
        <div class="language-editor-grid">
          <article v-for="localeCode in orderedLocales" :key="localeCode" class="language-editor-card">
            <header><div><strong>{{ localeLabel(localeCode) }}</strong><small>{{ localeCode }}</small></div><button v-if="!requiredLocales.has(localeCode)" class="text-button danger-text" type="button" @click="removeWelcomeLanguage(localeCode)">删除语言</button><span v-else class="required-chip">必填</span></header>
            <WelcomeMessageEditor :ref="(value) => setEditorRef(`language:${localeCode}`, value)" v-model="languageMessages[localeCode]" :token-examples="tokenExamples" :placeholder="`请输入 ${localeLabel(localeCode)} 欢迎语`" @focus="activeEditorKey = `language:${localeCode}`" />
            <footer><small>{{ languageMessages[localeCode]?.length ?? 0 }}/1000</small><button v-if="localeCode === 'en-US'" class="button secondary small translate-button" type="button" :disabled="translating" @click="translateChinese">{{ translating ? '正在翻译…' : '重新翻译' }}</button></footer>
          </article>
        </div>

        <div class="placeholder-toolbar">
          <span>插入学生信息</span>
          <button v-for="item in placeholders" :key="item.name" type="button" :title="item.example" @click="insertPlaceholder(item.name)">{{ item.name }}</button>
          <small>当前插入到：{{ activeEditorLabel }}</small>
        </div>

        <div class="language-add-row">
          <label><span>添加语言版本</span><select v-model="newLocale" class="input"><option v-for="option in welcomeLanguageOptions" :key="option.value" :value="option.value">{{ option.label }} · {{ option.value }}</option></select></label>
          <button class="button secondary" type="button" @click="addWelcomeLanguage">添加语言版本</button>
        </div>

        <div class="country-message-section">
          <h4 class="editor-group-title">按国家或地区设置</h4>
          <p>仅在需要与通用语言版本不同的内容时添加，例如为特定国家或地区补充报到说明。</p>
          <div class="country-add-row">
            <select v-model="newCountryCode" class="input" :disabled="!availableCountryOptions.length"><option v-for="country in availableCountryOptions" :key="country.code" :value="country.code">{{ country.name }} · {{ country.code }}</option></select>
            <button class="button secondary" type="button" :disabled="!newCountryCode" @click="addCountryMessage">添加国家或地区</button>
          </div>
          <div v-if="orderedCountryCodes.length" class="language-editor-grid country-editor-grid">
            <article v-for="countryCode in orderedCountryCodes" :key="countryCode" class="language-editor-card">
              <header><div><strong>{{ countryLabel(countryCode) }}</strong><small>{{ countryCode }} · 未配置语言时仍回退英文</small></div><button class="text-button danger-text" type="button" @click="removeCountryMessage(countryCode)">删除</button></header>
              <WelcomeMessageEditor :ref="(value) => setEditorRef(`country:${countryCode}`, value)" v-model="countryMessages[countryCode]" :token-examples="tokenExamples" :placeholder="`请输入 ${countryLabel(countryCode)} 专属欢迎语`" @focus="activeEditorKey = `country:${countryCode}`" />
              <footer><small>{{ countryMessages[countryCode]?.length ?? 0 }}/1000</small></footer>
            </article>
          </div>
        </div>

        <div class="welcome-setting-meta"><span v-if="welcomeUpdatedAt">最后修改：{{ welcomeUpdatedAt }}</span><span v-if="welcomeUpdatedBy">修改人：{{ welcomeUpdatedBy }}</span></div>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
        <div class="button-row welcome-setting-actions"><button class="button ghost" :disabled="welcomeSaving" @click="loadWelcomeSetting">重新加载</button><button class="button primary" :disabled="welcomeSaving" @click="saveWelcomeSetting">{{ welcomeSaving ? '正在保存…' : '保存全部欢迎语' }}</button></div>
      </section>

      <div class="admin-grid">
        <section class="panel span-2"><div class="section-head compact-section-head"><div><span class="eyebrow">{{ subtitle('选寝批次', 'BATCHES') }}</span><h3>最近选寝批次</h3></div><RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink></div><div class="table-wrap"><table><thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead><tbody><tr v-for="batch in batches.slice(0, 6)" :key="String(batch.id)"><td><strong>{{ batch.batch_name }}</strong><small>{{ batch.batch_code }}</small></td><td><span class="status-chip compact">{{ batchStatus(batch.batch_status) }}</span></td><td>{{ batch.eligible_count }}</td><td>{{ batch.assigned_count }}</td></tr></tbody></table></div></section>
        <section class="panel"><div class="section-head compact-section-head"><div><span class="eyebrow">{{ subtitle('操作记录', 'ACTIVITY') }}</span><h3>最近操作</h3></div></div><div class="audit-list friendly-audit-list"><article v-for="log in auditLogs" :key="String(log.id)"><span class="audit-dot" /><div><strong>{{ auditAction(log.action_type) }}{{ auditResource(log.resource_type) }}</strong><p>{{ log.operator_display_name || '系统' }} · {{ log.created_at }}</p></div></article><p v-if="!auditLogs.length" class="empty-state">暂无操作记录。</p></div></section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.compact-section-head{margin-bottom:12px}.editor-group-title{margin:0 0 10px}.language-editor-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:14px;align-items:stretch}.language-editor-card{display:grid;grid-template-rows:auto minmax(142px,1fr) auto;gap:10px;padding:15px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.language-editor-card header,.language-editor-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px}.language-editor-card header div{display:grid;gap:2px}.language-editor-card header small,.language-editor-card footer small{color:var(--muted)}.required-chip{padding:4px 8px;border-radius:999px;color:#17664f;background:#e8f8f2;font-size:12px}.translate-button{margin-left:auto}.placeholder-toolbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:14px;padding:11px 12px;border-radius:14px;background:var(--soft)}.placeholder-toolbar>span{color:var(--muted);font-size:13px;font-weight:700}.placeholder-toolbar button{padding:6px 10px;border:1px solid #c9daf6;border-radius:999px;color:#245da8;background:#fff;cursor:pointer}.placeholder-toolbar small{margin-left:auto;color:var(--muted)}.language-add-row,.country-add-row{display:flex;align-items:end;gap:10px;margin-top:15px}.language-add-row label{display:grid;grid-template-columns:minmax(150px,auto) minmax(260px,1fr);align-items:center;gap:10px;min-width:min(540px,100%)}.country-message-section{display:grid;gap:8px;margin-top:22px;padding-top:20px;border-top:1px solid var(--line)}.country-message-section>p{margin:0}.country-add-row .input{max-width:430px}.country-editor-grid{margin-top:8px}.welcome-setting-meta{display:flex;gap:18px;margin-top:14px;color:var(--muted);font-size:12px}.welcome-setting-actions{justify-content:flex-end;margin-top:14px}.danger-text{color:var(--danger)}.floating-toast{position:fixed;z-index:1600;top:22px;right:24px;display:flex;align-items:center;gap:14px;max-width:min(480px,calc(100vw - 32px));padding:13px 15px;border:1px solid #bfe8d4;border-radius:14px;color:#17664f;background:#effaf5;box-shadow:0 14px 36px rgba(17,70,52,.18)}.floating-toast button{border:0;color:inherit;background:transparent;font-size:20px;cursor:pointer}.stat-value-line{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.stat-value-line small{color:var(--muted)}@media(max-width:760px){.language-add-row,.language-add-row label,.country-add-row{display:grid;grid-template-columns:1fr;min-width:0}.language-editor-grid{grid-template-columns:1fr}.placeholder-toolbar small{width:100%;margin:0}.floating-toast{top:12px;right:12px}}
</style>
