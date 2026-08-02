<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

const subscription = ref<Record<string, unknown>>({})
const quotas = ref<Record<string, unknown>>({})
const error = ref('')

onMounted(async () => {
  try {
    ;[subscription.value, quotas.value] = await Promise.all([
      platformApi.subscription(),
      platformApi.quotas(),
    ])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  }
})
</script>

<template>
  <section>
    <h1>服务概览</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="grid">
      <article><h2>当前服务状态</h2><strong>{{ subscription.serviceStatus || '加载中' }}</strong></article>
      <article><h2>订阅类型</h2><strong>{{ subscription.subscriptionType || '加载中' }}</strong></article>
      <article><h2>当前套餐</h2><strong>{{ subscription.planName || '加载中' }}</strong></article>
      <article><h2>配额项目</h2><strong>{{ Array.isArray(quotas.usage) ? quotas.usage.length : 0 }}</strong></article>
    </div>
  </section>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }
article { padding: 20px; background: white; border-radius: 12px; box-shadow: 0 4px 16px #0f172a0d; }
h2 { font-size: 14px; color: #64748b; } strong { font-size: 22px; }.error { color: #b91c1c; }
</style>
