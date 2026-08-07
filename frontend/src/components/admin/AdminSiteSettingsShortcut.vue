<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const visible = computed(() => auth.isAdmin && route.path.startsWith('/admin'))
</script>

<template>
  <Teleport v-if="visible" to=".sidebar-foot">
    <RouterLink to="/admin/settings/site" class="button ghost full sidebar-action-link site-settings-shortcut">界面与登录页设置</RouterLink>
  </Teleport>
</template>
