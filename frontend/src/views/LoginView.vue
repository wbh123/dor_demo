<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { ActivateRequest, LoginRequest } from '../api/types'

const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || '')
const heroTitle = String(import.meta.env.VITE_LOGIN_HERO_TITLE || `${institutionName}学生社区`)
const heroDescription = String(import.meta.env.VITE_LOGIN_HERO_DESCRIPTION || '尊重差异、友好相处，共同建设温暖有序的学生社区。')
const heroBrandSubtitle = String(import.meta.env.VITE_LOGIN_BRAND_SUBTITLE || '学生社区')
const loginServiceName = String(import.meta.env.VITE_LOGIN_SERVICE_NAME || '学生宿舍服务')
const showOperatorInfo = String(import.meta.env.VITE_SHOW_OPERATOR_INFO || 'false').toLowerCase() === 'true' && Boolean(operatorName.trim())
const showIcpRecord = String(import.meta.env.VITE_SHOW_ICP_RECORD || 'false').toLowerCase() === 'true' && Boolean(icpRecord.trim())
const publicBase = String(import.meta.env.BASE_URL || '/').replace(/\/?$/, '/')
const brandLogo = `${publicBase}assets/logo-title-right.png`
const legacyBrandLogo = `${publicBase}assert/logo-only.png`
const auth = useAuthStore()
const router = useRouter()
const mode = ref<'login' | 'activate'>('login')
const error = ref('')
const message = ref('')
const loginUsernameInput = ref<HTMLInputElement | null>(null)
const activateStudentInput = ref<HTMLInputElement | null>(null)
const loginForm = reactive<LoginRequest>({ username: '', password: '' })
const activateForm = reactive<ActivateRequest>({ studentNumber: '', studentName: '', password: '' })

const loginFormHint = computed(() => error.value || message.value || '请使用管理员账号或学生学号登录。')
const activateFormHint = computed(() => error.value || message.value || '首次使用的学生请凭12位学号、姓名和新密码激活账号。')

