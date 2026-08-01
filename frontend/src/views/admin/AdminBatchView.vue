<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { BatchRequest, DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const batches = ref<DataObject[]>([])
const error = ref('')
const message = ref('')
const preview = ref<DataObject | null>(null)
const previewBatchId = ref<number | null>(null)
const form = reactive({
  batchCode: '',
  batchName: '',
  startAt: '',
  endAt: '',
  holdDurationSeconds: 300,
  allowTeam: true,
  teamMaxSize: 5,
  allowStudentRandom: true,
})

onMounted(load)

async function load() {
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/batches')
    batches.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次加载失败'
  }
}

async function createBatch() {
  error.value = ''
  try {
    const payload: BatchRequest = {
      ...form,
      startAt: new Date(form.startAt).toISOString(),
      endAt: new Date(form.endAt).toISOString(),
    }
    await api.post('/api/v1/admin/batches', payload)
    message.value = '批次已创建为草稿。'
    form.batchCode = ''
    form.batchName = ''
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次创建失败'
  }
}

async function prepare(batchId: unknown) {
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${Number(batchId)}/prepare`)
    message.value = '已加入全部启用专业学生和启用宿舍楼范围。'
  })
}

async function changeStatus(batchId: unknown, target: string) {
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${Number(batchId)}/status/${target}`)
    message.value = `批次状态已更新为 ${target}。`
  })
}

async function previewAllocation(batchId: unknown) {
  error.value = ''
  try {
    const id = Number(batchId)
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/admin/batches/${id}/allocation/preview`,
      { params: { randomSeed: 20260801 } },
    )
    preview.value = (response.data.data ?? {}) as DataObject
    previewBatchId.value = id
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '分配预演失败'
  }
}

async function commitAllocation() {
  if (!previewBatchId.value) return
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${previewBatchId.value}/allocation/commit`, {
      randomSeed: 20260801,
      idempotencyKey: crypto.randomUUID(),
    })
    message.value = '统一分配已提交，结果和执行明细已保存。'
    preview.value = null
    previewBatchId.value = null
  })
}

async function download(batchId: unknown) {
  try {
    const response = await api.get(`/api/v1/admin/batches/${Number(batchId)}/assignments.csv`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `assignments-${batchId}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '导出失败'
  }
}

async function run(action: () => Promise<void>) {
  error.value = ''
  message.value = ''
  try {
    await action()
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '操作失败'
  }
}

function nextActions(status: unknown) {
  const actions: Record<string, string[]> = {
    DRAFT: ['PUBLISHED', 'CANCELLED'],
    PUBLISHED: ['OPEN', 'CANCELLED'],
    OPEN: ['PAUSED', 'CLOSED'],
    PAUSED: ['OPEN', 'CLOSED'],
    CLOSED: ['ALLOCATING', 'FINISHED'],
    ALLOCATING: ['FINISHED', 'CLOSED'],
  }
  return actions[String(status)] ?? []
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">SELECTION OPERATIONS</span>
      <h2>选寝批次与统一分配</h2>
      <p>批次按状态机流转。预演不会写入正式分配，正式执行使用幂等键防止重复提交。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">NEW BATCH</span><h3>创建选寝批次</h3></div></div>
      <form class="form-grid three-column" @submit.prevent="createBatch">
        <label><span>批次编号</span><input v-model.trim="form.batchCode" class="input" required maxlength="32" /></label>
        <label><span>批次名称</span><input v-model.trim="form.batchName" class="input" required maxlength="128" /></label>
        <label><span>占用秒数</span><input v-model.number="form.holdDurationSeconds" class="input" type="number" min="30" max="3600" /></label>
        <label><span>开始时间</span><input v-model="form.startAt" class="input" type="datetime-local" required /></label>
        <label><span>结束时间</span><input v-model="form.endAt" class="input" type="datetime-local" required /></label>
        <label><span>队伍最大人数</span><input v-model.number="form.teamMaxSize" class="input" type="number" min="2" max="20" /></label>
        <label class="checkbox-line"><input v-model="form.allowTeam" type="checkbox" />允许组队选寝</label>
        <label class="checkbox-line"><input v-model="form.allowStudentRandom" type="checkbox" />允许学生随机推荐</label>
        <button class="button primary">创建草稿批次</button>
      </form>
    </section>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">BATCH LIST</span><h3>批次管理</h3></div></div>
      <div class="batch-list admin-batches">
        <article v-for="batch in batches" :key="String(batch.id)" class="batch-card">
          <div class="batch-title">
            <div><span class="status-chip compact">{{ batch.batch_status }}</span><h3>{{ batch.batch_name }}</h3><p>{{ batch.batch_code }}</p></div>
            <strong>{{ batch.assigned_count }}/{{ batch.eligible_count }}</strong>
          </div>
          <div class="button-row wrap">
            <button v-if="batch.batch_status === 'DRAFT'" class="button secondary small" @click="prepare(batch.id)">准备资格和宿舍范围</button>
            <button v-for="target in nextActions(batch.batch_status)" :key="target" class="button ghost small" @click="changeStatus(batch.id, target)">{{ target }}</button>
            <button v-if="['CLOSED', 'ALLOCATING'].includes(String(batch.batch_status))" class="button accent small" @click="previewAllocation(batch.id)">统一分配预演</button>
            <button class="button ghost small" @click="download(batch.id)">导出结果</button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="preview" class="panel preview-panel">
      <div class="section-head"><div><span class="eyebrow">ALLOCATION PREVIEW</span><h3>统一分配预演</h3></div></div>
      <div class="stat-grid compact-grid">
        <article class="stat-card"><span>待分配学生</span><strong>{{ (preview.summary as DataObject)?.studentCount }}</strong></article>
        <article class="stat-card"><span>可用床位</span><strong>{{ (preview.summary as DataObject)?.availableBedCount }}</strong></article>
        <article class="stat-card"><span>预计成功</span><strong>{{ (preview.summary as DataObject)?.assignedCount }}</strong></article>
        <article class="stat-card"><span>无法分配</span><strong>{{ (preview.summary as DataObject)?.unassignedCount }}</strong></article>
      </div>
      <div class="button-row"><button class="button ghost" @click="preview = null">关闭预演</button><button class="button primary" @click="commitAllocation">确认正式执行</button></div>
    </section>
  </div>
</template>
