<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const profile = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, batchResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/student/profile'),
      api.get<ListSuccessResponse>('/api/v1/student/batches'),
    ])
    profile.value = (profileResponse.data.data ?? {}) as DataObject
    batches.value = (batchResponse.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '数据加载失败'
  } finally {
    loading.value = false
  }
}

function batchId(batch: DataObject) {
  return Number(batch.id)
}

function statusText(status: unknown) {
  const texts: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    OPEN: '选寝进行中',
    PAUSED: '暂时停止',
    CLOSED: '已关闭',
    ALLOCATING: '统一分配中',
    FINISHED: '已完成',
    CANCELLED: '已取消',
  }
  return texts[String(status)] ?? String(status ?? '')
}
</script>

<template>
  <div class="page-grid">
    <section class="welcome-card panel gradient-panel">
      <div>
        <span class="eyebrow light">欢迎回来</span>
        <h2>{{ profile.student_name || '同学' }}</h2>
        <p>{{ profile.student_number }} · {{ profile.major_code }} {{ profile.major_name }}</p>
      </div>
      <div class="welcome-mark">{{ profile.gender === 'M' ? '男寝' : '女寝' }}</div>
    </section>

    <section class="panel span-2">
      <div class="section-head">
        <div>
          <span class="eyebrow">SELECTION BATCHES</span>
          <h2>可参与的选寝批次</h2>
        </div>
        <button class="button ghost" @click="load">刷新</button>
      </div>

      <p v-if="loading" class="empty-state">正在读取选寝批次…</p>
      <p v-else-if="error" class="alert error">{{ error }}</p>
      <p v-else-if="batches.length === 0" class="empty-state">当前没有可参与的选寝批次。</p>

      <div v-else class="batch-list">
        <article v-for="batch in batches" :key="String(batch.id)" class="batch-card">
          <div class="batch-title">
            <div>
              <span class="status-chip compact">{{ statusText(batch.batch_status) }}</span>
              <h3>{{ batch.batch_name }}</h3>
              <p>{{ batch.batch_code }}</p>
            </div>
            <span class="big-number">{{ batch.assigned ? '✓' : batchId(batch) }}</span>
          </div>
          <dl class="meta-grid">
            <div><dt>临时占用</dt><dd>{{ batch.hold_duration_seconds }} 秒</dd></div>
            <div><dt>组队选寝</dt><dd>{{ batch.allow_team ? '支持' : '不支持' }}</dd></div>
            <div><dt>学生随机</dt><dd>{{ batch.allow_student_random ? '支持' : '不支持' }}</dd></div>
          </dl>
          <div class="button-row">
            <RouterLink
              v-if="!batch.assigned"
              class="button secondary"
              :to="`/student/batches/${batchId(batch)}/questionnaire`"
            >填写问卷</RouterLink>
            <RouterLink
              v-if="['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(batch.batch_status)) && !batch.assigned"
              class="button primary"
              :to="`/student/batches/${batchId(batch)}/rooms`"
            >进入选寝</RouterLink>
            <RouterLink
              class="button ghost"
              :to="`/student/batches/${batchId(batch)}/assignment`"
            >查看结果</RouterLink>
          </div>
        </article>
      </div>
    </section>

    <section class="panel info-card">
      <span class="eyebrow">规则说明</span>
      <h3>最终床位以服务端确认为准</h3>
      <p>临时占用只在倒计时内保留床位。确认成功后，MySQL唯一约束会阻止任何重复分配。</p>
    </section>
  </div>
</template>
