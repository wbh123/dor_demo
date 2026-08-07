<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

type ThemeName = 'blue' | 'green'
const STORAGE_KEY = 'wust-dormitory-theme'
const theme = ref<ThemeName>('blue')

onMounted(() => {
  const stored = localStorage.getItem(STORAGE_KEY)
  theme.value = stored === 'green' ? 'green' : 'blue'
  applyTheme()
})
watch(theme, applyTheme)

function setTheme(next: ThemeName) {
  theme.value = next
  localStorage.setItem(STORAGE_KEY, next)
}
function applyTheme() {
  document.documentElement.dataset.wustTheme = theme.value
}
</script>

<template>
  <div class="admin-theme-toggle" aria-label="界面主题切换">
    <div><strong>全系统主题</strong><small>统一应用到学校管理员端、学生端与登录页。</small></div>
    <div class="theme-segment">
      <button type="button" :class="{active:theme==='blue'}" @click="setTheme('blue')">经典蓝</button>
      <button type="button" :class="{active:theme==='green'}" @click="setTheme('green')">校园绿</button>
    </div>
  </div>
</template>

<style scoped>
.admin-theme-toggle{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:14px 16px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}.admin-theme-toggle>div:first-child{display:grid;gap:4px}.admin-theme-toggle small{color:var(--muted);font-size:12px}.theme-segment{display:flex;gap:4px;padding:4px;border-radius:10px;background:var(--panel)}.theme-segment button{min-height:32px;padding:5px 12px;border:0;border-radius:8px;background:transparent;color:var(--muted);font-size:12px;font-weight:700;cursor:pointer}.theme-segment button.active{background:var(--soft);color:var(--primary);box-shadow:0 2px 8px rgba(15,23,42,.08)}@media(max-width:680px){.admin-theme-toggle{align-items:stretch;flex-direction:column}.theme-segment button{flex:1}}
</style>
