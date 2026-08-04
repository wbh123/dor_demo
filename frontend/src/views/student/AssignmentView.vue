<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { bedTypeLabel } from '../../utils/bedLabels'

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.batchId)
const result = ref<DataObject>({ assigned: false })
const confirmation = ref<DataObject>({ resident: false, eligible: false, beds: [] })
const selectedBedId = ref(0)
const confirmationReason = ref('本人当前实际使用该床位')
const loading = ref(true)
const error = ref('')
const message = ref('')
const cancelling = ref(false)
const confirmationSaving = ref(false)
const showCancelConfirm = ref(false)
const canReselect = ref(false)
const assignment = computed(() => (result.value.assignment ?? {}) as DataObject)
const bedConfirmed = computed(() => Boolean(assignment.value.bed_id ?? assignment.value.bed_confirmed))
const confirmationBeds = computed(() => (confirmation.value.beds ?? []) as DataObject[])
const pendingRequest = computed(() => (confirmation.value.request ?? {}) as DataObject)

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    const [assignmentResponse, readinessResponse, confirmationResponse] = await Promise.all([
      api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/assignment`),
      api.get<ObjectSuccessResponse>(`/api/v1/student/batches/${batchId}/selection-readiness`),
      api.get<ObjectSuccessResponse>('/api/v1/student/bed-confirmations'),
    ])
    result.value = (assignmentResponse.data.data ?? { assigned: false }) as DataObject
    canReselect.value = Boolean((readinessResponse.data.data as DataObject | undefined)?.allowStudentReselect)
    confirmation.value = (confirmationResponse.data.data ?? {}) as DataObject
    if (pendingRequest.value.declared_bed_id) selectedBedId.value = Number(pendingRequest.value.declared_bed_id)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '结果加载失败' }
  finally { loading.value = false }
}

async function submitBedConfirmation() {
  if (!selectedBedId.value || !confirmationReason.value.trim() || confirmationSaving.value) return
  confirmationSaving.value = true; error.value=''; message.value=''
  try {
    await api.post('/api/v1/student/bed-confirmations', { bedId:selectedBedId.value, reason:confirmationReason.value.trim() })
    message.value = '实际床位申报已提交，等待管理员按寝室核查。'
    await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '提交实际床位核查失败' }
  finally { confirmationSaving.value = false }
}

async function cancelBedConfirmation() {
  if (!pendingRequest.value.id || confirmationSaving.value) return
  const reason = window.prompt('请填写取消申报的原因', '申报床位有误，需要重新选择')
  if (!reason?.trim()) return
  confirmationSaving.value = true
  try { await api.post(`/api/v1/student/bed-confirmations/${pendingRequest.value.id}/cancel`, { reason:reason.trim() }); message.value='已取消实际床位申报。'; await load() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '取消申报失败' }
  finally { confirmationSaving.value=false }
}

function requestCancelAssignment(){if(!cancelling.value)showCancelConfirm.value=true}
function closeCancelConfirm(){if(!cancelling.value)showCancelConfirm.value=false}
async function cancelAssignment(){if(cancelling.value)return;cancelling.value=true;error.value='';try{await api.post(`/api/v1/student/batches/${batchId}/assignment/cancel`);showCancelConfirm.value=false;await router.replace(`/student/batches/${batchId}/rooms`)}catch(reason){error.value=reason instanceof Error?reason.message:'取消当前分配失败'}finally{cancelling.value=false}}
function methodText(value:unknown){return({SELF_SELECT:'个人自主选择',TEAM_SELECT:'队伍整体选择',ROOM_SELECT:'个人选择寝室',TEAM_ROOM_SELECT:'队伍整体选择寝室',BED_SELECT:'个人选择床位',TEAM_BED_SELECT:'队伍整体选择床位',STUDENT_RANDOM:'学生随机选择',ADMIN_RANDOM:'管理员统一分配',MANUAL_ADJUSTMENT:'管理员人工调整'} as Record<string,string>)[String(value)]??String(value)}
</script>

<template>
  <div class="content-column narrow">
    <div class="page-title"><span class="eyebrow">ASSIGNMENT RESULT</span><h2>我的住宿分配结果</h2><p>学生申报的实际床位须由管理员按寝室核查后，才会写入正式住宿记录。</p></div>
    <p v-if="loading" class="panel empty-state">正在读取最终分配…</p><p v-else-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>

    <template v-if="!loading && result.assigned">
      <section class="panel assignment-result"><div class="result-check">✓</div><span class="eyebrow">ASSIGNED</span><h2>{{ assignment.building_name }}</h2><div class="assignment-address"><strong>{{ assignment.room_number }} 室</strong><span>{{ bedConfirmed ? `${assignment.bed_code} 床位` : '具体床位待寝室成员协商（未固定床位）' }}</span></div><dl class="meta-grid"><div><dt>楼层</dt><dd>{{ assignment.floor_number }} 层</dd></div><div><dt>床位类型</dt><dd>{{ bedConfirmed ? bedTypeLabel(assignment.bed_type) : '未固定床位' }}</dd></div><div><dt>分配方式</dt><dd>{{ methodText(assignment.assignment_method) }}</dd></div></dl><div class="button-row"><button v-if="canReselect" class="button secondary" :disabled="cancelling" @click="requestCancelAssignment">{{ cancelling?'正在取消…':'取消并重新选择' }}</button><RouterLink class="button primary" to="/student">返回选寝首页</RouterLink></div></section>

      <section v-if="confirmation.eligible" class="panel bed-confirmation-card">
        <div class="section-head"><div><span class="eyebrow">实际床位核查</span><h3>确认本人当前使用的床位</h3><p>选择寝室内的实际床位并提交。管理员核查前不会修改正式床位。</p></div></div>
        <article v-if="pendingRequest.id" class="pending-confirmation"><div><strong>待核查：{{ pendingRequest.bed_code }}</strong><p>提交时间：{{ pendingRequest.submitted_at }}</p><small>{{ pendingRequest.reason }}</small></div><button class="button ghost" :disabled="confirmationSaving" @click="cancelBedConfirmation">取消申报</button></article>
        <form v-else class="confirmation-form" @submit.prevent="submitBedConfirmation">
          <div class="bed-option-grid"><label v-for="bed in confirmationBeds" :key="String(bed.bed_id)" :class="{selected:selectedBedId===Number(bed.bed_id),occupied:Number(bed.occupied)===1}"><input v-model="selectedBedId" type="radio" :value="Number(bed.bed_id)"><strong>{{ bed.bed_code }}</strong><span>{{ bedTypeLabel(bed.bed_type) }}</span><small>{{ Number(bed.occupied)===1?'系统显示已占用':'可申报，等待核查' }}</small></label></div>
          <label class="form-stack"><span>申报说明</span><textarea v-model="confirmationReason" class="input" maxlength="500" rows="3" required /></label>
          <button class="button primary" :disabled="!selectedBedId||confirmationSaving">{{ confirmationSaving?'正在提交…':'提交实际床位核查' }}</button>
        </form>
      </section>
    </template>

    <section v-else-if="!loading" class="panel empty-state large"><div class="empty-icon">○</div><h3>尚未形成最终分配</h3><p>完成个人确认、队伍确认或等待统一分配后再查看。</p><RouterLink class="button primary" to="/student">返回选寝首页</RouterLink></section>

    <div v-if="showCancelConfirm" class="modal-overlay" @click.self="closeCancelConfirm"><section class="modal-card confirmation-dialog assignment-cancel-dialog"><h3>确认取消当前住宿结果？</h3><p>该操作会立即释放当前寝室或床位。</p><div class="button-row dialog-actions"><button class="button ghost" @click="closeCancelConfirm">暂不取消</button><button class="button danger" :disabled="cancelling" @click="cancelAssignment">确认取消并重选</button></div></section></div>
  </div>
</template>

<style scoped>.assignment-cancel-dialog{width:min(520px,calc(100vw - 32px));padding:24px}.dialog-actions{justify-content:flex-end}.bed-confirmation-card{display:grid;gap:16px}.pending-confirmation{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px;border:1px solid #d7e4f8;border-radius:16px;background:#f3f8ff}.pending-confirmation p{margin:5px 0;color:var(--muted)}.confirmation-form{display:grid;gap:16px}.bed-option-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}.bed-option-grid label{display:grid;gap:5px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft);cursor:pointer}.bed-option-grid label.selected{border-color:#5684c9;background:#eef5ff}.bed-option-grid label.occupied{opacity:.72}.bed-option-grid input{position:absolute;opacity:0}.bed-option-grid span,.bed-option-grid small{color:var(--muted)}@media(max-width:640px){.pending-confirmation{align-items:flex-start;flex-direction:column}}
</style>
