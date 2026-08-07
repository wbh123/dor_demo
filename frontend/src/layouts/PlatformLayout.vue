<script setup lang="ts">
import { useRouter } from 'vue-router'
import { usePlatformSession } from '../platform/session'

const router = useRouter()
const { state, clearSession } = usePlatformSession()
const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')
const operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '')
const icpRecord = String(import.meta.env.VITE_ICP_RECORD || '')
const showOperatorInfo = String(import.meta.env.VITE_SHOW_OPERATOR_INFO || 'false').toLowerCase() === 'true' && Boolean(operatorName.trim())
const showIcpRecord = String(import.meta.env.VITE_SHOW_ICP_RECORD || 'false').toLowerCase() === 'true' && Boolean(icpRecord.trim())

function logout() {
  clearSession()
  void router.replace('/platform/login')
}
</script>

<template>
  <div class="platform-shell">
    <aside class="platform-nav">
      <div class="platform-brand"><strong>系统服务管理</strong><span>统一运维平台</span></div>
      <nav>
        <RouterLink to="/platform" exact-active-class="custom-active">服务概览</RouterLink>
        <RouterLink to="/platform/plans" exact-active-class="custom-active">套餐管理</RouterLink>
        <RouterLink to="/platform/subscription" exact-active-class="custom-active">服务订阅</RouterLink>
        <RouterLink to="/platform/features" exact-active-class="custom-active">功能授权</RouterLink>
        <RouterLink to="/platform/quotas" exact-active-class="custom-active">资源配额</RouterLink>
        <RouterLink to="/platform/site-metadata" exact-active-class="custom-active">学校与登录页</RouterLink>
        <RouterLink to="/platform/audit" exact-active-class="custom-active">操作审计</RouterLink>
        <RouterLink to="/platform/profile/password" exact-active-class="custom-active">修改密码</RouterLink>
      </nav>
      <div class="platform-nav-foot"><button type="button" class="logout" @click="logout">退出登录</button></div>
    </aside>
    <main class="platform-main">
      <header><div><strong>{{ state.user?.displayName }}</strong><span>{{ institutionName }}系统服务管理</span></div><span class="single-customer-badge">单客户运行</span></header>
      <RouterView />
      <footer v-if="showOperatorInfo || showIcpRecord" class="platform-page-compliance"><span v-if="showOperatorInfo">{{ operatorName }}</span><span v-if="showIcpRecord">{{ icpRecord }}</span></footer>
    </main>
  </div>
</template>

<style scoped>
.platform-shell{min-height:100vh;background:#f3f6fb;color:#172033}.platform-nav{position:fixed;inset:0 auto 0 0;z-index:30;display:flex;flex-direction:column;width:252px;height:100vh;padding:24px 18px;overflow:hidden;color:#eaf0ff;background:linear-gradient(180deg,#102c64,#0f2148)}.platform-brand{display:grid;gap:5px;padding:4px 4px 22px;border-bottom:1px solid rgba(255,255,255,.12)}.platform-brand strong{font-size:1.08rem}.platform-brand span{color:#aebfe5;font-size:.76rem;font-weight:700;letter-spacing:.08em}nav{display:grid;gap:7px;min-height:0;margin-top:22px;overflow-y:auto;scrollbar-width:none}nav::-webkit-scrollbar{display:none}a{padding:11px 13px;border-radius:11px;color:#b9c8e9;text-decoration:none;transition:.18s ease}a:hover,a.custom-active{color:#fff;background:rgba(255,255,255,.11)}.platform-nav-foot{display:grid;gap:14px;flex:0 0 auto;margin-top:auto}.logout{min-height:42px;border:1px solid rgba(255,255,255,.18);border-radius:11px;color:#dbe6ff;background:transparent;cursor:pointer}.platform-main{display:flex;flex-direction:column;min-width:0;min-height:100vh;margin-left:252px;padding:28px clamp(22px,4vw,56px) 24px}.platform-main>:not(header):not(.platform-page-compliance){flex:1 0 auto}.platform-page-compliance{display:flex;justify-content:center;gap:12px;flex-wrap:wrap;margin-top:28px;padding:18px 0;color:#69758b;font-size:.72rem;text-align:center}.platform-page-compliance span+span::before{content:"·";margin-right:12px}.platform-main>header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:28px;padding:16px 20px;border:1px solid #dde4ef;border-radius:16px;background:rgba(255,255,255,.88);box-shadow:0 12px 28px rgba(22,43,82,.06)}.platform-main>header div{display:grid;gap:4px}.platform-main>header strong{font-size:1rem}.platform-main>header span{color:#69758b;font-size:.76rem}.single-customer-badge{padding:7px 11px;border-radius:999px;color:#17664f!important;background:#e8f8f2;font-weight:700}@media(max-width:800px){.platform-nav{position:static;width:auto;height:auto;overflow:visible}.platform-main{margin-left:0}.platform-shell{display:block}}
</style>