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

function selectTemplate(item: DataObject) {
  emit('update:selected-template-revision-id', String(item.revision_id ?? ''))
}
</script>

<template>
  <section class="panel governance-section">
    <header class="section-head">
      <div>
        <span class="eyebrow">NOTIFICATION</span>
        <h3>统一通知中心</h3>
        <p>模板修订、双语内容、变量、接收范围和预览集中在同一工作台。当前仅发送站内通知。</p>
      </div>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="notification-workbench">
      <aside class="notification-template-list">
        <div class="workspace-title"><strong>模板与历史修订</strong><span>{{ templates.length }}项</span></div>
        <button
          v-for="item in templates"
          :key="String(item.revision_id)"
          class="template-revision-card"
          :class="{active:selectedTemplateRevisionId===String(item.revision_id)}"
          type="button"
          @click="selectTemplate(item)"
        >
          <strong>{{ item.template_name }}</strong>
          <span>{{ item.template_code }} · 修订{{ item.revision }}</span>
          <small>{{ item.enabled ? '当前可用' : '已停用' }}</small>
        </button>
        <p v-if="!templates.length" class="empty-state compact">暂无通知模板。</p>
      </aside>

      <main class="notification-editor-column">
        <div class="workspace-title"><strong>通知内容</strong><span>旧修订不会被覆盖</span></div>
        <NotificationTemplateEditor
          v-if="canTemplateManage"
          :model-value="templateDraft"
          :busy="busy"
          @update:model-value="emit('update:template-draft', $event)"
          @save="emit('save-template')"
        />
        <div v-else-if="selectedTemplateRevisionId" class="preview-card">
          <strong>已选择模板修订</strong>
          <span>修订编号 {{ selectedTemplateRevisionId }}</span>
        </div>
        <p v-else class="empty-state">请选择一个模板修订。</p>
      </main>

      <aside v-if="canNotificationSend" class="notification-recipient-column">
        <div class="workspace-title"><strong>接收范围与预览</strong><span>先预检再发送</span></div>
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
          <h4>{{ preview.titleZhCn }}</h4>
          <p>{{ preview.contentZhCn }}</p>
        </div>

        <label v-if="canNotificationSchedule" class="schedule-field">
          <span>定时发送时间（留空立即执行）</span>
          <input class="input" type="datetime-local" :value="scheduledAt" @input="emit('update:scheduled-at', ($event.target as HTMLInputElement).value)" />
        </label>

        <button class="button primary" type="button" :disabled="!preview || busy" @click="emit('confirm-send')">确认发送范围与内容</button>
      </aside>
    </div>

    <div v-if="canNotificationStatus" class="notification-task-section">
      <div class="workspace-title"><strong>发送任务</strong><span>最近状态</span></div>
      <div class="task-list">
        <article v-for="task in tasks" :key="String(task.id)">
          <strong>{{ task.task_status }}</strong>
          <span>{{ task.recipient_count }}人 · {{ task.scheduled_at }} · {{ task.time_zone }}</span>
          <button v-if="task.task_status==='SCHEDULED'&&canNotificationSchedule" class="button ghost small" type="button" :disabled="busy" @click="emit('cancel-task', Number(task.id))">取消</button>
        </article>
        <p v-if="!tasks.length" class="empty-state compact">暂无发送任务。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.governance-section{display:grid;gap:18px}.notification-workbench{display:grid;grid-template-columns:minmax(190px,.65fr) minmax(360px,1.35fr) minmax(320px,1fr);gap:14px;align-items:start}.notification-template-list,.notification-editor-column,.notification-recipient-column{display:grid;align-content:start;gap:12px;min-width:0;padding:14px;border:1px solid var(--border);border-radius:15px;background:var(--surface)}.workspace-title{display:flex;align-items:center;justify-content:space-between;gap:10px}.workspace-title span{color:var(--text-muted);font-size:11px}.template-revision-card{display:grid;gap:3px;width:100%;border:1px solid var(--border);border-radius:11px;padding:10px;background:var(--surface-soft);color:inherit;text-align:left;cursor:pointer}.template-revision-card.active{border-color:var(--primary);box-shadow:0 0 0 2px color-mix(in srgb,var(--primary) 12%,transparent)}.template-revision-card span,.template-revision-card small{color:var(--text-muted);font-size:11px}.preview-card{display:grid;gap:7px;padding:14px;border:1px solid var(--border);border-radius:13px;background:var(--surface-soft)}.preview-card h4,.preview-card p{margin:0}.schedule-field{display:grid;gap:6px}.notification-task-section{display:grid;gap:10px;padding-top:16px;border-top:1px solid var(--border)}.task-list{display:grid;gap:8px}.task-list article{display:flex;align-items:center;gap:12px;padding:11px;border:1px solid var(--border);border-radius:11px}.task-list article span{color:var(--text-muted);font-size:12px;flex:1}@media(max-width:1180px){.notification-workbench{grid-template-columns:220px 1fr}.notification-recipient-column{grid-column:1/-1}}@media(max-width:760px){.notification-workbench{grid-template-columns:1fr}.notification-recipient-column{grid-column:auto}.task-list article{align-items:flex-start;flex-direction:column}}
</style>
