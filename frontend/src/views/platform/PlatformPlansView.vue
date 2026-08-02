<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { platformApi } from '../../platform/api'

const rows = ref<Record<string, unknown>[]>([])
const error = ref('')
const form = ref({ planCode: '', planName: '', revisionName: '', description: '', reason: '', features: '', quotas: '{}' })

async function load() { rows.value = await platformApi.plans() }
onMounted(() => void load().catch(cause => { error.value = cause instanceof Error ? cause.message : '加载失败' }))

async function createPlan() {
  error.value = ''
  try {
    await platformApi.createPlan({
      planCode: form.value.planCode,
      planName: form.value.planName,
      revisionName: form.value.revisionName,
      description: form.value.description,
      reason: form.value.reason,
      features: form.value.features.split('\n').map(v => v.trim()).filter(Boolean),
      quotas: JSON.parse(form.value.quotas),
    })
    form.value = { planCode: '', planName: '', revisionName: '', description: '', reason: '', features: '', quotas: '{}' }
    await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '创建失败' }
}
</script>

<template>
  <section><h1>套餐与不可变修订</h1><p v-if="error" class="error">{{ error }}</p>
    <div class="panel"><h2>已有套餐</h2><pre>{{ JSON.stringify(rows, null, 2) }}</pre></div>
    <form class="panel form" @submit.prevent="createPlan"><h2>创建套餐首个修订</h2>
      <input v-model.trim="form.planCode" placeholder="套餐编码" required /><input v-model.trim="form.planName" placeholder="套餐名称" required />
      <input v-model.trim="form.revisionName" placeholder="修订名称" required /><textarea v-model="form.description" placeholder="说明" />
      <textarea v-model="form.features" placeholder="每行一个功能代码" required /><textarea v-model="form.quotas" placeholder='{"MAX_STUDENTS":10000}' required />
      <textarea v-model="form.reason" placeholder="创建原因" required /><button>创建</button>
    </form>
  </section>
</template>
<style scoped>.panel{background:#fff;padding:18px;border-radius:12px;margin-bottom:16px}.form{display:grid;gap:10px}input,textarea,button{padding:10px;border:1px solid #cbd5e1;border-radius:8px}button{background:#1d4ed8;color:white;border:0}.error{color:#b91c1c}pre{overflow:auto;white-space:pre-wrap}</style>