function setMode(nextMode: 'login' | 'activate') {
  mode.value = nextMode
  error.value = ''
  message.value = ''
  void focusModePrimaryInput()
}
async function focusModePrimaryInput() {
  await nextTick()
  const input = mode.value === 'login' ? loginUsernameInput.value : activateStudentInput.value
  if (!input) return
  input.focus()
  const end = input.value.length
  input.setSelectionRange(end, end)
}
function fallbackBrandLogo(event: Event) {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallbackApplied === 'true') return
  image.dataset.fallbackApplied = 'true'
  image.src = legacyBrandLogo
}
async function submitLogin() {
  error.value = ''; message.value = ''
  try { await auth.login(loginForm); await router.replace(auth.isAdmin ? '/admin' : '/student') }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '登录失败' }
}
async function submitActivate() {
  error.value = ''; message.value = ''
  try {
    await auth.activate(activateForm)
    loginForm.username = activateForm.studentNumber
    loginForm.password = ''
    mode.value = 'login'
    message.value = '账号激活成功，请使用学号和新密码登录。'
    await focusModePrimaryInput()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '激活失败' }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-copy">
        <div class="madrid-brand">
          <div class="brand-image-surface hero-brand-surface"><img class="login-school-logo" :src="brandLogo" :alt="`${institutionName}校徽与校名`" @error="fallbackBrandLogo" /></div>
          <span>{{ heroBrandSubtitle }}</span>
        </div>
        <h1 class="login-hero-title">{{ heroTitle }}</h1>
        <p>{{ heroDescription }}</p>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card auth-card-fixed">
        <div class="school-login-brand">
          <div class="brand-image-surface card-brand-surface"><img :src="brandLogo" :alt="`${institutionName}校徽与校名`" @error="fallbackBrandLogo" /></div>
          <span>{{ loginServiceName }}</span>
        </div>
        <div class="segment auth-mode-switch"><button :class="{ active: mode === 'login' }" type="button" @click="setMode('login')">登录</button><button :class="{ active: mode === 'activate' }" type="button" @click="setMode('activate')">学生激活</button></div>

        <div class="auth-form-frame">
          <form v-if="mode === 'login'" class="form-stack auth-form" @submit.prevent="submitLogin">
            <div class="auth-fields">
              <label><span>用户名或学号</span><input ref="loginUsernameInput" v-model.trim="loginForm.username" required maxlength="64" autocomplete="username" placeholder="管理员用户名或12位学号" /></label>
              <label><span>密码</span><input v-model="loginForm.password" required minlength="4" maxlength="72" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
              <div class="auth-field-spacer" aria-hidden="true" />
            </div>
            <button class="button primary full auth-submit" :disabled="auth.loading">{{ auth.loading ? '正在登录…' : '进入系统' }}</button>
            <p class="auth-inline-hint" :class="{ error: error, success: message }" role="status">{{ loginFormHint }}</p>
          </form>

          <form v-else class="form-stack auth-form" @submit.prevent="submitActivate">
            <div class="auth-fields">
              <label><span>12位学号</span><input ref="activateStudentInput" v-model.trim="activateForm.studentNumber" required pattern="\d{12}" maxlength="12" autocomplete="username" placeholder="例如 202600000001" /></label>
              <label><span>姓名</span><input v-model.trim="activateForm.studentName" required maxlength="128" autocomplete="name" placeholder="必须与录入信息一致" /></label>
              <label><span>设置密码</span><input v-model="activateForm.password" required minlength="8" maxlength="72" type="password" autocomplete="new-password" placeholder="至少8位" /></label>
            </div>
            <button class="button primary full auth-submit" :disabled="auth.loading">{{ auth.loading ? '正在激活…' : '激活学生账号' }}</button>
            <p class="auth-inline-hint" :class="{ error: error, success: message }" role="status">{{ activateFormHint }}</p>
          </form>
        </div>
      </div>
      <footer v-if="showOperatorInfo || showIcpRecord" class="login-compliance"><span v-if="showOperatorInfo">{{ operatorName }}</span><span v-if="showIcpRecord">{{ icpRecord }}</span></footer>
    </section>
  </div>
</template>

<style scoped>
.login-hero{display:flex;align-items:center}.hero-copy{max-width:680px}.madrid-brand{display:grid;justify-items:start;gap:10px;margin-bottom:30px}.madrid-brand>span{font-size:16px;letter-spacing:.18em}.brand-image-surface{display:flex;align-items:center;justify-content:center;background:#fff;border:1px solid rgba(15,23,42,.08);box-shadow:0 12px 32px rgba(15,23,42,.12);overflow:hidden}.hero-brand-surface{width:min(500px,92vw);min-height:82px;padding:9px 15px;border-radius:16px}.login-school-logo{display:block;width:100%;height:64px;object-fit:contain}.school-login-brand{display:grid;justify-items:start;gap:8px;min-height:86px;margin-bottom:4px}.card-brand-surface{width:min(350px,100%);min-height:62px;padding:7px 11px;border-radius:13px}.card-brand-surface img{display:block;width:100%;height:48px;object-fit:contain}.school-login-brand>span{color:var(--muted);font-size:13px}.login-panel{display:flex;flex-direction:column;justify-content:center}.auth-card-fixed{width:min(470px,100%);min-height:596px;margin:auto;display:flex;flex-direction:column}.auth-mode-switch{flex:0 0 auto}.auth-form-frame{flex:1;display:flex;min-height:390px}.auth-form{display:grid;grid-template-rows:1fr auto 52px;width:100%;min-height:390px}.auth-fields{display:grid;grid-template-rows:repeat(3,minmax(78px,auto));align-content:start;gap:12px}.auth-field-spacer{min-height:78px}.auth-submit{align-self:end}.auth-inline-hint{min-height:44px;margin:8px 0 0;padding:8px 10px;border-radius:10px;background:var(--soft);color:var(--muted);font-size:13px;line-height:1.45}.auth-inline-hint.error{background:#fff1f2;color:#b42318}.auth-inline-hint.success{background:#ecfdf3;color:#027a48}.login-compliance{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;width:100%;margin-top:auto;padding-top:18px;color:var(--muted);text-align:center;font-size:.7rem}.login-compliance span+span::before{content:"·";margin-right:12px}@media(max-width:760px){.auth-card-fixed{min-height:570px}.hero-brand-surface{min-height:68px}.login-school-logo{height:50px}}
</style>
