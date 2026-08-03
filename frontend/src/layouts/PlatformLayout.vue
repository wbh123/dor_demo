<script setup lang="ts">
import { useRouter } from 'vue-router'
import { usePlatformSession } from '../platform/session'

const router = useRouter()
const { state, clearSession } = usePlatformSession()
const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '运营单位信息待填写')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || 'ICP备案信息待填写')

function logout() {
  clearSession()
  void router.replace('/platform/login')
}
</script>

<template>
  <div class="platform-shell">
    <aside class="platform-nav">
      <div class="platform-brand"><img src="/logo-title-right.png" :alt="`${institutionName}校徽`" /><span>系统服务管理</span></div>
      <nav>
        <RouterLink to="/platform">服务概览</RouterLink>
        <RouterLink to="/platform/plans">套餐管理</RouterLink>
        <RouterLink to="/platform/subscription">服务订阅</RouterLink>
        <RouterLink to="/platform/features">功能授权</RouterLink>
        <RouterLink to="/platform/quotas">资源配额</RouterLink>
        <RouterLink to="/platform/audit">操作审计</RouterLink>
        <RouterLink to="/platform/profile/password">修改密码</RouterLink>
      </nav>
      <div class="platform-nav-foot"><button type="button" class="logout" @click="logout">退出登录</button><footer><span>{{ operatorName }}</span><span>{{ icpRecord }}</span></footer></div>
    </aside>
    <main class="platform-main">
      <header><div><strong>{{ state.user?.displayName }}</strong><span>{{ institutionName }}系统服务管理</span></div><span class="single-customer-badge">单客户运行</span></header>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.platform-shell { min-height: 100vh; background: #f3f6fb; color: #172033; }
.platform-nav { position: fixed; inset: 0 auto 0 0; z-index: 30; width: 252px; height: 100vh; padding: 24px 18px; overflow-y: auto; color: #eaf0ff; background: linear-gradient(180deg,#102c64,#0f2148); display: flex; flex-direction: column; }
.platform-brand { display: grid; gap: 10px; padding: 2px 4px 22px; border-bottom: 1px solid rgba(255,255,255,.12); }
.platform-brand img { width: 100%; max-height: 56px; object-fit: contain; object-position: left center; }
.platform-brand span { color: #aebfe5; font-size: .76rem; font-weight: 700; letter-spacing: .08em; }
nav { display: grid; gap: 7px; margin-top: 22px; }
a { color: #b9c8e9; text-decoration: none; padding: 11px 13px; border-radius: 11px; transition: .18s ease; }
a:hover, a.router-link-exact-active, a.router-link-active { color: white; background: rgba(255,255,255,.11); }
.platform-nav-foot { margin-top: auto; display: grid; gap: 14px; }
.logout { min-height: 42px; border: 1px solid rgba(255,255,255,.18); border-radius: 11px; color: #dbe6ff; background: transparent; cursor: pointer; }
footer { display: grid; gap: 4px; padding-top: 12px; border-top: 1px solid rgba(255,255,255,.12); color: #91a8d5; font-size: .66rem; line-height: 1.45; }
.platform-main { min-width: 0; min-height: 100vh; margin-left: 252px; padding: 28px clamp(22px,4vw,56px) 70px; }
header { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 28px; padding: 16px 20px; border: 1px solid #dde4ef; border-radius: 16px; background: rgba(255,255,255,.88); box-shadow: 0 12px 28px rgba(22,43,82,.06); }
header div { display: grid; gap: 4px; } header strong { font-size: 1rem; } header span { color: #69758b; font-size: .76rem; }
.single-customer-badge { padding: 7px 11px; border-radius: 999px; color: #17664f; background: #e8f8f2; font-weight: 700; }
@media (max-width: 800px) { .platform-nav { position: static; width: auto; height: auto; } .platform-main { margin-left: 0; } .platform-shell { display: block; } }
</style>
