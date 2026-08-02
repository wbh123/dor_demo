<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { BatchCopyRequest, BatchRequest, DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const batches = ref<DataObject[]>([])
const error = ref('')
const message = ref('')
const preview = ref<DataObject | null>(null)
const previewBatchId = ref<number | null>(null)
const copyDialog = ref(false)
const copying = ref(false)
const copySource = ref<DataObject | null>(null)
const copyForm = reactive({
  batchCode: '',
  batchName: '',
  startAt: '',
  endAt: '',
  reason: '',
})
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

const stateGuide = [
  { status: 'DRAFT', label: '草稿', description: '配置资格与宿舍' },
  { status: 'PUBLISHED', label: '已发布', description: '学生可填写问卷' },
  { status: 'OPEN', label: '选寝中', description: '允许选择和确认床位' },
  { status: 'PAUSED', label: '已暂停', description: '临时停止选寝' },
  { status: 'CLOSED', label: '已关闭', description: '学生入口关闭' },
  { status: 'FINISHED', label: '已完成', description: '结果已经确定' },
]

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

function openCopy(batch: DataObject) {
  error.value = ''
  message.value = ''
  copySource.value = batch
  copyForm.batchCode = ''
  copyForm.batchName = ''
  copyForm.startAt = ''
  copyForm.endAt = ''
  copyForm.reason = ''
  copyDialog.value = true
}

function closeCopy() {
  if (copying.value) return
  copyDialog.value = false
  copySource.value = null
}

async function copyBatch() {
  if (!copySource.value) return
  error.value = ''
  message.value = ''
  copying.value = true
  try {
    const payload: BatchCopyRequest = {
      batchCode: copyForm.batchCode,
      batchName: copyForm.batchName,
      startAt: new Date(copyForm.startAt).toISOString(),
      endAt: new Date(copyForm.endAt).toISOString(),
      reason: copyForm.reason,
    }
    const response = await api.post<ObjectSuccessResponse>(
      `/api/v1/admin/batches/${Number(copySource.value.id)}/copy`,
      payload,
    )
    const copied = (response.data.data ?? {}) as DataObject
    message.value = `批次配置已复制为草稿：楼栋${Number(copied.buildingScopeCount ?? 0)}、房间${Number(copied.roomScopeCount ?? 0)}、床位${Number(copied.bedScopeCount ?? 0)}。学生资格、队伍和分配结果未复制。`
    copyDialog.value = false
    copySource.value = null
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次配置复制失败'
  } finally {
    copying.value = false
  }
}

async function prepare(batchId: unknown) {
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${Number(batchId)}/prepare`)
    message.value = '学生资格和可选宿舍范围已准备。'
  })
}

async function changeStatus(batchId: unknown, target: string) {
  await run(async () => {
    await api.post(`/api/v1/admin/batches/${Number(batchId)}/status/${target}`)
    message.value = `批次已切换为“${statusText(target)}”。`
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
    message.value = '统一分配已正式执行，结果和明细已保存。'
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

function statusText(status: unknown) {
  const texts: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    OPEN: '选寝中',
    PAUSED: '已暂停',
    CLOSED: '已关闭',
    ALLOCATING: '分配中',
    FINISHED: '已完成',
    CANCELLED: '已取消',
  }
  return texts[String(status)] ?? String(status)
}

function actionText(status: string) {
  const texts: Record<string, string> = {
    PUBLISHED: '发布活动',
    OPEN: '开放选寝',
    PAUSED: '暂停选寝',
    CLOSED: '结束选寝',
    ALLOCATING: '进入统一分配',
    FINISHED: '标记完成',
    CANCELLED: '取消批次',
  }
  return texts[status] ?? statusText(status)
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">SELECTION OPERATIONS</span>
      <h2>选寝批次与统一分配</h2>
      <p>已发布表示学生可以查看活动并填写问卷；选寝中表示学生可以正式选择和确认床位。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div v-if="copyDialog" class="batch-copy-overlay" @click.self="closeCopy">
      <section class="batch-copy-dialog" role="dialog" aria-modal="true" aria-labelledby="batch-copy-title">
        <header class="batch-copy-head">
          <div>
            <span class="eyebrow">COPY BATCH CONFIGURATION</span>
            <h3 id="batch-copy-title">复制批次配置</h3>
            <p>来源：{{ copySource?.batch_name }}。只复制规则和宿舍范围，不复制学生、队伍、占用或分配结果。</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭复制窗口" :disabled="copying" @click="closeCopy">×</button>
        </header>
        <form class="batch-copy-form" @submit.prevent="copyBatch">
          <label><span>新批次编号</span><input v-model.trim="copyForm.batchCode" class="input" required maxlength="32" pattern="[A-Za-z0-9_-]+" /></label>
          <label><span>新批次名称</span><input v-model.trim="copyForm.batchName" class="input" required maxlength="128" /></label>
          <label><span>新开始时间</span><input v-model="copyForm.startAt" class="input" type="datetime-local" required /></label>
          <label><span>新结束时间</span><input v-model="copyForm.endAt" class="input" type="datetime-local" required /></label>
          <label class="batch-copy-reason"><span>复制原因</span><textarea v-model.trim="copyForm.reason" class="input" required maxlength="500" rows="3" placeholder="例如：复用上一年度选寝配置"></textarea></label>
          <div class="batch-copy-actions">
            <button class="button ghost" type="button" :disabled="copying" @click="closeCopy">取消</button>
            <button class="button primary" type="submit" :disabled="copying">{{ copying ? '正在复制…' : '创建草稿副本' }}</button>
          </div>
        </form>
      </section>
    </div>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">STATUS GUIDE</span><h3>批次状态流程</h3></div></div>
      <div class="batch-state-guide">
        <article v-for="state in stateGuide" :key="state.status" class="batch-state-step">
          <strong>{{ state.label }}</strong>
          <span>{{ state.description }}</span>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="section-head"><div><span class="eyebrow">NEW BATCH</span><h3>创建选寝批次</h3></div></div>
      <form class="form-grid three-column" @submit.prevent="createBatch">
        <label><span>批次编号</span><input v-model.trim="form.batchCode" class="input" required maxlength="32" /></label>
        <label><span>批次名称</span><input v-model.trim="form.batchName" class="input" required maxlength="128" /></label>
        <label><span>床位保留时间（秒）</span><input v-model.number="form.holdDurationSeconds" class="input" type="number" min="30" max="3600" /></label>
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
            <div><span class="status-chip compact">{{ statusText(batch.batch_status) }}</span><h3>{{ batch.batch_name }}</h3><p>{{ batch.batch_code }}</p></div>
            <strong>{{ batch.assigned_count }}/{{ batch.eligible_count }}</strong>
          </div>
          <div class="button-row wrap">
            <button v-if="batch.batch_status === 'DRAFT'" class="button secondary small" @click="prepare(batch.id)">准备学生与宿舍范围</button>
            <button v-for="target in nextActions(batch.batch_status)" :key="target" class="button ghost small" @click="changeStatus(batch.id, target)">{{ actionText(target) }}</button>
            <button v-if="['CLOSED', 'ALLOCATING'].includes(String(batch.batch_status))" class="button accent small" @click="previewAllocation(batch.id)">预演统一分配</button>
            <button v-if="batch.batch_status !== 'CANCELLED'" class="button secondary small" @click="openCopy(batch)">复制配置</button>
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
