<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const props = defineProps<{ majors: DataObject[] }>()
const emit = defineEmits<{ completed: [message: string] }>()

const visible = defineModel<boolean>({ required: true })
const rooms = ref<DataObject[]>([])
const batches = ref<DataObject[]>([])
const beds = ref<DataObject[]>([])
const saving = ref(false)
const loadingBeds = ref(false)
const error = ref('')

const form = reactive({
  studentNumber: '',
  studentName: '',
  gender: 'M',
  majorId: 0,
  nationalityCode: 'CN',
  studentCategory: 'DOMESTIC',
  phoneNumber: '',
  action: 'PROFILE_ONLY' as 'PROFILE_ONLY' | 'DIRECT_ASSIGNMENT' | 'ADD_TO_BATCH',
  roomId: 0,
  bedId: 0,
  batchId: 0,
  reason: '',
})

const enabledMajors = computed(() => props.majors.filter((major) => Boolean(major.enabled)))
const filteredRooms = computed(() => rooms.value.filter((room) => {
  if (String(room.gender_restriction) !== form.gender) return false
  const scope = String(room.resident_scope ?? 'MIXED')
  if (form.studentCategory === 'DOMESTIC') return scope !== 'INTERNATIONAL_ONLY'
  return scope !== 'DOMESTIC_ONLY'
}))
const selectedRoom = computed(() => rooms.value.find((room) => Number(room.id) === form.roomId) ?? null)
const selectedBatch = computed(() => batches.value.find((batch) => Number(batch.id) === form.batchId) ?? null)

onMounted(loadOptions)
watch(() => props.majors, () => {
  if (!form.majorId && enabledMajors.value.length) form.majorId = Number(enabledMajors.value[0].id)
}, { immediate: true })
watch(() => form.roomId, () => void loadBeds())
watch([() => form.gender, () => form.studentCategory], () => {
  if (!filteredRooms.value.some((room) => Number(room.id) === form.roomId)) {
    form.roomId = 0
    form.bedId = 0
  }
})

async function loadOptions() {
  try {
    const [roomResponse, batchResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/rooms'),
      api.get<ListSuccessResponse>('/api/v1/admin/batches'),
    ])
    rooms.value = (roomResponse.data.data ?? []) as DataObject[]
    batches.value = ((batchResponse.data.data ?? []) as DataObject[])
      .filter((batch) => ['DRAFT', 'PUBLISHED', 'OPEN', 'PAUSED'].includes(String(batch.batch_status)))
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '可选寝室和批次加载失败'
  }
}

