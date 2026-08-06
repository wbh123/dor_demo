<script setup lang="ts">
import type { DataObject } from '../../../api/types'
import NotificationTemplateEditor from '../../../components/admin/NotificationTemplateEditor.vue'
import RecipientSelector from '../../../components/admin/RecipientSelector.vue'
import type {
  NotificationRecipientCriteria,
  NotificationTemplateDraft,
} from '../composables/useNotificationCenter'

const props = defineProps<{
  templateDraft: NotificationTemplateDraft
  templates: DataObject[]
  recipientCriteria: NotificationRecipientCriteria
  selectedTemplateRevisionId: string
  recipientCount?: number
  preview: DataObject | null
  tasks: DataObject[]
  scheduledAt: string
  canTemplateManage: boolean
  canNotificationSend: boolean
  canNotificationSchedule: boolean
  canNotificationStatus: boolean
  busy?: boolean
  error?: string
  message?: string
}>()

const emit = defineEmits<{
  'update:template-draft': [value: NotificationTemplateDraft]
  'update:recipient-criteria': [value: NotificationRecipientCriteria]
  'update:selected-template-revision-id': [value: string]
  'update:scheduled-at': [value: string]
  'save-template': []
  preflight: []
  'confirm-send': []
  'cancel-task': [taskId: number]
}>()
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">NOTIFICATION</span>
        <h3>统一通知中心</h3>
        <p>Notification center · 本轮只实现站内通知，短信、邮件、移动推送和渐进式网页应用推送未接入。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <NotificationTemplateEditor
      v-if="canTemplateManage"
      :model-value="templateDraft"
      :busy="busy"
      @update:model-value="emit('update:template-draft', $event)"
      @save="emit('save-template')"
    />

    <div v-if="canNotificationSend" class="notification-compose">
      <label>
        <span>模板修订</span>
        <select
          class="input"
          :value="selectedTemplateRevisionId"
          @change="emit('update:selected-template-revision-id', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">请选择</option>
          <option
            v-for="item in templates"
            :key="String(item.revision_id)"
            :value="String(item.revision_id)"
          >{{ item.template_name }} · 修订{{ item.revision }}</option>
        </select>
      </label>

      <RecipientSelector
        :model-value="recipientCriteria"
        :recipient-count="recipientCount"
        :busy="busy"
        @update:model-value="emit('update:recipient-criteria', $event)"
        @preflight="emit('preflight')"
      />

      <div v-if="preview" class="preview-card">
        <strong>内容预览</strong>
        <span>接收人 {{ preview.recipientCount }} 人</span>
        <p>{{ preview.titleZhCn }}</p>
        <p>{{ preview.contentZhCn }}</p>
      </div>

      <label v-if="canNotificationSchedule">
        <span>定时发送时间（留空立即执行）</span>
        <input
          class="input"
          type="datetime-local"
          :value="scheduledAt"
          @input="emit('update:scheduled-at', ($event.target as HTMLInputElement).value)"
        />
      </label>

      <button
        class="button primary"
        type="button"
        :disabled="!preview || busy"
        @click="emit('confirm-send')"
      >确认发送范围与内容</button>
    </div>

    <div v-if="canNotificationStatus" class="task-list">
      <article v-for="task in tasks" :key="String(task.id)">
        <strong>{{ task.task_status }}</strong>
        <span>{{ task.recipient_count }}人 · {{ task.scheduled_at }} · {{ task.time_zone }}</span>
        <button
          v-if="task.task_status==='SCHEDULED'&&canNotificationSchedule"
          class="button ghost small"
          type="button"
          :disabled="busy"
          @click="emit('cancel-task', Number(task.id))"
        >取消</button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.notification-compose{display:grid;gap:14px;padding-top:16px;border-top:1px solid var(--border)}.notification-compose>label{display:grid;gap:7px}.preview-card{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.preview-card p{margin:0}.task-list{display:grid;gap:8px}.task-list article{display:flex;align-items:center;gap:12px;padding:11px;border:1px solid var(--border);border-radius:11px}.task-list article span{color:var(--text-muted);font-size:12px;flex:1}@media(max-width:620px){.task-list article{align-items:flex-start;flex-direction:column}}
</style>
