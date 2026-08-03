<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { platformApi } from '../../platform/api'
import { usePlatformSession } from '../../platform/session'

const router = useRouter()
const username = ref('system_admin')
const password = ref('')
const loading = ref(false)
const error = ref('')
const { setSession } = usePlatformSession()

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const result = await platformApi.login(username.value, password.value)
    setSession(result.accessToken, result.user)
    await router.replace(result.user.passwordChangeRequired
      ? '/platform/profile/password'
      : '/platform')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>系统服务管理</h1>
      <p>该入口仅供系统管理员使用。</p>
      <label>用户名<input v-model.trim="username" autocomplete="username" /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="loading || !username || !password">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #eef2f7; }
.login-card { width: min(420px, calc(100vw - 32px)); padding: 32px; background: white; border-radius: 16px; box-shadow: 0 18px 40px #0f172a1f; display: grid; gap: 16px; }
h1 { margin: 0; } p { margin: 0; color: #64748b; }
label { display: grid; gap: 6px; font-weight: 600; }
input { padding: 11px 12px; border: 1px solid #cbd5e1; border-radius: 8px; }
button { padding: 12px; border: 0; border-radius: 8px; background: #1d4ed8; color: white; font-weight: 700; cursor: pointer; }
.error { color: #b91c1c; }
</style>
