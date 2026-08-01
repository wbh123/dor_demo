<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const dashboard = ref<DataObject>({})
const batches = ref<DataObject[]>([])
const auditLogs = ref<DataObject[]>([])
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [dashboardResponse, batchesResponse, auditResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>('/api/v1/admin/dashboard'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
      api.get<ListSuccessResponse>('/api/v1/admin/audit-logs?limit=8'),
    ])
    dashboard.value = (dashboardResponse.data.data ?? {}) as DataObject
    batches.value = (batchesResponse.data.data ?? []) as DataObject[]
    auditLogs.value = (auditResponse.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '管理数据加载失败'
  } finally {
    loading.value = false
  }
}

const stats = [
  ['studentCount', '学生总数', '人'],
  ['roomCount', '宿舍房间', '间'],
  ['bedCount', '启用床位', '个'],
  ['activeAssignmentCount', '已完成分配', '人'],
]
</script>

<template>
  <div class="content-column">
    <div class="page-title split-title">
      <div>
        <span class="eyebrow">OPERATIONS OVERVIEW</span>
        <h2>第一阶段运行概览</h2>
        <p>学生、宿舍、批次和最终分配的当前数据库统计。</p>
      </div>
      <button class="button ghost" @click="load">刷新数据</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="loading" class="panel empty-state">正在汇总管理数据…</p>

    <template v-else>
      <div class="stat-grid">
        <article v-for="stat in stats" :key="stat[0]" class="panel stat-card">
          <span>{{ stat[1] }}</span>
          <strong>{{ dashboard[stat[0]] ?? 0 }}</strong>
          <small>{{ stat[2] }}</small>
        </article>
      </div>

      <div class="admin-grid">
        <section class="panel span-2">
          <div class="section-head">
            <div><span class="eyebrow">BATCHES</span><h3>最近选寝批次</h3></div>
            <RouterLink class="button ghost" to="/admin/batches">管理批次</RouterLink>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>批次</th><th>状态</th><th>资格人数</th><th>已分配</th></tr></thead>
              <tbody>
                <tr v-for="batch in batches.slice(0, 6)" :key="String(batch.id)">
                  <td><strong>{{ batch.batch_name }}</strong><small>{{ batch.batch_code }}</small></td>
                  <td><span class="status-chip compact">{{ batch.batch_status }}</span></td>
                  <td>{{ batch.eligible_count }}</td>
                  <td>{{ batch.assigned_count }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <div class="section-head"><div><span class="eyebrow">AUDIT</span><h3>最近操作</h3></div></div>
          <div class="audit-list">
            <article v-for="log in auditLogs" :key="String(log.id)">
              <span class="audit-dot" />
              <div><strong>{{ log.action_type }}</strong><p>{{ log.resource_type }} · {{ log.resource_id || '-' }}</p></div>
            </article>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>
