<script setup lang="ts">
import type { DataObject } from '../../../api/types'
defineProps<{ health: DataObject }>()
</script>

<template>
  <section class="panel health-summary-card" :class="{ healthy: health.healthy, unhealthy: !health.healthy }">
    <div class="health-icon">{{ health.healthy ? '✓' : '!' }}</div>
    <div><span class="eyebrow">运行健康</span><h3>{{ health.healthy ? '系统运行正常' : '系统需要检查' }}</h3><p>{{ health.healthy ? '核心服务当前可用，可以继续开展学生、宿舍和选寝业务。' : '部分核心服务不可用，请联系系统管理员查看依赖、锁和数据库诊断详情。' }}</p></div>
    <span class="health-badge" :class="{ ok: health.healthy }">{{ health.healthy ? '健康' : '异常' }}</span>
  </section>
</template>

<style scoped>
.health-summary-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:16px}.health-summary-card.healthy{border-color:#c8eadc;background:#f7fdf9}.health-summary-card.unhealthy{border-color:#f1c9cf;background:#fff8f8}.health-summary-card h3{margin-bottom:4px}.health-summary-card p{margin:0}.health-icon{width:48px;height:48px;display:grid;place-items:center;border-radius:50%;color:white;background:#c33c4b;font-size:24px;font-weight:800}.healthy .health-icon{background:#158467}.health-badge{padding:6px 10px;border-radius:999px;background:#fff0f0;color:#a33}.health-badge.ok{background:#eafaf2;color:#16734f}@media(max-width:640px){.health-summary-card{grid-template-columns:1fr}.health-badge{justify-self:start}}
</style>
