<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

const catalog = ref<Record<string, unknown>[]>([])
const overrides = ref<Record<string, unknown>[]>([])
const error = ref('')
const form = ref({ featureCode: '', overrideType: 'GRANT', reason: '' })
async function load() { [catalog.value, overrides.value] = await Promise.all([platformApi.features(), platformApi.featureOverrides()]) }
onMounted(() => void load().catch(show))
function show(cause: unknown) { error.value = cause instanceof Error ? cause.message : '加载失败' }
async function submit() { try { await platformApi.addFeatureOverride(form.value); form.value = { featureCode: '', overrideType: 'GRANT', reason: '' }; await load() } catch (cause) { show(cause) } }
</script>
<template><section><h1>功能授权</h1><p>权限目录随程序版本固化，只能增补或移除已有权限。</p><p v-if="error" class="error">{{ error }}</p>
  <form class="panel form" @submit.prevent="submit"><input v-model.trim="form.featureCode" placeholder="功能代码" required /><select v-model="form.overrideType"><option value="GRANT">增补</option><option value="REVOKE">移除</option></select><textarea v-model="form.reason" placeholder="调整原因" required /><button>保存覆盖</button></form>
  <div class="panel"><h2>当前覆盖记录</h2><pre>{{ JSON.stringify(overrides, null, 2) }}</pre></div>
  <div class="panel"><h2>固化功能目录</h2><pre>{{ JSON.stringify(catalog, null, 2) }}</pre></div>
</section></template>
<style scoped>.panel{background:#fff;padding:18px;border-radius:12px;margin-bottom:16px}.form{display:grid;grid-template-columns:2fr 1fr;gap:10px}.form textarea,.form button{grid-column:1/-1}input,select,textarea,button{padding:10px;border:1px solid #cbd5e1;border-radius:8px}button{background:#1d4ed8;color:white;border:0}.error{color:#b91c1c}pre{overflow:auto;white-space:pre-wrap}</style>
