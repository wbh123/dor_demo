<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppModal from '../modal/AppModal.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

type ComposeMode = 'DIRECT' | 'TEMPLATE'

const props = defineProps<{
  open: boolean
  student: DataObject | null
}>()
const emit = defineEmits<{ close: []; sent: [message: string] }>()

const mode = ref<ComposeMode>('DIRECT')
const templates = ref<DataObject[]>([])
const templateRevisionId = ref('')
const directTitleZhCn = ref('')
const directContentZhCn = ref('')
const directTitleEnUs = ref('')
const directContentEnUs = ref('')
const reason = ref('')
const preview = ref<DataObject | null>(null)
const loading = ref(false)
const sending = ref(false)
const error = ref('')

const studentIds = computed(() => props.student?.id ? [Number(props.student.id)] : [])
const directReady = computed(() => directTitleZhCn.value.trim().length > 0 && directContentZhCn.value.trim().length > 0)
const canSend = computed(() => reason.value.trim().length >= 2
  && (mode.value === 'DIRECT' ? directReady.value : Boolean(preview.value)))

watch(() => props.open, async (open) => {
  if (!open) return
  mode.value = 'DIRECT'
  templateRevisionId.value = ''
  directTitleZhCn.value = ''
  directContentZhCn.value = ''
  directTitleEnUs.value = ''
  directContentEnUs.value = ''
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

watch(mode, () => {
  preview.value = null
  error.value = ''
})
watch(templateRevisionId, () => { preview.value = null })

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
  if (!canSend.value || sending.value || !studentIds.value.length) return
  sending.value = true
  error.value = ''
  try {
    if (mode.value === 'DIRECT') {
      await api.post('/api/v1/admin/governance/notifications/direct', {
        criteria: { studentIds: studentIds.value },
        titleZhCn: directTitleZhCn.value.trim(),
        contentZhCn: directContentZhCn.value.trim(),
        titleEnUs: directTitleEnUs.value.trim(),
        contentEnUs: directContentEnUs.value.trim(),
        reason: reason.value.trim(),
      })
    } else {
      await api.post('/api/v1/admin/governance/notifications/schedule', {
        criteria: { studentIds: studentIds.value },
        templateRevisionId: Number(templateRevisionId.value),
        variables: {},
        scheduledAt: null,
        zoneId: 'Asia/Shanghai',
        reason: reason.value.trim(),
      })
    }
    emit('sent', `已向${String(props.student?.student_name ?? '该学生')}发送站内私信。`)
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '私信发送失败'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <AppModal :open="open" :busy="sending" title="发送学生私信" size="wide" busy-text="正在发送私信…" @close="!sending && emit('close')">
    <div v-if="student" class="student-message-dialog">
      <div class="student-summary"><div><strong>{{ student.student_name }}</strong><span>{{ student.student_number }}</span></div><small>{{ student.major_name ?? '未设置专业' }}</small></div>
      <p v-if="error" class="alert error">{{ error }}</p>
      <div class="compose-mode-switch" role="tablist" aria-label="私信编辑方式">
        <button type="button" class="mode-card" :class="{active:mode==='DIRECT'}" @click="mode='DIRECT'"><strong>直接编辑</strong><span>不使用模板，直接填写标题和正文</span></button>
        <button type="button" class="mode-card" :class="{active:mode==='TEMPLATE'}" @click="mode='TEMPLATE'"><strong>使用模板</strong><span>选择已有模板并预览后发送</span></button>
      </div>

      <div v-if="mode==='DIRECT'" class="direct-compose">
        <label><span>中文标题</span><input v-model.trim="directTitleZhCn" class="input" maxlength="200" placeholder="例如：个人资料补充提醒" /></label>
        <label><span>中文正文</span><textarea v-model="directContentZhCn" class="input" rows="7" maxlength="5000" placeholder="请输入要直接发送给该学生的内容"></textarea></label>
        <details class="english-compose"><summary>补充英文内容（可选）</summary><div><label><span>English title</span><input v-model.trim="directTitleEnUs" class="input" maxlength="200" /></label><label><span>English content</span><textarea v-model="directContentEnUs" class="input" rows="5" maxlength="5000"></textarea></label></div></details>
        <div v-if="directReady" class="private-message-preview"><strong>{{ directTitleZhCn }}</strong><p>{{ directContentZhCn }}</p><small>接收人：1人 · 直接发送</small></div>
      </div>

      <div v-else class="template-compose">
        <label><span>通知模板</span><select v-model="templateRevisionId" class="input" :disabled="loading"><option value="">请选择模板修订</option><option v-for="item in templates" :key="String(item.revision_id)" :value="String(item.revision_id)">{{ item.template_name }} · 修订{{ item.revision }}</option></select></label>
        <button class="button secondary" type="button" :disabled="!templateRevisionId || loading" @click="preflight">{{ loading ? '正在预检…' : '预览私信内容' }}</button>
        <div v-if="preview" class="private-message-preview"><strong>{{ preview.titleZhCn }}</strong><p>{{ preview.contentZhCn }}</p><small>接收人：{{ preview.recipientCount }}人</small></div>
      </div>

      <label><span>发送原因</span><textarea v-model.trim="reason" class="input" rows="3" minlength="2" maxlength="500" placeholder="例如：单独提醒该学生补充材料"></textarea></label>
    </div>
    <template #footer><button class="button ghost" type="button" :disabled="sending" @click="emit('close')">取消</button><button class="button primary" type="button" :disabled="!canSend || sending" @click="send">{{ sending ? '正在发送…' : '确认发送私信' }}</button></template>
  </AppModal>
</template>

<style scoped>
.student-message-dialog{display:grid;gap:14px}.student-message-dialog label{display:grid;gap:6px}.student-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border-radius:12px;background:var(--surface-soft)}.student-summary div{display:grid;gap:2px}.student-summary span,.student-summary small{color:var(--text-muted)}.compose-mode-switch{display:grid;grid-template-columns:1fr 1fr;gap:10px}.mode-card{display:grid;gap:4px;padding:13px;border:1px solid var(--border);border-radius:13px;background:var(--surface);color:inherit;text-align:left;cursor:pointer}.mode-card span{color:var(--text-muted);font-size:12px}.mode-card.active{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 10%,transparent)}.direct-compose,.template-compose{display:grid;gap:12px}.english-compose{padding:10px 12px;border:1px solid var(--border);border-radius:12px}.english-compose summary{cursor:pointer;font-weight:700}.english-compose>div{display:grid;gap:10px;margin-top:10px}.private-message-preview{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.private-message-preview p{margin:0;line-height:1.7;white-space:pre-wrap}.private-message-preview small{color:var(--text-muted)}@media(max-width:640px){.compose-mode-switch{grid-template-columns:1fr}}
</style>