async function loadBeds() {
  beds.value = []
  form.bedId = 0
  if (!form.roomId) return
  loadingBeds.value = true
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/admin/rooms/${form.roomId}/bed-layout`,
    )
    const data = (response.data.data ?? {}) as DataObject
    beds.value = ((data.beds ?? []) as DataObject[])
      .filter((bed) => String(bed.operational_status ?? bed.operationalStatus) === 'ENABLED')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '床位加载失败'
  } finally {
    loadingBeds.value = false
  }
}

function chooseAction(action: typeof form.action) {
  form.action = action
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!form.majorId) {
    error.value = '请选择专业'
    return
  }
  if (form.action === 'DIRECT_ASSIGNMENT' && !form.roomId) {
    error.value = '请选择直接分配的寝室'
    return
  }
  if (form.action === 'ADD_TO_BATCH' && !form.batchId) {
    error.value = '请选择要加入的现有批次'
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      studentNumber: form.studentNumber,
      studentName: form.studentName,
      gender: form.gender,
      majorId: form.majorId,
      nationalityCode: form.nationalityCode.trim().toUpperCase(),
      studentCategory: form.studentCategory,
      phoneNumber: form.phoneNumber.trim() || undefined,
      action: form.action,
      reason: form.reason,
    }
    if (form.action === 'DIRECT_ASSIGNMENT') {
      payload.directAssignment = {
        roomId: form.roomId,
        bedId: form.bedId || undefined,
        reason: form.reason,
      }
    }
    if (form.action === 'ADD_TO_BATCH') {
      payload.batchEnrollment = { batchId: form.batchId }
    }
    await api.post('/api/v1/admin/transfer-students', payload)
    const actionMessage = form.action === 'PROFILE_ONLY'
      ? '资料已录入，学生可等待后续批次。'
      : form.action === 'DIRECT_ASSIGNMENT'
        ? `已直接分配至${selectedRoom.value?.building_name ?? ''}${selectedRoom.value?.room_number ?? ''}。`
        : `已通过容量校验并加入批次“${selectedBatch.value?.batch_name ?? ''}”。`
    emit('completed', `${form.studentName}录入成功。${actionMessage}`)
    close()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '转学生录入失败'
  } finally {
    saving.value = false
  }
}

function reset() {
  form.studentNumber = ''
  form.studentName = ''
  form.gender = 'M'
  form.majorId = Number(enabledMajors.value[0]?.id ?? 0)
  form.nationalityCode = 'CN'
  form.studentCategory = 'DOMESTIC'
  form.phoneNumber = ''
  form.action = 'PROFILE_ONLY'
  form.roomId = 0
  form.bedId = 0
  form.batchId = 0
  form.reason = ''
  error.value = ''
  beds.value = []
}

function close() {
  if (saving.value) return
  visible.value = false
  reset()
}

function roomScopeLabel(value: unknown) {
  return {
    DOMESTIC_ONLY: '国内生宿舍',
    INTERNATIONAL_ONLY: '国际生宿舍',
    MIXED: '混住宿舍',
  }[String(value)] ?? '未设置'
}

function modeLabel(value: unknown) {
  return String(value) === 'BED' ? '选择床位' : '选择寝室'
}
</script>

<template>
  <div v-if="visible" class="modal-overlay transfer-overlay" @click.self="close">
    <section class="modal-card transfer-wizard" role="dialog" aria-modal="true" aria-labelledby="transfer-title">
      <header class="transfer-header">
        <div>
          <span class="eyebrow">TRANSFER STUDENT</span>
          <h3 id="transfer-title">手工录入转学生</h3>
          <p>录入学生完整资料，并选择仅建档、直接安排入住或加入现有批次。</p>
        </div>
        <button class="button ghost small" type="button" :disabled="saving" @click="close">关闭</button>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>

      <form class="transfer-form" @submit.prevent="submit">
        <section class="wizard-section">
          <div class="wizard-section-title"><strong>1. 学生资料</strong><span>全部字段由管理员核对录入</span></div>
          <div class="form-grid three-column">
            <label><span>12位学号</span><input v-model.trim="form.studentNumber" class="input" required pattern="\d{12}" maxlength="12" /></label>
            <label><span>姓名</span><input v-model.trim="form.studentName" class="input" required maxlength="128" /></label>
            <label><span>性别</span><select v-model="form.gender" class="input"><option value="M">男生</option><option value="F">女生</option></select></label>
            <label><span>专业</span><select v-model.number="form.majorId" class="input" required><option v-for="major in enabledMajors" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select></label>
            <label><span>国籍代码</span><input v-model.trim="form.nationalityCode" class="input" required pattern="[A-Za-z]{2}" maxlength="2" /></label>
            <label><span>手机号码</span><input v-model.trim="form.phoneNumber" class="input" maxlength="32" placeholder="可留空" /></label>
          </div>
          <div class="segmented-control category-control" aria-label="学生类别">
            <button type="button" :class="{ active: form.studentCategory === 'DOMESTIC' }" @click="form.studentCategory = 'DOMESTIC'">国内生</button>
            <button type="button" :class="{ active: form.studentCategory === 'INTERNATIONAL' }" @click="form.studentCategory = 'INTERNATIONAL'">国际生</button>
          </div>
        </section>

        <section class="wizard-section">
          <div class="wizard-section-title"><strong>2. 后续处理</strong><span>三种方式均会创建待激活学生账号</span></div>
          <div class="action-card-grid">
            <button type="button" class="action-card" :class="{ selected: form.action === 'PROFILE_ONLY' }" @click="chooseAction('PROFILE_ONLY')">
              <strong>仅录入资料</strong><span>暂不分配，等待后续新批次</span>
            </button>
            <button type="button" class="action-card" :class="{ selected: form.action === 'DIRECT_ASSIGNMENT' }" @click="chooseAction('DIRECT_ASSIGNMENT')">
              <strong>直接安排入住</strong><span>分配寝室，可选确认具体床位</span>
            </button>
            <button type="button" class="action-card" :class="{ selected: form.action === 'ADD_TO_BATCH' }" @click="chooseAction('ADD_TO_BATCH')">
              <strong>加入现有批次</strong><span>自动校验符合条件的剩余容量</span>
            </button>
          </div>

          <div v-if="form.action === 'DIRECT_ASSIGNMENT'" class="conditional-panel">
            <label><span>选择寝室</span><select v-model.number="form.roomId" class="input" required><option :value="0" disabled>请选择符合性别和类别的寝室</option><option v-for="room in filteredRooms" :key="String(room.id)" :value="Number(room.id)">{{ room.building_name }} {{ room.room_number }} · {{ roomScopeLabel(room.resident_scope) }} · 剩余{{ room.remaining_capacity ?? room.capacity }}人</option></select></label>
            <label><span>具体床位（可选）</span><select v-model.number="form.bedId" class="input" :disabled="!form.roomId || loadingBeds"><option :value="0">暂不确认床位，由学生入住后协商</option><option v-for="bed in beds" :key="String(bed.id)" :value="Number(bed.id)">{{ bed.bed_code }} · {{ bed.bed_type }}</option></select></label>
          </div>

          <div v-if="form.action === 'ADD_TO_BATCH'" class="conditional-panel">
            <label><span>选择现有批次</span><select v-model.number="form.batchId" class="input" required><option :value="0" disabled>请选择批次</option><option v-for="batch in batches" :key="String(batch.id)" :value="Number(batch.id)">{{ batch.batch_name }} · {{ modeLabel(batch.selection_mode) }} · {{ batch.batch_status }}</option></select></label>
            <div class="capacity-note"><strong>事务内容量预检</strong><span>系统会按性别、国内生/国际生属性、批次范围、当前在住人数和活动冲突进行检查。没有足够符合条件的宿舍时，本次学生录入和入批次会整体回滚。</span></div>
          </div>
        </section>

        <section class="wizard-section">
          <div class="wizard-section-title"><strong>3. 操作依据</strong><span>将写入业务审计</span></div>
          <label><span>录入原因</span><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="3" placeholder="例如：2026级转专业学生补录"></textarea></label>
        </section>

        <footer class="transfer-actions"><button class="button ghost" type="button" :disabled="saving" @click="close">取消</button><button class="button primary" type="submit" :disabled="saving">{{ saving ? '正在校验并保存…' : '确认录入' }}</button></footer>
      </form>
    </section>
  </div>
</template>

<style scoped>
.transfer-overlay { z-index: 80; }
.transfer-wizard { width: min(980px, calc(100vw - 32px)); max-height: calc(100vh - 32px); overflow: auto; padding: 24px; }
.transfer-header, .wizard-section-title, .transfer-actions { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.transfer-header h3 { margin: 4px 0; }
.transfer-header p, .wizard-section-title span { color: var(--text-muted); }
.transfer-form { display: grid; gap: 18px; margin-top: 18px; }
.wizard-section { padding: 18px; border: 1px solid var(--border); border-radius: 16px; background: var(--surface-soft); }
.wizard-section-title { margin-bottom: 14px; }
.action-card-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.action-card { display: grid; gap: 7px; padding: 16px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface); text-align: left; color: inherit; }
.action-card span { color: var(--text-muted); font-size: 13px; }
.action-card.selected { border-color: var(--primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 14%, transparent); }
.segmented-control { display: inline-flex; padding: 4px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); }
.segmented-control button { border: 0; border-radius: 9px; padding: 9px 18px; background: transparent; color: var(--text-muted); }
.segmented-control button.active { background: var(--primary); color: white; }
.category-control { margin-top: 14px; }
.conditional-panel { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 16px; padding: 16px; border-radius: 14px; background: var(--surface); }
.conditional-panel label { display: grid; gap: 7px; }
.capacity-note { display: grid; gap: 6px; padding: 14px; border-radius: 12px; background: color-mix(in srgb, var(--primary) 8%, var(--surface)); }
.capacity-note span { color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.transfer-actions { justify-content: flex-end; }
@media (max-width: 760px) { .action-card-grid, .conditional-panel { grid-template-columns: 1fr; } .transfer-header { flex-direction: column; } }
</style>
