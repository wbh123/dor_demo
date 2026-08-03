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
const {
  locale,
  localeOptions,
  t,
  subtitle,
  setLocale,
  applyNationalityLocale,
  welcomeMessage,
  translateError,
} = useI18n()

const icons = {
  dashboard: 'M3 3h8v8H3V3Zm10 0h8v5h-8V3ZM3 13h8v8H3v-8Zm10-3h8v11h-8V10Z',
  students: 'M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8 2c-3.31 0-6 1.79-6 4v3h12v-3c0-2.21-2.69-4-6-4ZM8 14c-3.31 0-6 1.79-6 4v3h6v-3c0-1.5.64-2.85 1.72-3.93A9.2 9.2 0 0 0 8 14Z',
  dormitory: 'M4 21V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v3h1a2 2 0 0 1 2 2v12h-2v-2H6v2H4Zm2-4h12V9h-3V4H6v13Zm2-11h2v2H8V6Zm4 0h2v2h-2V6Zm-4 4h2v2H8v-2Zm4 0h2v2h-2v-2Z',
  matching: 'M4 7h10v2H4V7Zm14-3h2v8h-2V4ZM4 15h4v2H4v-2Zm8-3h2v8h-2v-8Zm6 3h2v2h-2v-2ZM8 4h2v8H8V4Zm8 8h2v8h-2v-8Z',
  rules: 'M5 4h14v3H5V4Zm0 6h14v3H5v-3Zm0 6h14v3H5v-3Zm2-11v1h10V5H7Zm0 6v1h10v-1H7Zm0 6v1h10v-1H7Z',
  calendar: 'M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h3V2Zm13 8H4v10h16V10ZM4 8h16V6H4v2Z',
  assignment: 'M4 3h13a2 2 0 0 1 2 2v3h1a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-3H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm3 7v9h13v-9H7Zm2 2h9v2H9v-2Zm0 4h6v2H9v-2ZM4 5v9h1v-4a2 2 0 0 1 2-2h10V5H4Z',
  home: 'm12 3 9 8h-3v10h-5v-6h-2v6H6V11H3l9-8Z',
  team: 'M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM7 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm9 2c-3.31 0-6 1.79-6 4v3h12v-3c0-2.21-2.69-4-6-4ZM7 14c-2.76 0-5 1.57-5 3.5V21h6v-3c0-1.4.53-2.69 1.45-3.76A8.4 8.4 0 0 0 7 14Z',
}

const links = computed(() =>
  auth.isAdmin
    ? [
        { to: '/admin', label: '工作台', icon: icons.dashboard },
        { to: '/admin/data', label: '专业与学生', icon: icons.students },
        { to: '/admin/dormitories', label: '宿舍资源', icon: icons.dormitory },
        { to: '/admin/matching', label: '匹配规则', icon: icons.matching },
        { to: '/admin/rule-templates', label: '批次规则', icon: icons.rules },
        { to: '/admin/batches', label: '选寝批次', icon: icons.calendar },
        { to: '/admin/assignments', label: '分配与调整', icon: icons.assignment },
      ]
    : [
        { to: '/student', label: '选寝首页', icon: icons.home },
        { to: '/student/teams', label: '我的队伍', icon: icons.team },
      ],
)

const welcomeText = computed(() => {
  const welcome = auth.user?.welcome as (DataObject & { messages?: Record<string, string> }) | undefined
  return welcomeMessage(welcome?.messages) || String(welcome?.message ?? '')
})

onMounted(async () => {
  if (!auth.isStudent) return
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/student/profile')
    const profile = (response.data.data ?? {}) as DataObject
    applyNationalityLocale(profile.nationality_code)
  } catch {
    // 资料页会再次加载，语言自动判断失败不阻断系统使用。
  }
})

function changeLocale(event: Event) {
  setLocale((event.target as HTMLSelectElement).value as LocaleCode)
}

async function acknowledgeWelcome() {
  welcomeError.value = ''
  try {
    await auth.acknowledgeWelcome()
  } catch (reason) {
    welcomeError.value = translateError(reason)
  }
}

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">W</span>
        <div>
          <strong>高校选寝</strong>
          <small>宿舍智能选择系统</small>
        </div>
      </div>

      <nav class="nav-list">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-item">
          <svg class="nav-icon nav-svg-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="link.icon" />
          </svg>
          <span>{{ link.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <label class="language-switcher">
          <span>{{ t('language.label') }}</span>
          <select class="input" :value="locale" @change="changeLocale">
            <option v-for="option in localeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <div class="user-card account-card-without-avatar">
          <div>
            <strong>{{ auth.user?.displayName }}</strong>
            <small>{{ auth.isAdmin ? '业务管理员' : auth.user?.username }}</small>
          </div>
        </div>
        <button class="button ghost full" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="main-content" :class="{ 'student-main-content': auth.isStudent }">
      <header v-if="auth.isAdmin" class="topbar">
        <div>
          <span class="eyebrow">{{ subtitle('高校选寝', 'WUST DORMITORY SELECT') }}</span>
          <h1>管理控制台</h1>
        </div>
      </header>
      <section class="page-container" :class="{ 'student-page-container': auth.isStudent }">
        <RouterView />
      </section>
    </main>

    <Transition name="welcome-pop">
      <div v-if="auth.welcomeRequired" class="welcome-overlay" role="presentation">
        <section
          class="welcome-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="student-welcome-title"
        >
          <div class="welcome-glow welcome-glow-one" />
          <div class="welcome-glow welcome-glow-two" />
          <div class="welcome-symbol" aria-hidden="true">W</div>
          <span class="eyebrow">{{ subtitle('欢迎来到校园', 'WELCOME TO CAMPUS') }}</span>
          <h2 id="student-welcome-title">{{ t('welcome.title') }}</h2>
          <p>{{ welcomeText }}</p>
          <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
          <button
            class="button primary welcome-start-button"
            :disabled="auth.welcomeAcknowledging"
            @click="acknowledgeWelcome"
          >
            {{ auth.welcomeAcknowledging ? '正在进入…' : t('welcome.start') }}
          </button>
        </section>
      </div>
    </Transition>
  </div>
</template>
