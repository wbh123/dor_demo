<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client'
import type { DataObject, ObjectSuccessResponse } from '../api/types'
import { useI18n, type LocaleCode } from '../i18n'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const welcomeError = ref('')
const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')
const productName = String(import.meta.env.VITE_APP_TITLE || `${institutionName}选寝`)
const productSubtitle = String(import.meta.env.VITE_APP_SUBTITLE || '宿舍智能选择系统')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '运营单位信息待填写')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || 'ICP备案信息待填写')
const logoOnly = '/assert/logo-only.png'
const { locale, localeOptions, t, subtitle, setLocale, applyNationalityLocale, welcomeMessage, translateError } = useI18n()

const icons = {
  dashboard:'M3 3h8v8H3V3Zm10 0h8v5h-8V3ZM3 13h8v8H3v-8Zm10-3h8v11h-8V10Z',
  students:'M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8 2c-3.31 0-6 1.79-6 4v3h12v-3c0-2.21-2.69-4-6-4ZM8 14c-3.31 0-6 1.79-6 4v3h6v-3c0-1.5.64-2.85 1.72-3.93A9.2 9.2 0 0 0 8 14Z',
  dormitory:'M4 21V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v3h1a2 2 0 0 1 2 2v12h-2v-2H6v2H4Zm2-4h12V9h-3V4H6v13Zm2-11h2v2H8V6Zm4 0h2v2h-2V6Zm-4 4h2v2H8v-2Zm4 0h2v2h-2v-2Z',
  matching:'M4 7h10v2H4V7Zm14-3h2v8h-2V4ZM4 15h4v2H4v-2Zm8-3h2v8h-2v-8Zm6 3h2v2h-2v-2ZM8 4h2v8H8V4Zm8 8h2v8h-2v-8Z',
  rules:'M5 4h14v3H5V4Zm0 6h14v3H5v-3Zm0 6h14v3H5v-3Zm2-11v1h10V5H7Zm0 6v1h10v-1H7Zm0 6v1h10v-1H7Z',
  calendar:'M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h3V2Zm13 8H4v10h16V10ZM4 8h16V6H4v2Z',
  assignment:'M4 3h13a2 2 0 0 1 2 2v3h1a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-3H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm3 7v9h13v-9H7Zm2 2h9v2H9v-2Zm0 4h6v2H9v-2ZM4 5v9h1v-4a2 2 0 0 1 2-2h10V5H4Z',
  bedCheck:'M5 4h14v4H5V4Zm0 6h14v10H5V10Zm2 2v6h10v-6H7Zm2 1h6v2H9v-2Zm0 3h4v1H9v-1Z',
  change:'M7 7h11l-3-3 1.4-1.4L21.8 8l-5.4 5.4L15 12l3-3H7a3 3 0 0 0-3 3v1H2v-1a5 5 0 0 1 5-5Zm10 10H6l3 3-1.4 1.4L2.2 16l5.4-5.4L9 12l-3 3h11a3 3 0 0 0 3-3v-1h2v1a5 5 0 0 1-5 5Z',
  waitlist:'M12 2a7 7 0 0 0-7 7v3H3v2h2v5a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-5h2v-2h-2V9a7 7 0 0 0-7-7Zm0 2a5 5 0 0 1 5 5v3H7V9a5 5 0 0 1 5-5Zm-5 10h10v5a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-5Zm4-8h2v4h-2V6Z',
  operations:'M3 20h18v2H3v-2Zm2-2V9h3v9H5Zm5 0V3h3v15h-3Zm5 0v-6h3v6h-3Z',
  importQuality:'M4 3h10l6 6v12H4V3Zm9 2H6v14h12v-9h-5V5Zm2 1.5V8h1.5L15 6.5ZM8 12h8v2H8v-2Zm0 4h6v2H8v-2Z',
  anomaly:'M12 2 2 20h20L12 2Zm0 5.2L18.6 18H5.4L12 7.2ZM11 10v4h2v-4h-2Zm0 5.5v2h2v-2h-2Z',
  home:'m12 3 9 8h-3v10h-5v-6h-2v6H6V11H3l9-8Z',
  team:'M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM7 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm9 2c-3.31 0-6 1.79-6 4v3h12v-3c0-2.21-2.69-4-6-4ZM7 14c-2.76 0-5 1.57-5 3.5V21h6v-3c0-1.4.53-2.69 1.45-3.76A8.4 8.4 0 0 0 7 14Z',
}

const links = computed(() => auth.isAdmin ? [
  {to:'/admin',label:'工作台',icon:icons.dashboard},
  {to:'/admin/data',label:'专业与学生',icon:icons.students},
  {to:'/admin/import-quality',label:'导入质量',icon:icons.importQuality},
  {to:'/admin/dormitories',label:'宿舍资源',icon:icons.dormitory},
  {to:'/admin/residencies',label:'在住与床位核查',icon:icons.assignment},
  {to:'/admin/matching',label:'匹配规则',icon:icons.matching},
  {to:'/admin/rule-templates',label:'批次规则',icon:icons.rules},
  {to:'/admin/batches',label:'选寝批次',icon:icons.calendar},
  {to:'/admin/assignments',label:'分配与调整',icon:icons.assignment},
  {to:'/admin/room-change',label:'换寝管理',icon:icons.change},
  {to:'/admin/waitlist',label:'候补管理',icon:icons.waitlist},
  {to:'/admin/operations',label:'运营与健康',icon:icons.operations},
  {to:'/admin/anomalies',label:'异常工作台',icon:icons.anomaly},
] : [
  {to:'/student',label:'选寝首页',icon:icons.home},
  {to:'/student/teams',label:'我的队伍',icon:icons.team},
  {to:'/student/room-change',label:'申请换寝',icon:icons.change},
  {to:'/student/waitlist',label:'候补补位',icon:icons.waitlist},
])

