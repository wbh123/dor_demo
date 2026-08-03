<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { ActivateRequest, LoginRequest } from '../api/types'

const institutionName = import.meta.env.VITE_INSTITUTION_NAME || '示例大学'
const operatorName = import.meta.env.VITE_OPERATOR_NAME || '运营单位信息待填写'
const icpRecord = import.meta.env.VITE_ICP_RECORD || 'ICP备案信息待填写'
const auth = useAuthStore()
const router = useRouter()
const mode = ref<'login' | 'activate'>('login')
const error = ref('')
const message = ref('')
const loginForm = reactive<LoginRequest>({ username: '', password: '' })
const activateForm = reactive<ActivateRequest>({ studentNumber: '', studentName: '', password: '' })

async function submitLogin() {
  error.value = ''
  try { await auth.login(loginForm); await router.replace(auth.isAdmin ? '/admin' : '/student') }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '登录失败' }
}
async function submitActivate() {
  error.value = ''; message.value = ''
  try {
    await auth.activate(activateForm)
    message.value = '账号激活成功，请使用学号和新密码登录。'
    loginForm.username = activateForm.studentNumber; loginForm.password = ''; mode.value = 'login'
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '激活失败' }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-copy"><img class="login-school-logo" src="/logo-title-bottom.png" :alt="`${institutionName}校徽`" /><h1>让宿舍选择<br />更公平，也更合拍</h1><p>基于明确规则和个人偏好的宿舍智能选择系统。每一次占用、分配和调整都有记录。</p></div>
      <div class="hero-stats"><div><strong>实时</strong><span>房间床位状态</span></div><div><strong>可解释</strong><span>个人偏好匹配</span></div><div><strong>可追溯</strong><span>分配与审计记录</span></div></div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="brand login-brand school-login-brand"><img src="/logo-title-right.png" :alt="`${institutionName}校徽`" /></div>
        <div class="segment"><button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button><button :class="{ active: mode === 'activate' }" @click="mode = 'activate'">学生激活</button></div>

        <form v-if="mode === 'login'" class="form-stack" @submit.prevent="submitLogin">
          <label><span>用户名或学号</span><input v-model.trim="loginForm.username" required maxlength="64" placeholder="管理员用户名或12位学号" /></label>
          <label><span>密码</span><input v-model="loginForm.password" required minlength="8" maxlength="72" type="password" placeholder="请输入密码" /></label>
          <button class="button primary full" :disabled="auth.loading">{{ auth.loading ? '正在登录…' : '进入系统' }}</button>
        </form>

        <form v-else class="form-stack" @submit.prevent="submitActivate">
          <label><span>12位学号</span><input v-model.trim="activateForm.studentNumber" required pattern="\d{12}" maxlength="12" placeholder="例如 202600000001" /></label>
          <label><span>姓名</span><input v-model.trim="activateForm.studentName" required maxlength="128" placeholder="必须与录入信息一致" /></label>
          <label><span>设置密码</span><input v-model="activateForm.password" required minlength="8" maxlength="72" type="password" placeholder="至少8位" /></label>
          <button class="button primary full" :disabled="auth.loading">{{ auth.loading ? '正在激活…' : '激活学生账号' }}</button>
        </form>

        <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
        <footer class="login-compliance"><span>{{ operatorName }}</span><span>{{ icpRecord }}</span></footer>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-school-logo { width: min(330px, 80%); max-height: 150px; object-fit: contain; object-position: left center; margin-bottom: 28px; filter: drop-shadow(0 12px 30px rgba(0,0,0,.16)); }
.school-login-brand img { width: min(280px, 100%); max-height: 70px; object-fit: contain; object-position: left center; }
.login-compliance { display: grid; gap: 4px; margin-top: 22px; padding-top: 16px; border-top: 1px solid var(--line); color: var(--muted); text-align: center; font-size: .7rem; }
</style>
