<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

const current = ref<Record<string, unknown>>({})
const history = ref<Record<string, unknown>[]>([])
const error = ref('')
const planRevisionId = ref<number | null>(null)
const direction = ref<'UPGRADE' | 'DOWNGRADE'>('UPGRADE')
const reason = ref('')
const contractNumber = ref('')
const preview = ref<Record<string, unknown> | null>(null)

async function load() { [current.value, history.value] = await Promise.all([platformApi.subscription(), platformApi.subscriptionHistory()]) }
onMounted(() => void load().catch(showError))
function showError(cause: unknown) { error.value = cause instanceof Error ? cause.message : '操作失败' }
async function loadPreview() { if (planRevisionId.value) preview.value = await platformApi.previewChange(planRevisionId.value) }
async function changePlan() {
  if (!planRevisionId.value) return
  await platformApi.changePlan({ planRevisionId: planRevisionId.value, direction: direction.value, contractNumber: contractNumber.value || null, reason: reason.value })
  preview.value = null; reason.value = ''; await load()
}
async function status(action: string) { const value = prompt('请输入操作原因'); if (!value) return; await platformApi.changeStatus(action, value); await load() }
</script>
<template><section><h1>服务订阅</h1><p v-if="error" class="error">{{ error }}</p>
  <div class="panel"><h2>当前订阅</h2><pre>{{ JSON.stringify(current, null, 2) }}</pre>
    <div class="actions"><button @click="status('SUSPEND')">暂停</button><button @click="status('RESUME')">恢复</button><button @click="status('TERMINATE')">终止</button><button class="danger" @click="status('EMERGENCY_STOP')">紧急停止</button><button @click="status('EMERGENCY_RESUME')">解除紧急停止</button></div>
  </div>
  <form class="panel form" @submit.prevent="changePlan"><h2>立即升级或降级</h2>
    <input v-model.number="planRevisionId" type="number" min="1" placeholder="目标套餐修订编号" required />
    <select v-model="direction"><option value="UPGRADE">立即升级</option><option value="DOWNGRADE">立即降级</option></select>
    <input v-model.trim="contractNumber" placeholder="合同编号（可选）" /><textarea v-model="reason" placeholder="变更原因" required />
    <button type="button" @click="loadPreview">影响预览</button><pre v-if="preview">{{ JSON.stringify(preview, null, 2) }}</pre><button>确认生效</button>
  </form>
  <div class="panel"><h2>不可变订阅修订历史</h2><pre>{{ JSON.stringify(history, null, 2) }}</pre></div>
</section></template>
<style scoped>.panel{background:#fff;padding:18px;border-radius:12px;margin-bottom:16px}.form{display:grid;gap:10px}.actions{display:flex;gap:8px;flex-wrap:wrap}input,select,textarea,button{padding:10px;border:1px solid #cbd5e1;border-radius:8px}button{cursor:pointer}.danger{background:#b91c1c;color:#fff}.error{color:#b91c1c}pre{overflow:auto;white-space:pre-wrap}</style>
