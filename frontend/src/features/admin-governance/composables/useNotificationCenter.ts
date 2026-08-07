import { computed, reactive, ref } from 'vue'
import { api } from '../../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../../api/types'

export interface NotificationTemplateDraft {
  templateId?: number
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
  batchIds: number[]
  majorIds: number[]
  buildingIds: number[]
  gradeYears: number[]
  degreeLevels: string[]
  studentCategories: string[]
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
    studentIds: [], batchIds: [], majorIds: [], buildingIds: [], gradeYears: [],
    degreeLevels: [], studentCategories: [], unselectedOnly: false, pendingReviewOnly: false,
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
      const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/governance/notifications/templates/revisions', templateDraft)
      const saved = (response.data.data ?? {}) as DataObject
      templateDraft.templateId = Number(saved.templateId ?? templateDraft.templateId ?? 0) || undefined
      templateDraft.templateCode = String(saved.templateCode ?? templateDraft.templateCode ?? '')
      selectedTemplateRevisionId.value = String(saved.revisionId ?? selectedTemplateRevisionId.value)
      templateDraft.creationReason = ''
      message.value = '当前通知内容已保存为模板新修订，旧修订保持不变。'
      await loadTemplates()
    })
  }

  async function preflightRecipients() {
    if (!capabilities.canNotificationSend()) return
    message.value = ''
    await execute(async () => {
      const response = await api.post<ObjectSuccessResponse>(
        '/api/v1/admin/governance/notifications/recipients/count',
        { criteria: criteriaPayload() },
      )
      const data = (response.data.data ?? {}) as DataObject
      recipientCount.value = Number(data.recipientCount ?? 0)
      preview.value = {
        recipientCount: recipientCount.value,
        titleZhCn: templateDraft.titleZhCn,
        contentZhCn: templateDraft.contentZhCn,
        titleEnUs: templateDraft.titleEnUs,
        contentEnUs: templateDraft.contentEnUs,
        direct: !scheduledAt.value,
      }
    })
  }

  async function sendNotification(payload: { reason: string }) {
    message.value = ''
    let succeeded = false
    await execute(async () => {
      if (scheduledAt.value) {
        if (!selectedTemplateRevisionId.value) {
          throw new Error('定时发送需要先将当前内容保存为模板，或选择已有模板修订。')
        }
        await api.post('/api/v1/admin/governance/notifications/schedule', {
          criteria: criteriaPayload(),
          templateRevisionId: Number(selectedTemplateRevisionId.value),
          variables: {},
          scheduledAt: toIso(scheduledAt.value),
          zoneId: 'Asia/Shanghai',
          reason: payload.reason,
        })
        message.value = '定时站内通知已创建。'
        await loadStatus()
      } else {
        await api.post('/api/v1/admin/governance/notifications/direct', {
          criteria: criteriaPayload(),
          titleZhCn: templateDraft.titleZhCn,
          contentZhCn: templateDraft.contentZhCn,
          titleEnUs: templateDraft.titleEnUs,
          contentEnUs: templateDraft.contentEnUs,
          reason: payload.reason,
        })
        message.value = '当前编辑内容已直接发送为站内通知。'
      }
      succeeded = true
    })
    if (!succeeded) throw new Error(error.value || '通知发送失败')
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
    preview.value = null
  }

  function updateRecipientCriteria(value: NotificationRecipientCriteria) {
    Object.assign(recipientCriteria, value)
    recipientCount.value = undefined
    preview.value = null
  }

  function criteriaPayload() {
    return {
      studentIds: recipientCriteria.studentIds,
      batchIds: recipientCriteria.batchIds,
      majorIds: recipientCriteria.majorIds,
      buildingIds: recipientCriteria.buildingIds,
      gradeYears: recipientCriteria.gradeYears,
      degreeLevels: recipientCriteria.degreeLevels,
      studentCategories: recipientCriteria.studentCategories,
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

function toIso(value: string) {
  return value ? new Date(value).toISOString() : null
}
