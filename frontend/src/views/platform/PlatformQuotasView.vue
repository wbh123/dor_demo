<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

const summary = ref<Record<string, unknown>>({})
const error = ref('')
const form = ref({ quotaCode: '', quotaValue: 0, reason: '' })
async function load() { summary.value = await platformApi.quotas() }
onMounted(() => void load().catch(show))
function show(cause: unknown) { error.value = cause instanceof Error ? cause.message : '加载失败' }
async function submit() { try { await platformApi.addQuotaOverride(form.value); form.value = { quotaCode: '', quotaValue: 0, reason: '' }; await load() } catch (cause) { show(cause) } }
</script>
<template><section><h1>资源配额</h1><p>达到80%时预警，达到100%后禁止新增；降级后的存量超额不会被删除。</p><p v-if="error" class="error">{{ error }}</p>
  <form class="panel form" @submit.prevent="submit"><input v-model.trim="form.quotaCode" placeholder="配额代码" required /><input v-model.number="form.quotaValue" type="number" min="0" placeholder="覆盖值" required /><textarea v-model="form.reason" placeholder="调整原因" required /><button>保存覆盖</button></form>
  <div class="panel"><h2>配额目录、覆盖和使用率</h2><pre>{{ JSON.stringify(summary, null, 2) }}</pre></div>
</section></template>
<style scoped>.panel{background:#fff;padding:18px;border-radius:12px;margin-bottom:16px}.form{display:grid;grid-template-columns:1fr 1fr;gap:10px}.form textarea,.form button{grid-column:1/-1}input,textarea,button{padding:10px;border:1px solid #cbd5e1;border-radius:8px}button{background:#1d4ed8;color:white;border:0}.error{color:#b91c1c}pre{overflow:auto;white-space:pre-wrap}</style>
