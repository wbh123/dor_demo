<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ActionConfirmDialog from '../../components/common/ActionConfirmDialog.vue'
import TransientNotice from '../../components/common/TransientNotice.vue'
import DormitoryBedSelector from '../../components/admin/DormitoryBedSelector.vue'
import AppModal from '../../components/modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'
import AdminBedConfirmationView from './AdminBedConfirmationView.vue'

const residencyTab = ref<'RESIDENCY' | 'DECLARATION'>('RESIDENCY')
const items = ref<DataObject[]>([])
const rooms = ref<DataObject[]>([])
const keyword = ref('')
const mappingStatus = ref('ALL')
const roomId = ref<number | undefined>()
const selected = ref<DataObject | null>(null)
const selectableBeds = ref<DataObject[]>([])
const selectedBedId = ref(0)
const reason = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const ending = ref<DataObject | null>(null)
const endReason = ref('')
const endingResidency = ref(false)
const swapConfirmOpen = ref(false)

const filteredRooms = computed(() => rooms.value.filter((room) => Number(room.active_residents ?? 0) > 0))
const selectedBed = computed(() => selectableBeds.value.find(bed => Number(bed.bed_id ?? bed.id) === selectedBedId.value) ?? null)
const selectedRequiresSwap = computed(() => Number(selectedBed.value?.swap_required ?? 0) === 1)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/residencies', {
      params: { keyword: keyword.value || undefined, roomId: roomId.value, bedMappingStatus: mappingStatus.value },
    })
    const data = (response.data.data ?? {}) as DataObject
    items.value = (data.items ?? []) as DataObject[]
    rooms.value = (data.rooms ?? []) as DataObject[]
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '在住信息加载失败'
  } finally { loading.value = false }
}

async function openBedDialog(item: DataObject) {
  selected.value = item
  selectedBedId.value = 0
  reason.value = ''
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/students/${item.student_id}/residency-adjustment-context`)
    const data = (response.data.data ?? {}) as DataObject
    selectableBeds.value = (data.beds ?? data.availableBeds ?? []) as DataObject[]
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '学生住宿调整信息加载失败'
  }
}
function closeDialog() {
  if (saving.value) return
  selected.value = null
  selectableBeds.value = []
  selectedBedId.value = 0
  reason.value = ''
  swapConfirmOpen.value = false
}
function requestSaveBed() {
  if (!selected.value || !selectedBedId.value || reason.value.trim().length < 2) return
  if (selectedRequiresSwap.value) swapConfirmOpen.value = true
  else void performSaveBed()
}
async function performSaveBed() {
  if (!selected.value || !selectedBedId.value || reason.value.trim().length < 2) return
  swapConfirmOpen.value = false
  saving.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/students/${selected.value.student_id}/residency-adjustment`, {
      bedId: selectedBedId.value,
      reason: reason.value.trim(),
    })
    const data = (response.data.data ?? {}) as DataObject
    message.value = String(data.message ?? (data.swapped
      ? '两名学生的床位已交换，来源均记录为管理员修改。'
      : `${selected.value.student_name}的寝室和床位已修改。`))
    closeDialogAfterSave()
    await load()
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '床位调整失败'
  } finally { saving.value = false }
}
function requestEndResidency(item: DataObject) { ending.value = item; endReason.value = ''; error.value = '' }
function closeEndDialog() { if (!endingResidency.value) { ending.value = null; endReason.value = '' } }
async function endResidency() {
  const item = ending.value
  if (!item || !endReason.value.trim() || endingResidency.value) return
  endingResidency.value = true
  try {
    await api.post(`/api/v1/admin/residencies/${item.residency_id}/end`, { reason: endReason.value.trim() })
    message.value = `${item.student_name}退宿办理成功，寝室容量已释放。`
    ending.value = null; endReason.value = ''; await load()
  } catch (reasonValue) { error.value = reasonValue instanceof Error ? reasonValue.message : '退宿处理失败' }
  finally { endingResidency.value = false }
}
function closeDialogAfterSave() { selected.value = null; selectableBeds.value = []; selectedBedId.value = 0; reason.value = ''; swapConfirmOpen.value = false }
function scopeText(value: unknown) { return ({ DOMESTIC_ONLY:'国内生宿舍', INTERNATIONAL_ONLY:'国际生宿舍', MIXED:'混住宿舍' } as Record<string,string>)[String(value)] ?? value }
function methodText(value: unknown) { return ({ ROOM_SELECT:'个人选寝室',TEAM_ROOM_SELECT:'队伍选寝室',BED_SELECT:'个人选床位',TEAM_BED_SELECT:'队伍选床位',DIRECT_ROOM:'管理员直接分寝',DIRECT_BED:'管理员直接分床',MANUAL_ADJUSTMENT:'管理员修改',IMPORT_MIGRATION:'历史迁移' } as Record<string,string>)[String(value)] ?? value }
</script>

