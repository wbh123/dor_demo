<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../../api/client'
import { useI18n } from '../../i18n'
import { applySiteTheme, type SiteTheme } from '../../site/theme'

const props = defineProps<{ modelValue: SiteTheme }>()
const emit = defineEmits<{ 'update:modelValue': [value: SiteTheme] }>()
const saving = ref(false)
const error = ref('')
const { translateError } = useI18n()

async function setTheme(next: SiteTheme) {
  if (saving.value || next === props.modelValue) return
  saving.value = true
  error.value = ''
  try {
    await api.put('/api/v1/admin/settings/theme', { theme: next })
    applySiteTheme(next)
    emit('update:modelValue', next)
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="admin-theme-toggle" aria-label="界面主题切换">
    <div><strong>全系统主题</strong><small>统一应用到学校管理员端、学生端与登录页。</small></div>
    <div class="theme-control">
      <div class="theme-segment">
        <button type="button" :disabled="saving" :class="{active:modelValue==='blue'}" @click="setTheme('blue')">经典蓝</button>
        <button type="button" :disabled="saving" :class="{active:modelValue==='green'}" @click="setTheme('green')">校园绿</button>
      </div>
      <small v-if="error" class="theme-error" role="alert">{{ error }}</small>
    </div>
  </div>
</template>

<style scoped>
.admin-theme-toggle{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:14px 16px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.admin-theme-toggle>div:first-child{display:grid;gap:4px}.admin-theme-toggle small{color:var(--muted);font-size:12px}.theme-control{display:grid;justify-items:end;gap:6px}.theme-segment{display:flex;gap:4px;padding:4px;border-radius:10px;background:var(--panel)}.theme-segment button{min-height:32px;padding:5px 12px;border:0;border-radius:8px;background:transparent;color:var(--muted);font-size:12px;font-weight:700;cursor:pointer}.theme-segment button:disabled{cursor:wait;opacity:.7}.theme-segment button.active{background:var(--soft);color:var(--primary);box-shadow:0 2px 8px rgba(15,23,42,.08)}.theme-error{max-width:320px;color:#b42318!important;text-align:right}@media(max-width:680px){.admin-theme-toggle{align-items:stretch;flex-direction:column}.theme-control{justify-items:stretch}.theme-segment button{flex:1}.theme-error{text-align:left}}
</style>
