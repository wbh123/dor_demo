<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import DormitoryBedSelector from '../../components/admin/DormitoryBedSelector.vue'
import AppModal from '../../components/modal/AppModal.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

const batches = ref<DataObject[]>([])
const assignments = ref<DataObject[]>([])
const selectedBatchId = ref<number | null>(null)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const selectedAssignment = ref<DataObject | null>(null)
const targetBeds = ref<DataObject[]>([])
const selectedBedId = ref(0)
const adjustmentReason = ref('')
const error = ref('')
const message = ref('')
const loading = ref(false)
const adjusting = ref(false)
const swapConfirmOpen = ref(false)

const pagedAssignments = computed(() => assignments.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const selectedBed = computed(() => targetBeds.value.find(bed => Number(bed.bed_id ?? bed.id) === selectedBedId.value) ?? null)
const requiresSwap = computed(() => Number(selectedBed.value?.swap_required ?? 0) === 1)

onMounted(async () => {
  await loadBatches()
  if (batches.value.length) { selectedBatchId.value = Number(batches.value[0].id); await loadAssignments() }
})
async function loadBatches() {
  try { const response = await api.get<ListSuccessResponse>('/api/v1/admin/batches'); batches.value = (response.data.data ?? []) as DataObject[] }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '批次加载失败' }
}
async function loadAssignments(resetPage = true) {
  if (!selectedBatchId.value) return
  loading.value = true; error.value = ''; if (resetPage) page.value = 1
  try {
    const response = await api.get<ListSuccessResponse>(`/api/v1/admin/batches/${selectedBatchId.value}/assignments`, { params: { keyword: keyword.value || undefined } })
    assignments.value = (response.data.data ?? []) as DataObject[]; selectedAssignment.value = null
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '分配结果加载失败' }
  finally { loading.value = false }
}
async function openAdjustment(item: DataObject) {
  selectedAssignment.value = item; selectedBedId.value = 0; adjustmentReason.value = ''; error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/students/${item.student_id}/residency-adjustment-context`)
    const data = (response.data.data ?? {}) as DataObject
    targetBeds.value = (data.beds ?? data.availableBeds ?? []) as DataObject[]
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '可调整床位加载失败' }
}
function closeAdjustment() { if (!adjusting.value) { selectedAssignment.value = null; targetBeds.value = []; selectedBedId.value = 0; adjustmentReason.value = ''; swapConfirmOpen.value = false } }
function requestSubmit() {
  if (!selectedAssignment.value || !selectedBedId.value || adjustmentReason.value.trim().length < 2) return
  if (requiresSwap.value) swapConfirmOpen.value = true
  else void submitAdjustment()
}
async function submitAdjustment() {
  if (!selectedAssignment.value || !selectedBedId.value || adjusting.value) return
  swapConfirmOpen.value = false; error.value = ''; message.value = ''; adjusting.value = true
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/students/${selectedAssignment.value.student_id}/residency-adjustment`, { bedId: selectedBedId.value, reason: adjustmentReason.value.trim() })
    const data = (response.data.data ?? {}) as DataObject
    message.value = String(data.message ?? (data.swapped ? '两名学生床位交换成功。' : '床位调整成功，原因和操作人已写入历史与审计日志。'))
    selectedAssignment.value = null; targetBeds.value = []; await loadAssignments(false)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '床位调整失败' }
  finally { adjusting.value = false }
}
function methodText(value: unknown) { return ({ SELF_SELECT:'个人自选',TEAM_SELECT:'队伍自选',STUDENT_RANDOM:'学生随机',ADMIN_RANDOM:'统一分配',MANUAL_ADJUSTMENT:'管理员修改' } as Record<string,string>)[String(value)] ?? String(value) }
function batchStatus(value: unknown) { return ({ DRAFT:'草稿',PUBLISHED:'已发布',OPEN:'进行中',CLOSED:'已关闭',ALLOCATING:'分配中',FINISHED:'已完成',CANCELLED:'已取消' } as Record<string,string>)[String(value)] ?? String(value ?? '-') }
</script>

