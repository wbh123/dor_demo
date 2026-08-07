<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppModal from '../modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const props = defineProps<{
  open: boolean
  student: DataObject | null
}>()
const emit = defineEmits<{ close: []; sent: [message: string] }>()

const templates = ref<DataObject[]>([])
const templateRevisionId = ref('')
const reason = ref('')
const preview = ref<DataObject | null>(null)
const loading = ref(false)
const sending = ref(false)
const error = ref('')

const studentIds = computed(() => props.student?.id ? [Number(props.student.id)] : [])

watch(() => props.open, async (open) => {
  if (!open) return
  templateRevisionId.value = ''
  reason.value = ''
  preview.value = null
  error.value = ''
  loading.value = true
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/templates')
    templates.value = (response.data.data ?? []) as DataObject[]
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '通知模板加载失败'
  } finally {
    loading.value = false
  }
})

async function preflight() {
  if (!templateRevisionId.value || !studentIds.value.length) return
  loading.value = true
  error.value = ''
  try {
    const response = await api.post<ObjectSuccessResponse>(
      '/api/v1/admin/governance/notifications/preflight',
      {
        criteria: { studentIds: studentIds.value },
        templateRevisionId: Number(templateRevisionId.value),
        variables: {},
      },
    )
    preview.value = (response.data.data ?? {}) as DataObject
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '私信内容预检失败'
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!preview.value || reason.value.trim().length < 2 || sending.value) return
  sending.value = true
  error.value = ''
  try {
    await api.post('/api/v1/admin/governance/notifications/schedule', {
      criteria: { studentIds: studentIds.value },
      templateRevisionId: Number(templateRevisionId.value),
      variables: {},
      scheduledAt: null,
      zoneId: 'Asia/Shanghai',
      reason: reason.value.trim(),
    })
    emit('sent', `已向${String(props.student?.student_name ?? '该学生')}创建站内私信任务。`)
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '私信发送失败'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <AppModal :open="open" :busy="sending" title="发送学生私信" size="wide" busy-text="正在创建私信任务…" @close="!sending && emit('close')">
    <div v-if="student" class="student-message-dialog">
      <div class="student-summary"><div><strong>{{ student.student_name }}</strong><span>{{ student.student_number }}</span></div><small>{{ student.major_name ?? '未设置专业' }}</small></div>
      <p v-if="error" class="alert error">{{ error }}</p>
      <label><span>通知模板</span><select v-model="templateRevisionId" class="input" :disabled="loading"><option value="">请选择模板修订</option><option v-for="item in templates" :key="String(item.revision_id)" :value="String(item.revision_id)">{{ item.template_name }} · 修订{{ item.revision }}</option></select></label>
      <button class="button secondary" type="button" :disabled="!templateRevisionId || loading" @click="preflight">{{ loading ? '正在预检…' : '预览私信内容' }}</button>
      <div v-if="preview" class="private-message-preview"><strong>{{ preview.titleZhCn }}</strong><p>{{ preview.contentZhCn }}</p><small>接收人：{{ preview.recipientCount }}人</small></div>
      <label><span>发送原因</span><textarea v-model.trim="reason" class="input" rows="3" minlength="2" maxlength="500" placeholder="例如：单独提醒该学生补充材料"></textarea></label>
    </div>
    <template #footer><button class="button ghost" type="button" :disabled="sending" @click="emit('close')">取消</button><button class="button primary" type="button" :disabled="!preview || reason.trim().length < 2 || sending" @click="send">{{ sending ? '正在发送…' : '确认发送私信' }}</button></template>
  </AppModal>
</template>

<style scoped>
.student-message-dialog{display:grid;gap:14px}.student-message-dialog label{display:grid;gap:6px}.student-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border-radius:12px;background:var(--surface-soft)}.student-summary div{display:grid;gap:2px}.student-summary span,.student-summary small{color:var(--text-muted)}.private-message-preview{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.private-message-preview p{margin:0;line-height:1.7}.private-message-preview small{color:var(--text-muted)}
</style>
