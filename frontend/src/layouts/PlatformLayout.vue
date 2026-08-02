<script setup lang="ts">
import { useRouter } from 'vue-router'
import { usePlatformSession } from '../platform/session'

const router = useRouter()
const { state, clearSession } = usePlatformSession()

function logout() {
  clearSession()
  void router.replace('/platform/login')
}
</script>

<template>
  <div class="platform-shell">
    <aside class="platform-nav">
      <div class="brand">系统服务管理</div>
      <RouterLink to="/platform">概览</RouterLink>
      <RouterLink to="/platform/plans">套餐修订</RouterLink>
      <RouterLink to="/platform/subscription">服务订阅</RouterLink>
      <RouterLink to="/platform/features">功能授权</RouterLink>
      <RouterLink to="/platform/quotas">资源配额</RouterLink>
      <RouterLink to="/platform/audit">平台审计</RouterLink>
      <RouterLink to="/platform/profile/password">修改密码</RouterLink>
      <button type="button" class="logout" @click="logout">退出登录</button>
    </aside>
    <main class="platform-main">
      <header>
        <strong>{{ state.user?.displayName }}</strong>
        <span>独立系统管理入口</span>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.platform-shell { min-height: 100vh; display: grid; grid-template-columns: 240px 1fr; background: #f4f6f8; color: #17202a; }
.platform-nav { padding: 24px 18px; background: #111827; display: flex; flex-direction: column; gap: 8px; }
.brand { color: white; font-weight: 700; font-size: 18px; margin-bottom: 18px; }
a { color: #cbd5e1; text-decoration: none; padding: 10px 12px; border-radius: 8px; }
a.router-link-active { color: white; background: #334155; }
.logout { margin-top: auto; border: 0; border-radius: 8px; padding: 10px; cursor: pointer; }
.platform-main { min-width: 0; padding: 24px; }
header { display: flex; justify-content: space-between; margin-bottom: 20px; color: #475569; }
@media (max-width: 800px) { .platform-shell { grid-template-columns: 1fr; } .platform-nav { position: static; } }
</style>
