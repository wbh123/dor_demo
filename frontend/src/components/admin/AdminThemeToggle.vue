<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

type ThemeName = 'blue' | 'green'
const STORAGE_KEY = 'wust-dormitory-theme'
const auth = useAuthStore()
const route = useRoute()
const theme = ref<ThemeName>('blue')
const visible = computed(() => auth.isAdmin && route.path.startsWith('/admin'))

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
  <div v-if="visible" class="admin-theme-toggle" aria-label="界面主题切换">
    <span>主题</span>
    <div class="theme-segment">
      <button type="button" :class="{active:theme==='blue'}" @click="setTheme('blue')">经典蓝</button>
      <button type="button" :class="{active:theme==='green'}" @click="setTheme('green')">武科大绿</button>
    </div>
  </div>
</template>

<style scoped>
.admin-theme-toggle{position:fixed;z-index:55;top:14px;right:22px;display:flex;align-items:center;gap:8px;padding:6px 8px;border:1px solid var(--line);border-radius:12px;background:color-mix(in srgb,var(--panel) 94%,transparent);box-shadow:0 8px 24px rgba(15,23,42,.08);backdrop-filter:blur(12px)}.admin-theme-toggle>span{padding-left:4px;color:var(--muted);font-size:11px;font-weight:700}.theme-segment{display:flex;gap:3px;padding:3px;border-radius:9px;background:var(--soft)}.theme-segment button{min-height:28px;padding:4px 9px;border:0;border-radius:7px;background:transparent;color:var(--muted);font-size:11px;font-weight:700;cursor:pointer}.theme-segment button.active{background:var(--panel);color:var(--primary);box-shadow:0 2px 8px rgba(15,23,42,.09)}@media(max-width:820px){.admin-theme-toggle{position:sticky;top:8px;right:auto;justify-self:end;width:max-content;margin:8px 12px 0 auto}}
</style>
