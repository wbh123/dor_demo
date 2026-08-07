<script setup lang="ts">
import AdminSiteSettingsShortcut from './components/admin/AdminSiteSettingsShortcut.vue'
</script>

<template>
  <RouterView />
  <AdminSiteSettingsShortcut />
</template>

<style>
.welcome-setting-card{position:relative;padding-top:22px}.welcome-setting-card .welcome-actions{position:absolute;top:18px;right:20px;z-index:2;margin:0}.welcome-setting-card .welcome-editor-workspace{padding-top:28px}@media(max-width:760px){.welcome-setting-card .welcome-actions{position:static;justify-content:flex-end;margin-bottom:10px}.welcome-setting-card .welcome-editor-workspace{padding-top:0}}
</style>