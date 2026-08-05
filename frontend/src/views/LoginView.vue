<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { ActivateRequest, LoginRequest } from '../api/types'

const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '武汉科技大学')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || '')
const heroTitle = String(import.meta.env.VITE_LOGIN_HERO_TITLE || `${institutionName}学生社区`)
const heroDescription = String(import.meta.env.VITE_LOGIN_HERO_DESCRIPTION || '尊重差异、友好相处，共同建设温暖有序的学生社区。')
const heroBrandSubtitle = String(import.meta.env.VITE_LOGIN_BRAND_SUBTITLE || '学生社区')
const loginServiceName = String(import.meta.env.VITE_LOGIN_SERVICE_NAME || '学生宿舍服务')
const showOperatorInfo = String(import.meta.env.VITE_SHOW_OPERATOR_INFO || 'false').toLowerCase() === 'true' && Boolean(operatorName.trim())
const showIcpRecord = String(import.meta.env.VITE_SHOW_ICP_RECORD || 'false').toLowerCase() === 'true' && Boolean(icpRecord.trim())
const logoOnly = `${import.meta.env.BASE_URL}assert/logo-only.png`
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
        <div class="madrid-brand"><img class="login-school-logo" :src="logoOnly" :alt="`${institutionName}校徽`" /><div><strong>{{ institutionName }}</strong><span>{{ heroBrandSubtitle }}</span></div></div>
        <h1 class="login-hero-title">{{ heroTitle }}</h1>
        <p>{{ heroDescription }}</p>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card auth-card-fixed">
        <div class="brand login-brand school-login-brand"><img :src="logoOnly" :alt="`${institutionName}校徽`" /><div><strong>{{ institutionName }}</strong><span>{{ loginServiceName }}</span></div></div>
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
.login-hero{display:flex;align-items:center}.hero-copy{max-width:680px}.madrid-brand{display:flex;align-items:center;gap:18px;margin-bottom:30px}.madrid-brand div{display:grid;gap:2px}.madrid-brand strong{font-size:27px}.madrid-brand span{font-size:18px;letter-spacing:.2em}.login-school-logo{width:112px;height:112px;object-fit:contain;filter:drop-shadow(0 12px 30px rgba(0,0,0,.16))}.school-login-brand{display:flex;align-items:center;gap:13px;min-height:84px}.school-login-brand img{width:72px;height:72px;object-fit:contain}.school-login-brand div{display:grid;gap:3px}.school-login-brand strong{font-size:18px}.school-login-brand span{color:var(--muted);font-size:13px}.login-panel{display:flex;flex-direction:column;justify-content:center}.auth-card-fixed{width:min(470px,100%);min-height:596px;margin:auto;display:flex;flex-direction:column}.auth-mode-switch{flex:0 0 auto}.auth-form-frame{flex:1;display:flex;min-height:390px}.auth-form{display:grid;grid-template-rows:1fr auto 52px;width:100%;min-height:390px}.auth-fields{display:grid;grid-template-rows:repeat(3,minmax(78px,auto));align-content:start;gap:12px}.auth-field-spacer{min-height:78px}.auth-submit{align-self:end}.auth-inline-hint{min-height:44px;margin:8px 0 0;padding:8px 10px;border-radius:10px;background:var(--soft);color:var(--muted);font-size:13px;line-height:1.45}.auth-inline-hint.error{background:#fff1f2;color:#b42318}.auth-inline-hint.success{background:#ecfdf3;color:#027a48}.login-compliance{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;width:100%;margin-top:auto;padding-top:18px;color:var(--muted);text-align:center;font-size:.7rem}.login-compliance span+span::before{content:"·";margin-right:12px}@media(max-width:760px){.auth-card-fixed{min-height:570px}.madrid-brand strong{font-size:22px}.login-school-logo{width:78px;height:78px}}
</style>
