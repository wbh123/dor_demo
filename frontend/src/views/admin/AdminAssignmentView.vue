<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppModal from '../../components/modal/AppModal.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import { api } from '../../api/client'
import type { AssignmentAdjustmentRequest, DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

const batches = ref<DataObject[]>([])
const assignments = ref<DataObject[]>([])
const selectedBatchId = ref<number | null>(null)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const selectedAssignment = ref<DataObject | null>(null)
const error = ref('')
const message = ref('')
const loading = ref(false)
const adjusting = ref(false)
const adjustment = reactive<AssignmentAdjustmentRequest>({ newBedId: 0, reason: '' })

const targetBeds = computed(() => ((selectedAssignment.value?.availableBeds ?? []) as DataObject[]))
const pagedAssignments = computed(() => assignments.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(async () => {
  await loadBatches()
  if (batches.value.length) {
    selectedBatchId.value = Number(batches.value[0].id)
    await loadAssignments()
  }
})

async function loadBatches() {
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/batches')
    batches.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次加载失败'
  }
}

async function loadAssignments(resetPage = true) {
  if (!selectedBatchId.value) return
  loading.value = true
  error.value = ''
  if (resetPage) page.value = 1
  try {
    const response = await api.get<ListSuccessResponse>(
      `/api/v1/admin/batches/${selectedBatchId.value}/assignments`,
      { params: { keyword: keyword.value || undefined } },
    )
    assignments.value = (response.data.data ?? []) as DataObject[]
    selectedAssignment.value = null
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '分配结果加载失败'
  } finally {
    loading.value = false
  }
}

function openAdjustment(item: DataObject) {
  selectedAssignment.value = item
  const available = (item.availableBeds ?? []) as DataObject[]
  adjustment.newBedId = available.length ? Number(available[0].bed_id) : 0
  adjustment.reason = ''
}

function closeAdjustment() {
  if (!adjusting.value) selectedAssignment.value = null
}

async function submitAdjustment() {
  if (!selectedAssignment.value || !adjustment.newBedId || adjusting.value) return
  error.value = ''
  message.value = ''
  adjusting.value = true
  try {
    await api.post<ObjectSuccessResponse>(
      `/api/v1/admin/assignments/${selectedAssignment.value.assignment_id}/adjust`,
      adjustment,
    )
    message.value = '床位调整成功，调整前后结果、原因和操作人已写入历史与审计日志。'
    selectedAssignment.value = null
    await loadAssignments(false)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '床位调整失败'
  } finally {
    adjusting.value = false
  }
}

function methodText(value: unknown) {
  return ({ SELF_SELECT: '个人自选', TEAM_SELECT: '队伍自选', STUDENT_RANDOM: '学生随机', ADMIN_RANDOM: '统一分配', MANUAL_ADJUSTMENT: '管理员修改' } as Record<string, string>)[String(value)] ?? String(value)
}
function batchStatus(value: unknown) {
  return ({ DRAFT: '草稿', PUBLISHED: '已发布', OPEN: '进行中', CLOSED: '已关闭', ALLOCATING: '分配中', FINISHED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[String(value)] ?? String(value ?? '-')
}
</script>

<template>
  <div class="content-column">
    <div class="page-title"><span class="eyebrow">分配管理</span><h2>分配结果与人工调整</h2><p>查询当前有效床位归属。人工调整必须选择同批次、同性别且未被占用的目标床位，并填写原因。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel filter-bar">
      <label class="search-field"><span>选寝批次</span><select v-model="selectedBatchId" class="input" @change="loadAssignments()"><option v-for="batch in batches" :key="String(batch.id)" :value="Number(batch.id)">{{ batch.batch_name }} · {{ batchStatus(batch.batch_status) }}</option></select></label>
      <form class="inline-form" @submit.prevent="loadAssignments()"><input v-model.trim="keyword" class="input" placeholder="学号、姓名、楼栋或房间" /><button class="button secondary">查询</button></form>
    </section>

    <section class="panel">
      <div class="section-head compact-section-head"><div><span class="eyebrow">有效分配</span><h3>当前有效分配 · {{ assignments.length }}条</h3></div></div>
      <p v-if="loading" class="empty-state">正在读取分配结果…</p><p v-else-if="assignments.length === 0" class="empty-state">当前批次还没有有效分配结果。</p>
      <div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>专业</th><th>当前宿舍</th><th>床位</th><th>方式</th><th>可调整床位</th><th /></tr></thead><tbody><tr v-for="item in pagedAssignments" :key="String(item.assignment_id)"><td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }} · {{ item.gender === 'M' ? '男' : '女' }}</small></td><td>{{ item.major_code }} · {{ item.major_name }}</td><td>{{ item.building_name }} {{ item.room_number }}</td><td>{{ item.bed_code }} · {{ bedTypeLabel(item.bed_type) }}</td><td><span class="status-chip compact">{{ methodText(item.assignment_method) }}</span></td><td>{{ ((item.availableBeds ?? []) as DataObject[]).length }}个</td><td><button class="button ghost small" type="button" :disabled="!((item.availableBeds ?? []) as DataObject[]).length" @click="openAdjustment(item)">调整</button></td></tr></tbody></table></div>
      <PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="assignments.length" />
    </section>

    <AppModal :open="Boolean(selectedAssignment)" :busy="adjusting" title="人工调整床位" description="选择目标床位并填写调整原因。" size="wide" busy-text="正在调整床位并写入审计记录…" @close="closeAdjustment">
      <div v-if="selectedAssignment" class="adjustment-dialog">
        <p class="current-assignment">当前：{{ selectedAssignment.building_name }} {{ selectedAssignment.room_number }}-{{ selectedAssignment.bed_code }}</p>
        <form class="form-grid two-column" @submit.prevent="submitAdjustment"><label><span>目标床位</span><select v-model.number="adjustment.newBedId" class="input" required><option v-for="bed in targetBeds" :key="String(bed.bed_id)" :value="Number(bed.bed_id)">{{ bed.display_name }} · {{ bedTypeLabel(bed.bed_type) }}</option></select></label><label><span>调整原因</span><input v-model.trim="adjustment.reason" class="input" required minlength="2" maxlength="500" placeholder="例如：原床位进入维护状态" /></label></form>
      </div>
      <template #footer><button type="button" class="button ghost" :disabled="adjusting" @click="closeAdjustment">取消</button><button class="button primary" type="button" :disabled="adjusting || !adjustment.newBedId || adjustment.reason.trim().length < 2" @click="submitAdjustment">{{ adjusting ? '正在调整…' : '确认调整' }}</button></template>
    </AppModal>
  </div>
</template>

<style scoped>
.compact-section-head{margin-bottom:12px}.adjustment-dialog{display:grid;gap:16px;width:100%}.current-assignment{margin:0;padding:12px;border-radius:12px;background:var(--surface-soft)}
</style>
