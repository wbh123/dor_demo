<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from './api/client'
import type { DataObject, ObjectSuccessResponse } from './api/types'
import AdminSiteSettingsShortcut from './components/admin/AdminSiteSettingsShortcut.vue'
import { applySiteTheme, clearSiteTheme } from './site/theme'

const route = useRoute()
let themeRequestVersion = 0

watch(
  () => route.path,
  async (path) => {
    const requestVersion = ++themeRequestVersion
    if (path.startsWith('/platform')) {
      clearSiteTheme()
      return
    }
    if (path === '/login') return
    try {
      const response = await api.get<ObjectSuccessResponse>('/api/v1/public/site-config')
      if (requestVersion !== themeRequestVersion) return
      const data = (response.data.data ?? {}) as DataObject
      applySiteTheme(data.theme)
    } catch {
      if (requestVersion === themeRequestVersion) applySiteTheme('blue')
    }
  },
  { immediate: true },
)
</script>

<template>
  <RouterView />
  <AdminSiteSettingsShortcut />
</template>

<style>
.welcome-setting-card{position:relative;padding-top:22px}.welcome-setting-card .welcome-actions{position:absolute;top:18px;right:20px;z-index:2;margin:0}.welcome-setting-card .welcome-editor-workspace{padding-top:28px}@media(max-width:760px){.welcome-setting-card .welcome-actions{position:static;justify-content:flex-end;margin-bottom:10px}.welcome-setting-card .welcome-editor-workspace{padding-top:0}}
</style>
