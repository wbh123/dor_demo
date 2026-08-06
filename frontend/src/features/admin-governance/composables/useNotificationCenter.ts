import { computed, reactive, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../../api/types'

export interface NotificationTemplateDraft {
  templateCode: string
  templateName: string
  titleZhCn: string
  contentZhCn: string
  titleEnUs: string
  contentEnUs: string
  enabled: boolean
  creationReason: string
}

export interface NotificationRecipientCriteria {
  studentIds: number[]
  batchId: string
  majorId: string
  gradeYear: string
  degreeLevel: string
  studentCategory: string
  buildingId: string
  unselectedOnly: boolean
  pendingReviewOnly: boolean
}

interface NotificationCapabilities {
  canTemplateManage: () => boolean
  canNotificationSend: () => boolean
  canNotificationStatus: () => boolean
}

export function useNotificationCenter(capabilities: NotificationCapabilities) {
  const templateDraft = reactive<NotificationTemplateDraft>({
    templateCode: '', templateName: '', titleZhCn: '', contentZhCn: '',
    titleEnUs: '', contentEnUs: '', enabled: true, creationReason: '',
  })
  const templates = ref<DataObject[]>([])
  const recipientCriteria = reactive<NotificationRecipientCriteria>({
    studentIds: [], batchId: '', majorId: '', gradeYear: '', degreeLevel: '',
    studentCategory: '', buildingId: '', unselectedOnly: false, pendingReviewOnly: false,
  })
  const selectedTemplateRevisionId = ref('')
  const recipientCount = ref<number | undefined>()
  const preview = ref<DataObject | null>(null)
  const tasks = ref<DataObject[]>([])
  const scheduledAt = ref('')
  const pendingActions = ref(0)
  const busy = computed(() => pendingActions.value > 0)
  const error = ref('')
  const message = ref('')

  async function execute(action: () => Promise<void>) {
    pendingActions.value += 1
    error.value = ''
    try {
      await action()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '通知中心操作失败'
    } finally {
      pendingActions.value -= 1
    }
  }

  async function loadTemplates() {
    await execute(async () => {
      const response = await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/templates')
      templates.value = (response.data.data ?? []) as DataObject[]
    })
  }

  async function loadStatus() {
    if (!capabilities.canNotificationStatus()) return
    await execute(async () => {
      const response = await api.get<ListSuccessResponse>('/api/v1/admin/governance/notifications/status')
      tasks.value = (response.data.data ?? []) as DataObject[]
    })
  }

  async function saveTemplate() {
    if (!capabilities.canTemplateManage()) return
    message.value = ''
    await execute(async () => {
      await api.post('/api/v1/admin/governance/notifications/templates/revisions', templateDraft)
      message.value = '通知模板修订已保存，旧修订保持不变。'
      await loadTemplates()
    })
  }

  async function preflightRecipients() {
    if (!capabilities.canNotificationSend()) return
    message.value = ''
    await execute(async () => {
      const response = await api.post<ObjectSuccessResponse>(
        '/api/v1/admin/governance/notifications/preflight',
        {
          criteria: criteriaPayload(),
          templateRevisionId: Number(selectedTemplateRevisionId.value),
          variables: {},
        },
      )
      preview.value = (response.data.data ?? {}) as DataObject
      recipientCount.value = Number(preview.value.recipientCount ?? 0)
    })
  }

  async function sendNotification(payload: { reason: string }) {
    message.value = ''
    let succeeded = false
    await execute(async () => {
      await api.post('/api/v1/admin/governance/notifications/schedule', {
        criteria: criteriaPayload(),
        templateRevisionId: Number(selectedTemplateRevisionId.value),
        variables: {},
        scheduledAt: toIso(scheduledAt.value),
        zoneId: 'Asia/Shanghai',
        reason: payload.reason,
      })
      message.value = scheduledAt.value ? '定时站内通知已创建。' : '站内通知已进入发送任务。'
      succeeded = true
      await loadStatus()
    })
    if (!succeeded) throw new Error(error.value || '通知任务创建失败')
  }

  async function cancelTask(taskId: number) {
    message.value = ''
    await execute(async () => {
      await api.post(`/api/v1/admin/governance/notifications/${taskId}/cancel`)
      message.value = '通知任务已取消。'
      await loadStatus()
    })
  }

  function updateTemplateDraft(value: NotificationTemplateDraft) {
    Object.assign(templateDraft, value)
  }

  function updateRecipientCriteria(value: NotificationRecipientCriteria) {
    Object.assign(recipientCriteria, value)
  }

  function criteriaPayload() {
    return {
      studentIds: recipientCriteria.studentIds,
      batchId: numberOrNull(recipientCriteria.batchId),
      majorId: numberOrNull(recipientCriteria.majorId),
      gradeYear: numberOrNull(recipientCriteria.gradeYear),
      degreeLevel: recipientCriteria.degreeLevel,
      studentCategory: recipientCriteria.studentCategory,
      buildingId: numberOrNull(recipientCriteria.buildingId),
      unselectedOnly: recipientCriteria.unselectedOnly,
      pendingReviewOnly: recipientCriteria.pendingReviewOnly,
    }
  }

  return {
    templateDraft,
    templates,
    recipientCriteria,
    selectedTemplateRevisionId,
    recipientCount,
    preview,
    tasks,
    scheduledAt,
    busy,
    error,
    message,
    loadTemplates,
    loadStatus,
    saveTemplate,
    preflightRecipients,
    sendNotification,
    cancelTask,
    updateTemplateDraft,
    updateRecipientCriteria,
  }
}

function numberOrNull(value: string) {
  const parsed = Number(value)
  return value.trim() && Number.isFinite(parsed) ? parsed : null
}

function toIso(value: string) {
  return value ? new Date(value).toISOString() : null
}
