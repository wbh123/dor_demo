<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client'
import type { ActivateRequest, DataObject, LoginRequest, ObjectSuccessResponse } from '../api/types'
import { useAuthStore } from '../stores/auth'

const fallbackInstitutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || '')
const loginServiceName = String(import.meta.env.VITE_LOGIN_SERVICE_NAME || '学生宿舍服务')
const showOperatorInfo = String(import.meta.env.VITE_SHOW_OPERATOR_INFO || 'false').toLowerCase() === 'true' && Boolean(operatorName.trim())
const showIcpRecord = String(import.meta.env.VITE_SHOW_ICP_RECORD || 'false').toLowerCase() === 'true' && Boolean(icpRecord.trim())
const publicBase = String(import.meta.env.BASE_URL || '/').replace(/\/?$/, '/')
const fallbackBrandLogo = `${publicBase}assets/logo-title-right.png`
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
const siteConfig = ref<DataObject>({})

const branding = computed(() => (siteConfig.value.branding ?? {}) as DataObject)
const loginContent = computed(() => (siteConfig.value.login ?? {}) as DataObject)
const institutionName = computed(() => String(branding.value.schoolName ?? fallbackInstitutionName))
const brandLogo = computed(() => String(branding.value.horizontalLogoUrl ?? fallbackBrandLogo) || fallbackBrandLogo)
const loginFrameHtml = computed(() => {
  const imageUrl = String(loginContent.value.imageUrl ?? '').trim()
  const html = String(loginContent.value.html ?? '').trim()
    || '<h1>宿舍智能选择系统</h1><p>查看开放批次、完善个人偏好，并在开放时段完成寝室选择。</p>'
  const image = imageUrl ? `<img src="${escapeAttribute(imageUrl)}" alt="登录页展示图片">` : ''
  return `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;color:#fff;font-family:Inter,"Microsoft YaHei",sans-serif}body{display:grid;align-content:center;gap:18px;padding:6px 0;box-sizing:border-box}h1,h2,h3,p,ul,ol{margin:0}h1{max-width:720px;font-size:clamp(34px,4vw,58px);line-height:1.08}h2{font-size:28px}p,li{max-width:720px;font-size:15px;line-height:1.78;color:rgba(255,255,255,.86)}img{display:block;max-width:min(720px,100%);max-height:260px;object-fit:contain;object-position:left center}</style></head><body>${image}${html}</body></html>`
})
const loginFormHint = computed(() => error.value || message.value || '请使用管理员账号或学生学号登录。')
const activateFormHint = computed(() => error.value || message.value || '首次使用的学生请凭12位学号、姓名和新密码激活账号。开发阶段不校验复杂度。')