<template>
  <div class="content-column">
    <div class="page-title"><span class="eyebrow">分配管理</span><h2>分配结果与人工调整</h2><p>使用统一宿舍床位选择器查看完整占用信息。选择已有学生的床位时，可在确认后交换双方床位。</p></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <section class="panel filter-bar"><label class="search-field"><span>选寝批次</span><select v-model="selectedBatchId" class="input" @change="loadAssignments()"><option v-for="batch in batches" :key="String(batch.id)" :value="Number(batch.id)">{{ batch.batch_name }} · {{ batchStatus(batch.batch_status) }}</option></select></label><form class="inline-form" @submit.prevent="loadAssignments()"><input v-model.trim="keyword" class="input" placeholder="学号、姓名、楼栋或房间" /><button class="button secondary">查询</button></form></section>
    <section class="panel"><div class="section-head compact-section-head"><div><span class="eyebrow">有效分配</span><h3>当前有效分配 · {{ assignments.length }}条</h3></div></div><p v-if="loading" class="empty-state">正在读取分配结果…</p><p v-else-if="assignments.length===0" class="empty-state">当前批次还没有有效分配结果。</p><div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>专业</th><th>当前宿舍</th><th>床位</th><th>方式</th><th>操作</th></tr></thead><tbody><tr v-for="item in pagedAssignments" :key="String(item.assignment_id)"><td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }} · {{ item.gender==='M'?'男':'女' }}</small></td><td>{{ item.major_code }} · {{ item.major_name }}</td><td>{{ item.building_name }} {{ item.room_number }}</td><td>{{ item.bed_code }} · {{ bedTypeLabel(item.bed_type) }}</td><td><span class="status-chip compact">{{ methodText(item.assignment_method) }}</span></td><td><button class="button ghost small" type="button" @click="openAdjustment(item)">调整</button></td></tr></tbody></table></div><PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="assignments.length" /></section>

    <AppModal :open="Boolean(selectedAssignment)" :busy="adjusting" title="人工调整床位" description="选择目标宿舍和床位，并填写调整原因。" size="wide" busy-text="正在调整床位并写入审计记录…" @close="closeAdjustment"><div v-if="selectedAssignment" class="adjustment-dialog"><p class="current-assignment">当前：{{ selectedAssignment.building_name }} {{ selectedAssignment.room_number }}-{{ selectedAssignment.bed_code }}</p><DormitoryBedSelector v-model="selectedBedId" :beds="targetBeds" :disabled="adjusting" allow-occupied /><label><span>调整原因</span><input v-model.trim="adjustmentReason" class="input" required minlength="2" maxlength="500" placeholder="例如：双方已确认交换，或原床位进入维护状态" /></label></div><template #footer><button type="button" class="button ghost" :disabled="adjusting" @click="closeAdjustment">取消</button><button class="button primary" type="button" :disabled="adjusting || !selectedBedId || adjustmentReason.trim().length < 2" @click="requestSubmit">{{ requiresSwap ? '确认交换床位' : '确认调整' }}</button></template></AppModal>
    <ActionConfirmDialog :open="swapConfirmOpen" title="确认交换两名学生的床位" :message="`目标床位当前由${String(selectedBed?.occupant_student_name ?? '另一名学生')}使用，是否交换双方床位？`" detail="提交时将重新锁定双方在住和分配记录，状态发生变化时操作会自动终止。" confirm-text="确认交换" danger :busy="adjusting" @cancel="swapConfirmOpen=false" @confirm="submitAdjustment" />
  </div>
</template>

<style scoped>
.compact-section-head{margin-bottom:12px}.adjustment-dialog{display:grid;gap:16px;width:100%}.adjustment-dialog label{display:grid;gap:6px}.current-assignment{margin:0;padding:12px;border-radius:12px;background:var(--surface-soft)}
</style>
