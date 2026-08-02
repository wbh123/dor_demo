<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const links = computed(() =>
  auth.isAdmin
    ? [
        { to: '/admin', label: '工作台', icon: '总' },
        { to: '/admin/data', label: '专业与学生', icon: '学' },
        { to: '/admin/dormitories', label: '宿舍资源', icon: '舍' },
        { to: '/admin/matching', label: '匹配规则', icon: '配' },
        { to: '/admin/batches', label: '选寝批次', icon: '批' },
        { to: '/admin/assignments', label: '分配与调整', icon: '调' },
      ]
    : [
        { to: '/student', label: '选寝首页', icon: '选' },
        { to: '/student/teams', label: '我的队伍', icon: '队' },
      ],
)

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">W</span>
        <div>
          <strong>武科大选寝</strong>
          <small>宿舍智能选择系统</small>
        </div>
      </div>

      <nav class="nav-list">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-item">
          <span class="nav-icon">{{ link.icon }}</span>
          <span>{{ link.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="user-card">
          <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span>
          <div>
            <strong>{{ auth.user?.displayName }}</strong>
            <small>{{ auth.isAdmin ? '业务管理员' : auth.user?.username }}</small>
          </div>
        </div>
        <button class="button ghost full" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div>
          <span class="eyebrow">WUST DORMITORY SELECT</span>
          <h1>{{ auth.isAdmin ? '管理控制台' : '学生选寝中心' }}</h1>
        </div>
      </header>
      <section class="page-container">
        <RouterView />
      </section>
    </main>
  </div>
</template>