onMounted(loadSiteConfig)
async function loadSiteConfig() {
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/public/site-config')
    siteConfig.value = (response.data.data ?? {}) as DataObject
  } catch {
    siteConfig.value = {}
  }
}
function setMode(nextMode: 'login' | 'activate') {
  mode.value = nextMode; error.value = ''; message.value = ''; void focusModePrimaryInput()
}
async function focusModePrimaryInput() {
  await nextTick()
  const input = mode.value === 'login' ? loginUsernameInput.value : activateStudentInput.value
  if (!input) return
  input.focus(); const end = input.value.length; input.setSelectionRange(end, end)
}
function fallbackLogo(event: Event) {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallbackApplied === 'true') return
  image.dataset.fallbackApplied = 'true'; image.src = legacyBrandLogo
}
async function submitLogin() {
  error.value = ''; message.value = ''
  try { await auth.login(loginForm); await router.replace(auth.isAdmin ? '/admin' : '/student') }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '登录失败' }
}
async function submitActivate() {
  error.value = ''; message.value = ''
  try {
    await auth.activate(activateForm); loginForm.username = activateForm.studentNumber; loginForm.password = ''
    mode.value = 'login'; message.value = '账号激活成功，请使用学号和新密码登录。'; await focusModePrimaryInput()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '激活失败' }
}
function escapeAttribute(value: string) {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-content-frame">
        <div class="brand-image-surface hero-brand-surface">
          <img class="hero-school-logo" :src="brandLogo" :alt="`${institutionName}校徽与校名`" @error="fallbackLogo" />
        </div>
        <iframe class="login-left-frame" title="登录页展示内容" :srcdoc="loginFrameHtml" sandbox="" scrolling="no"></iframe>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card auth-card-fixed">
        <div class="school-login-brand">
          <div class="brand-image-surface card-brand-surface">
            <img class="form-school-logo" :src="brandLogo" :alt="`${institutionName}校徽与校名`" @error="fallbackLogo" />
          </div>
          <span>{{ loginServiceName }}</span>
        </div>
        <div class="segment auth-mode-switch"><button :class="{ active: mode === 'login' }" type="button" @click="setMode('login')">登录</button><button :class="{ active: mode === 'activate' }" type="button" @click="setMode('activate')">学生激活</button></div>
        <div class="auth-form-frame">
          <form v-if="mode === 'login'" class="form-stack auth-form" @submit.prevent="submitLogin">
            <div class="auth-fields"><label><span>用户名或学号</span><input ref="loginUsernameInput" v-model.trim="loginForm.username" required maxlength="64" autocomplete="username" placeholder="管理员用户名或12位学号" /></label><label><span>密码</span><input v-model="loginForm.password" required minlength="1" maxlength="72" type="password" autocomplete="current-password" placeholder="请输入密码" /></label></div>
            <button class="button primary full auth-submit" :disabled="auth.loading">{{ auth.loading ? '正在登录…' : '进入系统' }}</button>
            <p class="auth-inline-hint" :class="{ error: error, success: message }" role="status">{{ loginFormHint }}</p>
          </form>
          <form v-else class="form-stack auth-form" @submit.prevent="submitActivate">
            <div class="auth-fields"><label><span>12位学号</span><input ref="activateStudentInput" v-model.trim="activateForm.studentNumber" required pattern="\d{12}" maxlength="12" autocomplete="username" placeholder="例如 202600000001" /></label><label><span>姓名</span><input v-model.trim="activateForm.studentName" required maxlength="128" autocomplete="name" placeholder="必须与录入信息一致" /></label><label><span>设置密码</span><input v-model="activateForm.password" required minlength="1" maxlength="72" type="password" autocomplete="new-password" placeholder="开发阶段仅要求非空" /></label></div>
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
.login-hero{display:flex;align-items:center}.hero-content-frame{display:grid;grid-template-rows:auto minmax(300px,1fr);gap:20px;width:min(760px,100%);max-height:76vh}.brand-image-surface{display:flex;align-items:center;justify-content:flex-start;background:#fff;border:1px solid rgba(15,23,42,.08);box-shadow:0 12px 32px rgba(15,23,42,.12);overflow:hidden}.hero-brand-surface{width:min(500px,92vw);min-height:82px;padding:9px 15px;border-radius:16px}.hero-school-logo{display:block;width:100%;height:64px;object-fit:contain;object-position:left center}.card-brand-surface{width:min(350px,100%);min-height:62px;padding:7px 11px;border-radius:13px}.form-school-logo{display:block;width:100%;height:48px;object-fit:contain;object-position:left center}.login-left-frame{display:block;width:100%;height:100%;min-height:300px;border:0;background:transparent;overflow:hidden}.school-login-brand{display:grid;justify-items:start;gap:5px;margin-bottom:8px}.school-login-brand>span{color:var(--muted);font-size:12px}.login-panel{display:flex;flex-direction:column;justify-content:center}.auth-card-fixed{width:min(450px,100%);margin:auto;display:flex;flex-direction:column}.auth-mode-switch{margin-bottom:10px}.auth-form-frame{display:flex}.auth-form{display:grid;gap:10px;width:100%}.auth-fields{display:grid;gap:9px}.auth-submit{margin-top:2px}.auth-inline-hint{min-height:36px;margin:0;padding:7px 9px;border-radius:9px;background:var(--soft);color:var(--muted);font-size:12px;line-height:1.4}.auth-inline-hint.error{background:#fff1f2;color:#b42318}.auth-inline-hint.success{background:#ecfdf3;color:#027a48}.login-compliance{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;width:100%;margin-top:auto;padding-top:14px;color:var(--muted);text-align:center;font-size:.7rem}.login-compliance span+span::before{content:"·";margin-right:12px}@media(max-width:760px){.hero-content-frame{grid-template-rows:auto minmax(220px,1fr);gap:12px}.hero-brand-surface{min-height:68px}.hero-school-logo{height:50px}.card-brand-surface{min-height:58px}.form-school-logo{height:44px}.login-left-frame{min-height:220px}}
</style>
