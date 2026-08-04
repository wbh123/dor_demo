<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import CountryWelcomeEditor from '../../components/admin/CountryWelcomeEditor.vue'
import WelcomeMessageEditor from '../../components/admin/WelcomeMessageEditor.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { useI18n } from '../../i18n'

interface BrowserTranslator { translate(text: string): Promise<string> }
interface BrowserTranslatorFactory { create(options: { sourceLanguage: string; targetLanguage: string }): Promise<BrowserTranslator> }
type ActiveWelcomeEditor = 'zh' | 'en' | 'country'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const welcomeError = ref('')
const welcomeMessage = ref('')
const welcomeSaving = ref(false)
const translating = ref(false)
const selectedCountry = ref('')
const welcomeVersion = ref(0)
const welcomeUpdatedAt = ref('')
const welcomeUpdatedBy = ref('')
const zhEditor = ref<InstanceType<typeof WelcomeMessageEditor> | null>(null)
const enEditor = ref<InstanceType<typeof WelcomeMessageEditor> | null>(null)
const countryEditor = ref<InstanceType<typeof CountryWelcomeEditor> | null>(null)
const activeWelcomeEditor = ref<ActiveWelcomeEditor>('zh')
const baseMessages = reactive({ 'zh-CN': '', 'en-US': '' })
const countryMessages = ref<Record<string, string>>({})
const { subtitle, translateError } = useI18n()
const { hasFeature } = useFeatureAccess()
const multilingualWelcomeEnabled = computed(() => hasFeature('P2_MULTILINGUAL_INTERFACE'))

const tokenExamples = {
  学生姓名: '例如：张三', 学号: '例如：202600000001', 专业名称: '例如：软件工程',
  年级: '例如：2026级', 培养层次: '例如：硕士生', 国家或地区: '例如：日本',
}
const stats = [
  ['studentCount', '学生总数', '人'], ['roomCount', '宿舍房间', '间'],
  ['bedCount', '启用床位', '个'], ['activeAssignmentCount', '已完成分配', '人'],
]

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    const [dashboardResponse, batchesResponse, auditResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/dashboard'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/audit-logs?limit=8'),
    ])
    dashboard.value = (dashboardResponse.data.data ?? {}) as DataObject
    batches.value = (batchesResponse.data.data ?? []) as DataObject[]
    auditLogs.value = (auditResponse.data.data ?? []) as DataObject[]
  } catch (reason) { error.value = translateError(reason) }
  finally { loading.value = false }
  await loadWelcomeSetting()
}

async function loadWelcomeSetting() {
  welcomeError.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome')
    const data = (response.data.data ?? {}) as DataObject
    const messages = (data.messages ?? {}) as Record<string, string>
    baseMessages['zh-CN'] = String(messages['zh-CN'] ?? data.message ?? '')
    baseMessages['en-US'] = String(messages['en-US'] ?? '')
    countryMessages.value = { ...((data.countryMessages ?? {}) as Record<string, string>) }
    selectedCountry.value = Object.keys(countryMessages.value)[0] ?? ''
    welcomeVersion.value = Number(data.version ?? 0)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
  } catch (reason) { welcomeError.value = translateError(reason) }
}

function selectWelcomeEditor(editor: ActiveWelcomeEditor) { activeWelcomeEditor.value = editor }
function insertWelcomeToken(name: string) {
  if (activeWelcomeEditor.value === 'en' && multilingualWelcomeEnabled.value) enEditor.value?.insertToken(name)
  else if (activeWelcomeEditor.value === 'country' && multilingualWelcomeEnabled.value) countryEditor.value?.insertToken(name)
  else { activeWelcomeEditor.value = 'zh'; zhEditor.value?.insertToken(name) }
}

async function translateChinese() {
  if (!multilingualWelcomeEnabled.value) return
  const source = baseMessages['zh-CN'].trim()
  if (!source || translating.value) return
  const factory = (window as Window & { Translator?: BrowserTranslatorFactory }).Translator
  if (!factory?.create) { welcomeError.value = '当前浏览器未启用本地翻译能力，请直接编辑英语卡片中的欢迎语。'; return }
  translating.value = true
  try {
    const translator = await factory.create({ sourceLanguage: 'zh', targetLanguage: 'en' })
    baseMessages['en-US'] = await translator.translate(source)
  } catch { welcomeError.value = '自动翻译暂时不可用，请直接编辑英文欢迎语。' }
  finally { translating.value = false }
}

