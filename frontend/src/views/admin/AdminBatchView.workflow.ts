// @ts-nocheck
import { computed, nextTick, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useAdminBatchView } from './AdminBatchView.logic'

export type PublishFlowState =
  | 'IDLE'
  | 'CREATING_DRAFT'
  | 'SAVING_SCOPE'
  | 'RUNNING_PREFLIGHT'
  | 'WAITING_CONFIRMATION'
  | 'PUBLISHING'
  | 'SUCCEEDED'
  | 'FAILED'

export function useAdminBatchViewWorkflow() {
  const base = useAdminBatchView()
  const publishFlowState = ref<PublishFlowState>('IDLE')
  const publishFlowError = ref('')
  const activeDraftId = ref<number | null>(null)
  const activeDraftCode = ref('')

  const publishFlowBusy = computed(() => [
    'CREATING_DRAFT',
    'SAVING_SCOPE',
    'RUNNING_PREFLIGHT',
    'PUBLISHING',
  ].includes(publishFlowState.value))

  const publishConfirmationFacts = computed(() => {
    const batch = base.publishConfirmation.value ?? {}
    const snapshot = base.publishPreflightSnapshot.value ?? {}
    const rule = base.ruleTemplates.value.find((item) =>
      Number(item.id) === Number(batch.rule_template_id ?? batch.ruleTemplateId),
    )
    return {
      batchName: String(batch.batch_name ?? batch.batchName ?? ''),
      studentCount: Number(batch.eligible_count ?? snapshot.eligibleCount ?? base.selectedStudentIds.value.length ?? 0),
      roomCount: Number(snapshot.roomCount ?? base.selectedRoomIds.value.length ?? 0),
      bedCount: Number(snapshot.availableCapacity ?? 0),
      selectionMode: base.modeText(batch.selection_mode ?? batch.selectionMode),
      openTime: formatRange(batch.start_at ?? batch.startAt, batch.end_at ?? batch.endAt),
      ruleSummary: rule
        ? `${rule.rule_name} · 修订${rule.revision}`
        : base.ruleTemplateSummary.value,
      restriction: '发布后参与范围和规则不能直接改写；如需调整，应暂停或取消后按业务流程处理。',
    }
  })

  const missingPublishSteps = computed(() => {
    const steps: string[] = []
    if (base.selectedStudentIds.value.length === 0
      && Number(base.scopeBatch.value?.eligible_count ?? 0) === 0) {
      steps.push('至少选择一名参与学生')
    }
    if (base.selectedRoomIds.value.length === 0
      && Number(base.publishPreflightSnapshot.value?.roomCount ?? 0) === 0) {
      steps.push('至少选择一间可选宿舍')
    }
    if (!base.scopeBatch.value?.rule_template_id && !base.scopeBatch.value?.ruleTemplateId
      && !base.form.ruleTemplateId) {
      steps.push('绑定有效规则模板')
    }
    return steps
  })

  async function createBatch(intent: 'draft' | 'publish' = 'draft') {
    if (publishFlowBusy.value) return
    base.error.value = ''
    base.message.value = ''
    publishFlowError.value = ''
    if (base.form.selectionMode === 'BED' && !base.bedModeAuthorized.value) {
      fail('当前服务未开放选择床位模式。')
      return
    }

    const request = {
      batchCode: base.form.batchCode,
      batchName: base.form.batchName,
      startAt: new Date(base.form.startAt).toISOString(),
      endAt: new Date(base.form.endAt).toISOString(),
      ruleTemplateId: base.form.ruleTemplateId,
      selectionMode: base.form.selectionMode,
      separateStudentCategories: base.form.separateStudentCategories,
    }
    activeDraftCode.value = request.batchCode
    publishFlowState.value = 'CREATING_DRAFT'
    try {
      const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/batches', request)
      const created = (response.data.data ?? {}) as DataObject
      const id = Number(created.id)
      if (!Number.isInteger(id) || id <= 0) {
        throw new Error('批次创建成功但服务端未返回有效批次编号。')
      }
      activeDraftId.value = id
      await base.load()
      const batch = findBatch(id) ?? draftFromRequest(id, request, created)
      clearCreateForm()
      if (intent === 'publish') {
        publishFlowState.value = 'IDLE'
        await base.openScope(batch, true)
      } else {
        publishFlowState.value = 'SUCCEEDED'
        base.message.value = `批次“${request.batchName}”已创建为草稿。`
      }
    } catch (reason) {
      const recovered = await recoverCreatedDraft(request.batchCode)
      if (recovered) {
        activeDraftId.value = Number(recovered.id)
        clearCreateForm()
        if (intent === 'publish') {
          publishFlowState.value = 'IDLE'
          await base.openScope(recovered, true)
        } else {
          publishFlowState.value = 'SUCCEEDED'
          base.message.value = '请求响应中断，但已确认草稿在服务端创建成功。'
        }
        return
      }
      fail(messageOf(reason, '批次创建失败'))
    }
  }

  async function saveScope() {
    if (base.publishAfterScope.value) {
      await saveScopeAndContinuePublish()
    } else {
      await saveScopeOnly()
    }
  }

  async function saveScopeOnly() {
    const batch = base.scopeBatch.value
    if (!batch || publishFlowBusy.value) return
    publishFlowState.value = 'SAVING_SCOPE'
    base.scopeSaving.value = true
    base.error.value = ''
    try {
      await persistScope(batch)
      base.message.value = `已保存${base.selectedStudentIds.value.length}名学生和${base.selectedRoomIds.value.length}间宿舍。`
      base.resetScopeDialog()
      await base.load()
      publishFlowState.value = 'SUCCEEDED'
    } catch (reason) {
      fail(messageOf(reason, '批次范围保存失败'))
    } finally {
      base.scopeSaving.value = false
    }
  }

  async function saveScopeAndContinuePublish() {
    const batch = base.scopeBatch.value
    if (!batch || publishFlowBusy.value) return
    const validation = validateScope()
    if (validation) {
      fail(validation, false)
      return
    }

    publishFlowState.value = 'SAVING_SCOPE'
    base.scopeSaving.value = true
    base.error.value = ''
    publishFlowError.value = ''
    try {
      await persistScope(batch)
      const refreshedBatch = {
        ...batch,
        eligible_count: base.selectedStudentIds.value.length,
      }
      publishFlowState.value = 'RUNNING_PREFLIGHT'
      const snapshot = await runPreflight(refreshedBatch)
      if (!snapshot) return
      base.resetScopeDialog()
      await nextTick()
      base.publishPreflightSnapshot.value = snapshot
      base.publishConfirmation.value = refreshedBatch
      publishFlowState.value = 'WAITING_CONFIRMATION'
    } catch (reason) {
      fail(messageOf(reason, '保存范围或发布预检失败'))
    } finally {
      base.scopeSaving.value = false
    }
  }

  async function preparePublish(batch: DataObject) {
    if (publishFlowBusy.value || String(batch.batch_status) === 'PUBLISHED') return
    base.error.value = ''
    publishFlowError.value = ''
    if (Number(batch.eligible_count ?? 0) === 0) {
      publishFlowState.value = 'IDLE'
      await base.openScope(batch, true)
      return
    }
    publishFlowState.value = 'RUNNING_PREFLIGHT'
    try {
      const snapshot = await runPreflight(batch)
      if (!snapshot) return
      await nextTick()
      base.publishPreflightSnapshot.value = snapshot
      base.publishConfirmation.value = batch
      publishFlowState.value = 'WAITING_CONFIRMATION'
    } catch (reason) {
      fail(messageOf(reason, '批次发布预检失败'))
    }
  }

  async function runPreflight(batch: DataObject): Promise<DataObject | null> {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/admin/batches/${Number(batch.id)}/room-preflight`,
    )
    const snapshot = (response.data.data ?? {}) as DataObject
    base.roomPreflight.value = snapshot
    base.preflightBatch.value = batch
    if (Number(snapshot.roomCount ?? 0) === 0) {
      fail('尚未选择可选宿舍。请在当前参与范围窗口内补齐宿舍范围。', false)
      return null
    }
    if (!Boolean(snapshot.publishable)) {
      fail('发布前检查未通过。预检窗口已列出阻断宿舍和处理要求。', false)
      if (base.scopeDialog.value) {
        base.resetScopeDialog()
        await nextTick()
      }
      return null
    }
    base.preflightBatch.value = null
    base.roomPreflight.value = null
    return { ...snapshot }
  }

  async function confirmPublish() {
    const batch = base.publishConfirmation.value
    if (!batch || publishFlowState.value === 'PUBLISHING') return
    publishFlowState.value = 'PUBLISHING'
    base.publishing.value = true
    base.error.value = ''
    publishFlowError.value = ''
    try {
      await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/PUBLISHED`)
      await finishPublished(batch)
    } catch (reason) {
      await base.load()
      const actual = findBatch(Number(batch.id))
      if (String(actual?.batch_status) === 'PUBLISHED') {
        base.message.value = '发布响应中断，但已确认服务端批次发布成功。'
        await finishPublished(actual)
        return
      }
      const text = messageOf(reason, '批次发布失败')
      fail(text)
      throw new Error(text)
    } finally {
      base.publishing.value = false
    }
  }

  async function finishPublished(batch: DataObject) {
    base.publishConfirmation.value = null
    base.publishPreflightSnapshot.value = null
    base.preflightBatch.value = null
    base.roomPreflight.value = null
    publishFlowState.value = 'SUCCEEDED'
    base.message.value ||= `批次“${batch.batch_name ?? batch.batchName}”已发布。`
    await base.load()
  }

  function closePublishConfirmation() {
    if (publishFlowState.value === 'PUBLISHING') return
    base.publishConfirmation.value = null
    base.publishPreflightSnapshot.value = null
    publishFlowState.value = 'IDLE'
  }

  async function persistScope(batch: DataObject) {
    await api.put(`/api/v1/admin/batches/${Number(batch.id)}/scope`, {
      studentIds: base.selectedStudentIds.value,
      roomIds: base.selectedRoomIds.value,
    })
  }

  function validateScope() {
    if (base.selectedStudentIds.value.length === 0) return '发布前至少选择一名参与学生。'
    if (base.selectedRoomIds.value.length === 0) return '发布前至少选择一间可选宿舍。'
    return ''
  }

  async function recoverCreatedDraft(batchCode: string) {
    try {
      await base.load()
      return base.batches.value.find((item) =>
        String(item.batch_code) === batchCode && String(item.batch_status) === 'DRAFT',
      ) ?? null
    } catch {
      return null
    }
  }

  function findBatch(id: number) {
    return base.batches.value.find((item) => Number(item.id) === id) ?? null
  }

  function draftFromRequest(id: number, request: DataObject, created: DataObject) {
    return {
      id,
      batch_code: request.batchCode,
      batch_name: request.batchName,
      batch_status: 'DRAFT',
      selection_mode: request.selectionMode,
      separate_student_categories: request.separateStudentCategories,
      start_at: request.startAt,
      end_at: request.endAt,
      rule_template_id: request.ruleTemplateId,
      eligible_count: 0,
      ...created,
    }
  }

  function clearCreateForm() {
    base.form.batchCode = ''
    base.form.batchName = ''
  }

  function fail(text: string, closeConfirmation = true) {
    publishFlowState.value = 'FAILED'
    publishFlowError.value = text
    base.error.value = text
    if (closeConfirmation) {
      base.publishConfirmation.value = null
      base.publishPreflightSnapshot.value = null
    }
  }

  function messageOf(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
  }

  function formatRange(start: unknown, end: unknown) {
    if (!start || !end) return '开放时间待确认'
    try {
      return `${new Date(String(start)).toLocaleString('zh-CN', { hour12: false })} 至 ${new Date(String(end)).toLocaleString('zh-CN', { hour12: false })}`
    } catch {
      return `${start} 至 ${end}`
    }
  }

  return {
    ...base,
    PublishFlowState: null as unknown as PublishFlowState,
    publishFlowState,
    publishFlowError,
    publishFlowBusy,
    activeDraftId,
    activeDraftCode,
    publishConfirmationFacts,
    missingPublishSteps,
    createBatch,
    saveScope,
    saveScopeOnly,
    saveScopeAndContinuePublish,
    preparePublish,
    confirmPublish,
    closePublishConfirmation,
  }
}
