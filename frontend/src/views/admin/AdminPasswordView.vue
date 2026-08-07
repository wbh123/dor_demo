<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useI18n } from '../../i18n'

const router = useRouter()
const auth = useAuthStore()
const { translateError } = useI18n()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const saving = ref(false)
const rules = computed(() => ({
  length: newPassword.value.length >= 12 && newPassword.value.length <= 72,
  upper: /[A-Z]/.test(newPassword.value),
  lower: /[a-z]/.test(newPassword.value),
  digit: /[0-9]/.test(newPassword.value),
  special: /[^A-Za-z0-9]/.test(newPassword.value),
}))
const validPassword = computed(() => Object.values(rules.value).every(Boolean))

async function submit() {
  error.value = ''
  if (!validPassword.value) { error.value = '新密码需为12至72位，并同时包含大写字母、小写字母、数字和特殊字符。'; return }
  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致。'; return }
  saving.value = true
  try {
    await api.put('/api/v1/auth/password', { currentPassword: currentPassword.value, newPassword: newPassword.value })
    await auth.logout()
    await router.replace('/login')
  } catch (reason) { error.value = translateError(reason) }
  finally { saving.value = false }
}
</script>

<template>
  <div class="content-column narrow">
    <div class="page-title"><span class="eyebrow">ACCOUNT SECURITY</span><h2>修改管理员密码</h2><p>修改成功后全部登录令牌立即失效，需要使用新密码重新登录。</p></div>
    <section class="panel password-card"><form class="form-stack" @submit.prevent="submit"><label><span>当前密码</span><input v-model="currentPassword" class="input" type="password" autocomplete="current-password" required /></label><label><span>新密码</span><input v-model="newPassword" class="input" type="password" autocomplete="new-password" required /></label><div class="password-rules"><strong>密码要求</strong><span :class="{ pass: rules.length }">12至72位</span><span :class="{ pass: rules.upper }">包含大写字母</span><span :class="{ pass: rules.lower }">包含小写字母</span><span :class="{ pass: rules.digit }">包含数字</span><span :class="{ pass: rules.special }">包含特殊字符</span></div><label><span>确认新密码</span><input v-model="confirmPassword" class="input" type="password" autocomplete="new-password" required /></label><p v-if="error" class="alert error">{{ error }}</p><button class="button primary" :disabled="saving">{{ saving ? '正在修改…' : '修改密码并重新登录' }}</button></form></section>
  </div>
</template>

<style scoped>.password-card{padding:24px}.password-rules{display:flex;gap:8px;flex-wrap:wrap;padding:12px;border-radius:12px;background:var(--soft)}.password-rules strong{width:100%}.password-rules span{padding:4px 8px;border-radius:999px;color:#9b2838;background:#fff0f2;font-size:12px}.password-rules span.pass{color:#17664f;background:#e8f8f2}</style>