const welcomeText = computed(() => {
  const welcome = auth.user?.welcome as (DataObject & { messages?: Record<string,string> }) | undefined
  return welcomeMessage(welcome?.messages)
})

onMounted(async () => {
  if (!auth.isStudent) return
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/student/profile')
    applyNationalityLocale(((response.data.data ?? {}) as DataObject).nationality_code)
  } catch {
    // 个人首页重新加载时仍会应用国籍语言回退。
  }
})

function changeLocale(event: Event) {
  setLocale((event.target as HTMLSelectElement).value as LocaleCode)
}

async function acknowledgeWelcome() {
  welcomeError.value = ''
  try { await auth.acknowledgeWelcome() }
  catch (reason) { welcomeError.value = translateError(reason) }
}

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell fixed-navigation-shell">
    <aside class="sidebar fixed-sidebar">
      <div class="brand school-brand logo-safe-layer">
        <img class="school-brand-logo logo-safe-layer" :src="logoOnly" :alt="`${institutionName}校徽`" />
        <div class="school-brand-title">
          <strong>{{ productName }}</strong>
          <small>{{ productSubtitle }}</small>
        </div>
      </div>
      <nav class="nav-list">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-item">
          <svg class="nav-icon nav-svg-icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="link.icon" /></svg>
          <span>{{ link.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-foot">
        <label v-if="!auth.isAdmin" class="language-switcher">
          <span>{{ t('language.label') }}</span>
          <select class="input" :value="locale" @change="changeLocale">
            <option v-for="option in localeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <div class="user-card account-card-without-avatar"><div><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.isAdmin ? '业务管理员' : auth.user?.username }}</small></div></div>
        <RouterLink v-if="auth.isAdmin" to="/admin/profile/password" class="button ghost full sidebar-action-link">修改密码</RouterLink>
        <button class="button ghost full" @click="logout">退出登录</button>
      </div>
    </aside>
    <main class="main-content fixed-sidebar-content" :class="{ 'student-main-content': auth.isStudent }">
      <section class="page-container" :class="{ 'student-page-container': auth.isStudent, 'admin-page-container': auth.isAdmin }"><RouterView /></section>
      <footer class="page-compliance"><span>{{ operatorName }}</span><span>{{ icpRecord }}</span></footer>
    </main>
    <Transition name="welcome-pop">
      <div v-if="auth.welcomeRequired" class="welcome-overlay" role="presentation">
        <section class="welcome-dialog" role="dialog" aria-modal="true" aria-labelledby="student-welcome-title">
          <div class="welcome-glow welcome-glow-one" /><div class="welcome-glow welcome-glow-two" />
          <img class="welcome-school-logo logo-safe-layer" :src="logoOnly" alt="" aria-hidden="true" />
          <span class="eyebrow">{{ subtitle('欢迎来到校园', 'WELCOME TO CAMPUS') }}</span>
          <h2 id="student-welcome-title">{{ t('welcome.title') }}</h2>
          <p>{{ welcomeText }}</p>
          <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
          <button class="button primary welcome-start-button" :disabled="auth.welcomeAcknowledging" @click="acknowledgeWelcome">{{ auth.welcomeAcknowledging ? '正在进入…' : t('welcome.start') }}</button>
        </section>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.fixed-navigation-shell{display:block;min-height:100vh}.fixed-sidebar{position:fixed;inset:0 auto 0 0;display:flex;flex-direction:column;width:260px;height:100vh;overflow:hidden;z-index:30}.fixed-sidebar .nav-list{min-height:0;overflow-y:auto;scrollbar-width:none}.fixed-sidebar .nav-list::-webkit-scrollbar{display:none}.fixed-sidebar .sidebar-foot{flex:0 0 auto}.sidebar-action-link{text-decoration:none;text-align:center}.fixed-sidebar-content{display:flex;flex-direction:column;min-height:100vh;margin-left:260px}.fixed-sidebar-content>.page-container{flex:1 0 auto}.school-brand{position:relative;z-index:5;isolation:isolate;display:flex;align-items:center;gap:11px;min-height:62px;padding:9px 12px;overflow:visible;background:linear-gradient(180deg,rgba(17,45,96,.98),rgba(17,45,96,.92))}.school-brand::before,.school-brand::after,.welcome-dialog::before,.welcome-dialog::after{pointer-events:none;z-index:0}.logo-safe-layer{position:relative;z-index:3;isolation:isolate}.school-brand-logo{position:relative;z-index:9;flex:0 0 auto;width:46px;height:46px;object-fit:contain;filter:drop-shadow(0 3px 8px rgba(0,0,0,.22))}.school-brand-title{position:relative;z-index:2;display:grid;gap:2px;min-width:0;text-align:left}.school-brand-title strong,.school-brand-title small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.school-brand-title strong{font-size:.92rem}.school-brand-title small{color:#91a8d5;font-size:.66rem}.admin-page-container{padding-top:22px}.account-card-without-avatar{padding:14px 16px}.account-card-without-avatar>div{min-width:0}.account-card-without-avatar strong,.account-card-without-avatar small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.page-compliance{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;padding:18px 24px 22px;color:var(--muted);font-size:.72rem;line-height:1.45;text-align:center}.page-compliance span+span::before{content:"·";margin-right:12px}.welcome-dialog{position:relative;isolation:isolate}.welcome-school-logo{width:72px;height:72px;object-fit:contain;margin:0 auto 12px}@media(max-width:820px){.fixed-sidebar{position:static;width:auto;height:auto;overflow:visible}.fixed-sidebar-content{margin-left:0}.school-brand{justify-content:flex-start}}
</style>
