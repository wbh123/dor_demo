<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { platformApi } from '../../platform/api'
import { usePlatformSession } from '../../platform/session'

const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const loading = ref(false)
const { clearSession } = usePlatformSession()

async function submit() {
  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致'; return }
  loading.value = true; error.value = ''
  try {
    await platformApi.changePassword(currentPassword.value, newPassword.value)
    clearSession()
    await router.replace('/platform/login')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '修改失败' }
  finally { loading.value = false }
}
</script>
<template><section class="panel"><h1>修改系统管理员密码</h1><p>首次登录或本地重置后必须修改密码。新密码至少12位，并包含大小写字母、数字和特殊字符。</p>
<form @submit.prevent="submit"><input v-model="currentPassword" type="password" placeholder="当前密码" required /><input v-model="newPassword" type="password" placeholder="新密码" required /><input v-model="confirmPassword" type="password" placeholder="确认新密码" required /><p v-if="error" class="error">{{ error }}</p><button :disabled="loading">{{ loading ? '修改中…' : '修改并重新登录' }}</button></form></section></template>
<style scoped>.panel{max-width:620px;background:#fff;padding:24px;border-radius:12px}form{display:grid;gap:12px;margin-top:18px}input,button{padding:11px;border:1px solid #cbd5e1;border-radius:8px}button{background:#1d4ed8;color:#fff;border:0}.error{color:#b91c1c}</style>