async function saveWelcomeSetting() {
  welcomeError.value = ''; welcomeMessage.value = ''
  const chinese = baseMessages['zh-CN'].trim()
  const english = baseMessages['en-US'].trim()
  if (!chinese || (multilingualWelcomeEnabled.value && !english)) {
    welcomeError.value = multilingualWelcomeEnabled.value ? '汉语和英语两个基础欢迎语均为必填项。' : '汉语欢迎语为必填项。'
    return
  }
  const countries = Object.fromEntries(Object.entries(countryMessages.value)
    .map(([code, message]) => [code, message.trim()]).filter(([, message]) => Boolean(message)))
  welcomeSaving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome', {
      messages: { 'zh-CN': chinese, 'en-US': english },
      countryMessages: countries,
      expectedVersion: welcomeVersion.value,
    })
    const data = (response.data.data ?? {}) as DataObject
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    welcomeUpdatedAt.value = String(data.updated_at ?? '')
    welcomeUpdatedBy.value = String(data.updated_by_name ?? '')
    countryMessages.value = countries
    welcomeMessage.value = multilingualWelcomeEnabled.value ? '全部欢迎语已保存。' : '中文欢迎语已保存。'
  } catch (reason) { welcomeError.value = translateError(reason) }
  finally { welcomeSaving.value = false }
}

function batchStatus(value: unknown) {
  return ({ DRAFT:'草稿',PUBLISHED:'已发布',OPEN:'进行中',CLOSED:'已关闭',ALLOCATING:'分配中',FINISHED:'已完成',CANCELLED:'已取消' } as Record<string,string>)[String(value)] ?? String(value ?? '-')
}
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title"><div><span class="eyebrow">{{ subtitle('运行概览','OPERATIONS OVERVIEW') }}</span><h2>宿舍管理运行概览</h2><p>集中查看学生、宿舍、选寝批次与分配工作的最新情况。</p></div><button class="button ghost" @click="load">刷新数据</button></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="loading" class="panel empty-state">正在汇总管理数据…</p>
    <template v-else>
      <div class="stat-grid dashboard-stat-grid"><article v-for="stat in stats" :key="stat[0]" class="panel stat-card dashboard-stat-card"><span>{{ stat[1] }}</span><div class="stat-value-line"><strong>{{ dashboard[stat[0]] ?? 0 }}</strong><small>{{ stat[2] }}</small></div></article></div>
      <section class="panel welcome-setting-card">
        <div class="section-head"><div><span class="eyebrow">首次登录欢迎</span><h3>新生欢迎语</h3><p>{{ multilingualWelcomeEnabled ? '基础卡片按语言展示，其他国家或地区可单独配置；未配置时自动使用英语欢迎语。' : '当前系统权限仅开放中文欢迎语设置。' }}</p></div></div>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p><p v-if="welcomeMessage" class="alert success">{{ welcomeMessage }}</p>
        <div class="welcome-token-toolbar"><span>插入学生信息</span><button v-for="(example,name) in tokenExamples" :key="name" type="button" :title="example" @mousedown.prevent @click="insertWelcomeToken(String(name))">{{ name }}</button></div>
        <div class="base-country-grid" :class="{single:!multilingualWelcomeEnabled}">
          <article class="base-country-card chinese"><header><div><strong>汉语</strong><small>汉语基础欢迎语</small></div><span class="required-chip">必填</span></header><WelcomeMessageEditor ref="zhEditor" v-model="baseMessages['zh-CN']" :token-examples="tokenExamples" placeholder="填写默认汉语欢迎语" @focus="selectWelcomeEditor('zh')" /></article>
          <article v-if="multilingualWelcomeEnabled" class="base-country-card english"><header><div><strong>英语</strong><small>其他国家或地区的默认回退</small></div><span class="required-chip">必填</span></header><WelcomeMessageEditor ref="enEditor" v-model="baseMessages['en-US']" :token-examples="tokenExamples" placeholder="Enter the default English welcome message" @focus="selectWelcomeEditor('en')" /><button class="button ghost translate-button" type="button" :disabled="translating" @click="translateChinese">{{ translating ? '正在翻译…' : '根据汉语欢迎语自动翻译' }}</button></article>
        </div>
        <div v-if="multilingualWelcomeEnabled" class="additional-country-section"><div><h4>其他国家或地区</h4><p>选择已配置国家进行修改，或从尚未配置的国家列表中添加。</p></div><CountryWelcomeEditor ref="countryEditor" v-model="countryMessages" v-model:selected-country="selectedCountry" :token-examples="tokenExamples" @focus="selectWelcomeEditor('country')" /></div>
        <div class="welcome-meta"><span>版本 {{ welcomeVersion }}</span><span v-if="welcomeUpdatedAt">最近更新 {{ welcomeUpdatedAt }}</span><span v-if="welcomeUpdatedBy">操作人 {{ welcomeUpdatedBy }}</span></div>
        <div class="button-row welcome-actions"><button class="button primary" :disabled="welcomeSaving" @click="saveWelcomeSetting">{{ welcomeSaving ? '正在保存…' : multilingualWelcomeEnabled ? '保存全部欢迎语' : '保存中文欢迎语' }}</button></div>
      </section>
      <div class="dashboard-lower-grid"><section class="panel"><div class="section-head"><div><span class="eyebrow">选寝批次</span><h3>最近批次</h3></div></div><div class="compact-list"><article v-for="batch in batches.slice(0,6)" :key="String(batch.id)"><strong>{{ batch.batch_name }}</strong><span>{{ batchStatus(batch.batch_status) }}</span></article><p v-if="!batches.length" class="empty-state">暂无选寝批次。</p></div></section><section class="panel"><div class="section-head"><div><span class="eyebrow">最近操作</span><h3>业务操作记录</h3></div></div><div class="compact-list"><article v-for="log in auditLogs" :key="String(log.id)"><strong>{{ log.operator_name || '系统' }}</strong><span>{{ log.action || log.operation_type || '完成业务操作' }}</span></article><p v-if="!auditLogs.length" class="empty-state">暂无操作记录。</p></div></section></div>
    </template>
  </div>
