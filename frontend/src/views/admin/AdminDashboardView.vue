<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')
const saving = ref(false)
const welcomeVersion = ref(0)
const newLocale = ref('ja-JP')
const languageMessages = reactive<Record<string, string>>({ 'zh-CN': '', 'en-US': '' })
const { subtitle, translateError } = useI18n()
const requiredLocales = new Set(['zh-CN', 'en-US'])
const welcomeLanguageOptions = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR', 'fr-FR', 'de-DE', 'es-ES', 'ru-RU']
const orderedLocales = computed(() => Object.keys(languageMessages).sort((a, b) => {
  const rank = (value: string) => value === 'zh-CN' ? 0 : value === 'en-US' ? 1 : 2
  return rank(a) - rank(b) || a.localeCompare(b)
}))
const stats = [
  ['studentCount', '学生总数'], ['roomCount', '宿舍房间'],
  ['bedCount', '启用床位'], ['activeAssignmentCount', '已完成分配'],
]

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [summary, batchList, welcome] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/dashboard'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome'),
    ])
    dashboard.value = (summary.data.data ?? {}) as DataObject
    batches.value = ((batchList.data.data ?? []) as DataObject[]).slice(0, 6)
    const data = (welcome.data.data ?? {}) as DataObject
    const messages = (data.messages ?? {}) as Record<string, string>
    Object.keys(languageMessages).forEach(key => delete languageMessages[key])
    Object.assign(languageMessages, messages)
    languageMessages['zh-CN'] ||= String(data.message ?? '')
    languageMessages['en-US'] ||= ''
    welcomeVersion.value = Number(data.version ?? 0)
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    loading.value = false
  }
}

function normalizeLocale(value: string) {
  const parts = value.trim().replaceAll('_', '-').split('-').filter(Boolean)
  if (!/^[A-Za-z]{2,3}$/.test(parts[0] ?? '')) return ''
  return parts.map((part, index) => index === 0
    ? part.toLowerCase()
    : part.length === 4
      ? `${part[0].toUpperCase()}${part.slice(1).toLowerCase()}`
      : part.toUpperCase()).join('-')
}

function addWelcomeLanguage() {
  const locale = normalizeLocale(newLocale.value)
  if (!locale || languageMessages[locale] !== undefined) {
    error.value = locale ? '该语言版本已经存在。' : '请输入类似 ja-JP 的语言代码。'
    return
  }
  languageMessages[locale] = languageMessages['en-US']
  newLocale.value = ''
  error.value = ''
}

function removeWelcomeLanguage(locale: string) {
  if (!requiredLocales.has(locale)) delete languageMessages[locale]
}

async function saveWelcome() {
  error.value = ''
  success.value = ''
  const messages = Object.fromEntries(Object.entries(languageMessages)
    .map(([locale, value]) => [locale, value.trim()]))
  if (!messages['zh-CN'] || !messages['en-US']) {
    error.value = '中文和英文欢迎语必须填写。'
    return
  }
  if (Object.values(messages).some(value => !value || value.length > 1000)) {
    error.value = '每个语言版本长度必须为1至1000个字符。'
    return
  }
  saving.value = true
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/student-welcome', {
      messages,
      expectedVersion: welcomeVersion.value,
    })
    const data = (response.data.data ?? {}) as DataObject
    welcomeVersion.value = Number(data.version ?? welcomeVersion.value + 1)
    success.value = '欢迎语语言版本已保存。未配置的外文语言将展示英文版本。'
  } catch (cause) {
    error.value = translateError(cause)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div><span class="eyebrow">{{ subtitle('运行概览', 'OPERATIONS OVERVIEW') }}</span><h2>宿舍管理运行概览</h2><p>集中查看运行情况并维护新生首次登录欢迎语。</p></div>
      <button class="button secondary" @click="load">刷新</button>
    </header>
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="success" class="alert success">{{ success }}</p>
    <p v-if="loading" class="panel empty-state">正在加载…</p>
    <template v-else>
      <div class="stat-grid dashboard-stat-grid">
        <article v-for="item in stats" :key="item[0]" class="panel stat-card"><span>{{ item[1] }}</span><strong>{{ dashboard[item[0]] ?? 0 }}</strong></article>
      </div>

      <section class="panel welcome-language-panel">
        <div class="section-head"><div><span class="eyebrow">新生欢迎语</span><h3>管理员统一设置语言版本</h3><p>中文和英文为必填版本；学生选择的其他外文未配置时统一回退英文。</p></div></div>
        <div class="language-grid">
          <article v-for="locale in orderedLocales" :key="locale" class="language-card">
            <header><strong>{{ locale }}</strong><button v-if="!requiredLocales.has(locale)" type="button" class="text-button" @click="removeWelcomeLanguage(locale)">删除</button><span v-else>必填</span></header>
            <textarea v-model="languageMessages[locale]" class="input" rows="4" maxlength="1000" :placeholder="`请输入 ${locale} 欢迎语`" />
          </article>
        </div>
        <div class="add-language"><input v-model.trim="newLocale" class="input" list="welcome-locales" placeholder="新增语言，例如 ja-JP" @keyup.enter="addWelcomeLanguage"/><datalist id="welcome-locales"><option v-for="locale in welcomeLanguageOptions" :key="locale" :value="locale" /></datalist><button class="button secondary" type="button" @click="addWelcomeLanguage">添加语言</button><button class="button primary" :disabled="saving" @click="saveWelcome">{{ saving ? '保存中…' : '保存全部语言版本' }}</button></div>
      </section>

      <section class="panel">
        <div class="section-head"><div><span class="eyebrow">最近批次</span><h3>选寝批次</h3></div><RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead><tbody><tr v-for="batch in batches" :key="String(batch.id)"><td>{{ batch.batch_name }}</td><td>{{ batch.batch_status }}</td><td>{{ batch.eligible_count }}</td><td>{{ batch.assigned_count }}</td></tr></tbody></table></div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.language-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}.language-card{display:grid;gap:9px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.language-card header{display:flex;align-items:center;justify-content:space-between}.language-card header span{color:var(--muted);font-size:12px}.add-language{display:grid;grid-template-columns:minmax(220px,1fr) auto auto;gap:10px;margin-top:14px}.stat-card strong{font-size:28px}@media(max-width:720px){.add-language{grid-template-columns:1fr}.language-grid{grid-template-columns:1fr}}
</style>
