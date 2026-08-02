<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'
const rows = ref<Record<string, unknown>[]>([])
const error = ref('')
onMounted(async () => { try { rows.value = await platformApi.audit(200) } catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' } })
</script>
<template><section><h1>平台审计</h1><p v-if="error" class="error">{{ error }}</p><div class="panel"><pre>{{ JSON.stringify(rows, null, 2) }}</pre></div></section></template>
<style scoped>.panel{background:#fff;padding:18px;border-radius:12px}.error{color:#b91c1c}pre{overflow:auto;white-space:pre-wrap}</style>