</template>

<style scoped>
.stat-value-line{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.stat-value-line small{color:var(--muted)}.welcome-token-toolbar{display:flex;align-items:center;gap:7px;flex-wrap:wrap;margin-bottom:12px;padding:8px 10px;border:1px solid var(--line);border-radius:12px;background:var(--panel,#fff)}.welcome-token-toolbar>span{margin-right:3px;color:var(--muted);font-size:12px;font-weight:700}.welcome-token-toolbar button{padding:4px 9px;border:1px solid #bed5ff;border-radius:999px;color:#245da8;background:#eef5ff;font-size:12px;cursor:pointer}.base-country-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;align-items:stretch}.base-country-grid.single{grid-template-columns:minmax(0,1fr)}.base-country-card{display:grid;grid-template-rows:auto 1fr;gap:12px;min-height:246px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.base-country-card.english{grid-template-rows:auto minmax(0,1fr) auto}.base-country-card.chinese :deep(.welcome-message-editor){min-height:166px}.base-country-card.english :deep(.welcome-message-editor){min-height:126px}.base-country-card header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.base-country-card header div{display:grid;gap:4px}.base-country-card small,.additional-country-section p,.welcome-meta{color:var(--muted)}.required-chip{padding:4px 8px;border-radius:999px;color:#17664f;background:#e8f8f2;font-size:12px}.translate-button{align-self:end}.additional-country-section{display:grid;gap:14px;margin-top:22px;padding-top:20px;border-top:1px solid var(--line)}.additional-country-section h4,.additional-country-section p{margin:0}.additional-country-section p{margin-top:5px}.welcome-meta{display:flex;gap:18px;flex-wrap:wrap;margin-top:14px;font-size:12px}.welcome-actions{justify-content:flex-end;margin-top:14px}.dashboard-lower-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.compact-list{display:grid;gap:8px}.compact-list article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px;border-radius:12px;background:var(--soft)}.compact-list span{color:var(--muted)}@media(max-width:760px){.base-country-grid,.dashboard-lower-grid{grid-template-columns:1fr}}
</style>
