<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

const route = useRoute()
const batchId = Number(route.params.batchId)
const result = ref<DataObject>({ assigned: false })
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/student/batches/${batchId}/assignment`,
    )
    result.value = (response.data.data ?? { assigned: false }) as DataObject
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '结果加载失败'
  } finally {
    loading.value = false
  }
}

function methodText(value: unknown) {
  return {
    SELF_SELECT: '个人自主选择',
    TEAM_SELECT: '队伍整体选择',
    STUDENT_RANDOM: '学生随机选择',
    ADMIN_RANDOM: '管理员统一分配',
    MANUAL_ADJUSTMENT: '管理员人工调整',
  }[String(value)] ?? String(value)
}
</script>

<template>
  <div class="content-column narrow">
    <div class="page-title">
      <span class="eyebrow">ASSIGNMENT RESULT</span>
      <h2>我的住宿分配结果</h2>
      <p>页面展示的是数据库中的当前有效分配结果。</p>
    </div>

    <p v-if="loading" class="panel empty-state">正在读取最终分配…</p>
    <p v-else-if="error" class="alert error">{{ error }}</p>

    <section v-else-if="result.assigned" class="panel assignment-result">
      <div class="result-check">✓</div>
      <span class="eyebrow">ASSIGNED</span>
      <h2>{{ (result.assignment as DataObject).building_name }}</h2>
      <div class="assignment-address">
        <strong>{{ (result.assignment as DataObject).room_number }} 室</strong>
        <span>{{ (result.assignment as DataObject).bed_code }} 床位</span>
      </div>
      <dl class="meta-grid">
        <div>
          <dt>楼层</dt>
          <dd>{{ (result.assignment as DataObject).floor_number }} 层</dd>
        </div>
        <div>
          <dt>床位类型</dt>
          <dd>{{ (result.assignment as DataObject).bed_type }}</dd>
        </div>
        <div>
          <dt>分配方式</dt>
          <dd>{{ methodText((result.assignment as DataObject).assignment_method) }}</dd>
        </div>
      </dl>
      <RouterLink class="button primary" to="/student">返回选寝首页</RouterLink>
    </section>

    <section v-else class="panel empty-state large">
      <div class="empty-icon">○</div>
      <h3>尚未形成最终分配</h3>
      <p>临时占用或推荐结果不会显示在这里。完成个人确认、队伍确认或等待统一分配后再查看。</p>
      <RouterLink class="button primary" to="/student">返回选寝首页</RouterLink>
    </section>
  </div>
</template>
