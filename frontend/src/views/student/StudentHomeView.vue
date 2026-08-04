<script setup lang="ts">
import StudentHomeContent from './StudentHomeContent.vue'

const adminContactPhone = String(import.meta.env.VITE_ADMIN_CONTACT_PHONE || '000-0000-0000')
</script>

<template>
  <div class="student-home-wrapper compact-home-top-card">
    <StudentHomeContent />
    <footer class="student-admin-contact">有疑问请致电管理员：<strong>{{ adminContactPhone }}</strong></footer>
  </div>
</template>

<style scoped>
.student-home-wrapper{position:relative}.student-admin-contact{display:flex;justify-content:center;align-items:center;gap:6px;margin-top:12px;padding:14px;color:var(--muted);font-size:13px;text-align:center}.student-admin-contact strong{color:var(--text);font-variant-numeric:tabular-nums}
</style>