<template>
  <div class="content-column">
    <TransientNotice :message="message" type="success" @close="message = ''" />
    <div class="page-title"><span class="eyebrow">RESIDENCY AND BED REVIEW</span><h2>在住与床位核查</h2><p>统一维护正式在住关系、管理员床位确认和学生实际床位申报。床位已有学生时可在确认后交换双方床位。</p></div>
    <div class="residency-tabs"><button class="button" :class="residencyTab === 'RESIDENCY' ? 'primary' : 'ghost'" @click="residencyTab = 'RESIDENCY'">在住名单与管理员确认</button><button class="button" :class="residencyTab === 'DECLARATION' ? 'primary' : 'ghost'" @click="residencyTab = 'DECLARATION'">学生申报核查</button></div>
    <template v-if="residencyTab === 'RESIDENCY'">
      <p v-if="error" class="alert error">{{ error }}</p>
      <div class="residency-stats"><article class="panel"><span>有效在住</span><strong>{{ items.length }}</strong></article><article class="panel"><span>待确认床位</span><strong>{{ items.filter((item) => !item.bed_id).length }}</strong></article><article class="panel"><span>涉及寝室</span><strong>{{ filteredRooms.length }}</strong></article></div>
      <section class="panel">
        <form class="residency-filter" @submit.prevent="load"><input v-model.trim="keyword" class="input" placeholder="搜索学号、姓名、楼栋或房间" /><select v-model="roomId" class="input"><option :value="undefined">全部在住寝室</option><option v-for="room in filteredRooms" :key="String(room.room_id)" :value="Number(room.room_id)">{{ room.building_name }} {{ room.room_number }} · 在住{{ room.active_residents }}人</option></select><select v-model="mappingStatus" class="input"><option value="ALL">全部床位状态</option><option value="UNCONFIRMED">仅待确认</option><option value="CONFIRMED">仅已确认</option></select><button class="button secondary">查询</button></form>
        <p v-if="loading" class="empty-state">正在加载在住信息…</p>
        <div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>类别</th><th>寝室</th><th>宿舍属性</th><th>实际床位</th><th>来源</th><th>入住时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="String(item.residency_id)"><td><strong>{{ item.student_name }}</strong><small>{{ item.student_number }}</small></td><td>{{ item.student_category === 'INTERNATIONAL' ? '国际生' : '国内生' }}</td><td>{{ item.building_name }} {{ item.room_number }}<small>{{ item.floor_number }}层</small></td><td>{{ scopeText(item.resident_scope) }}</td><td><span class="status-chip compact" :class="{ warning: !item.bed_id }">{{ item.bed_id ? `${item.bed_code} · ${bedTypeLabel(item.bed_type)}` : '待确认' }}</span></td><td>{{ methodText(item.assignment_method) }}</td><td>{{ new Date(String(item.assigned_at)).toLocaleString() }}</td><td><div class="button-row compact-actions"><button class="button primary small" type="button" @click="openBedDialog(item)">{{ item.bed_id ? '调整床位' : '确认床位' }}</button><button class="button danger small" type="button" @click="requestEndResidency(item)">办理退宿</button></div></td></tr></tbody></table></div>
      </section>
      <AppModal :open="Boolean(ending)" size="default" :busy="endingResidency" busy-text="正在办理退宿并释放寝室容量…" @close="closeEndDialog"><div v-if="ending" class="residency-end-dialog"><span class="eyebrow">END RESIDENCY</span><h3>办理 {{ ending.student_name }} 退宿</h3><p>退宿会结束当前在住记录并释放寝室容量，请填写可审计的处理原因。</p><label class="form-stack"><span>退宿原因</span><textarea v-model.trim="endReason" class="input" rows="4" maxlength="500"></textarea></label><div class="button-row dialog-actions"><button class="button ghost" type="button" :disabled="endingResidency" @click="closeEndDialog">取消</button><button class="button danger" type="button" :disabled="!endReason.trim() || endingResidency" @click="endResidency">{{ endingResidency ? '正在办理退宿…' : '确认结束在住' }}</button></div></div></AppModal>
    </template>
    <AdminBedConfirmationView v-else embedded />

    <AppModal :open="Boolean(selected)" size="wide" :busy="saving" title="确认或调整学生床位" busy-text="正在保存床位修改…" @close="closeDialog"><div v-if="selected" class="bed-confirm-dialog"><div class="student-current-placement"><strong>{{ selected.student_name }}</strong><span>{{ selected.student_number }} · 当前 {{ selected.building_name }} {{ selected.room_number }} {{ selected.bed_code || '床位待确认' }}</span></div><DormitoryBedSelector v-model="selectedBedId" :beds="selectableBeds" :disabled="saving" allow-occupied /><label class="form-stack"><span>确认或调整原因</span><textarea v-model.trim="reason" class="input" required minlength="2" maxlength="500" rows="3" placeholder="例如：线下核对实际床位，或双方已确认交换"></textarea></label></div><template #footer><button class="button ghost" type="button" :disabled="saving" @click="closeDialog">取消</button><button class="button primary" type="button" :disabled="!selectedBedId || reason.trim().length < 2 || saving" @click="requestSaveBed">{{ selectedRequiresSwap ? '确认交换床位' : '确认修改床位' }}</button></template></AppModal>

    <ActionConfirmDialog :open="swapConfirmOpen" title="确认交换两名学生的床位" :message="`该床位当前由${String(selectedBed?.occupant_student_name ?? '另一名学生')}使用。是否交换两名学生的床位？`" detail="系统会在事务中再次核对双方床位，并同步正式在住、有效分配、历史记录和审计记录。" confirm-text="确认交换" danger :busy="saving" @cancel="swapConfirmOpen=false" @confirm="performSaveBed" />
  </div>
</template>

<style scoped>
.residency-tabs{display:flex;gap:10px;flex-wrap:wrap}.residency-end-dialog{width:100%;padding:0}.residency-end-dialog p{color:var(--text-muted);line-height:1.7}.residency-end-dialog .dialog-actions{justify-content:flex-end;margin-top:16px}.residency-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.residency-stats article{padding:18px}.residency-stats span{color:var(--text-muted)}.residency-stats strong{display:block;margin-top:6px;font-size:28px}.residency-filter{display:grid;grid-template-columns:minmax(220px,1fr) 220px 160px auto;gap:10px;margin-bottom:16px}.warning{background:#fff7ed;color:#c2410c}.bed-confirm-dialog{display:grid;gap:16px;width:100%}.student-current-placement{display:grid;gap:4px;padding:12px;border-radius:12px;background:var(--surface-soft)}.student-current-placement span{color:var(--text-muted)}.dialog-actions{justify-content:flex-end;margin-top:16px}@media(max-width:800px){.residency-stats,.residency-filter{grid-template-columns:1fr}}
</style>
