// @ts-nocheck
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { isPublishFlowBusy, transitionPublishFlow, type PublishFlowState } from './batchPublishFlow'

export function useAdminBatchView() {
  const { hasFeature } = useFeatureAccess()
  const batches = ref<DataObject[]>([])
  const ruleTemplates = ref<DataObject[]>([])
  const error = ref('')
  const message = ref('')
  const allocationPreview = ref<DataObject | null>(null)
  const allocationBatchId = ref<number | null>(null)
  const roomPreflight = ref<DataObject | null>(null)
  const preflightBatch = ref<DataObject | null>(null)
  const copyDialog = ref(false)
  const copying = ref(false)
  const copySource = ref<DataObject | null>(null)
  const scopeDialog = ref(false)
  const scopeLoading = ref(false)
  const scopeBatch = ref<DataObject | null>(null)
  const scopeData = ref<DataObject | null>(null)
  const selectedStudentIds = ref<number[]>([])
  const selectedRoomIds = ref<number[]>([])
  const studentFilter = ref('')
  const studentGenderFilter = ref('')
  const studentCategoryFilter = ref('')
  const studentDegreeFilter = ref('')
  const studentMajorFilter = ref('')
  const studentGradeFilter = ref('')
  const roomFilter = ref('')
  const roomGenderFilter = ref('')
  const roomScopeFilter = ref('')
  const roomBuildingFilter = ref('')
  const roomFloorFilter = ref('')
  const publishAfterScope = ref(false)
  const publishConfirmation = ref<DataObject | null>(null)
  const publishPreflightSnapshot = ref<DataObject | null>(null)
  const publishFlowState = ref<PublishFlowState>('IDLE')

  const form = reactive({
    batchCode: '', batchName: '', startAt: '', endAt: '', ruleTemplateId: 0,
    selectionMode: 'ROOM' as 'ROOM' | 'BED', separateStudentCategories: false,
  })
  const copyForm = reactive({ batchCode: '', batchName: '', startAt: '', endAt: '', reason: '' })

  const publishFlowBusy = computed(() => isPublishFlowBusy(publishFlowState.value))
  const creatingDraft = computed(() => publishFlowState.value === 'CREATING_DRAFT')
  const scopeSaving = computed(() => publishFlowState.value === 'SAVING_SCOPE')
  const runningPreflight = computed(() => publishFlowState.value === 'RUNNING_PREFLIGHT')
  const publishing = computed(() => publishFlowState.value === 'PUBLISHING')
  const bedModeAuthorized = computed(() => hasFeature('P2_BED_SELECTION_MODE'))
  const selectedRuleTemplate = computed(() => ruleTemplates.value.find((item) => Number(item.id) === Number(form.ruleTemplateId)) ?? null)
  const ruleTemplateSummary = computed(() => {
    const item = selectedRuleTemplate.value
    if (!item) return '请选择规则模板'
    const team = item.allow_team ? `允许${item.team_min_size}—${item.team_max_size}人组队` : '不允许组队'
    return `${team}；临时占用${item.hold_duration_seconds}秒；${item.allow_student_random ? '允许' : '不允许'}随机推荐。`
  })
  const preflightRooms = computed(() => (roomPreflight.value?.rooms ?? []) as DataObject[])
  const preflightBlockers = computed(() => (roomPreflight.value?.blockers ?? []) as DataObject[])
  const preflightMissingSteps = computed(() => (roomPreflight.value?.missingSteps ?? []) as string[])
  const allocationSummary = computed(() => (allocationPreview.value?.summary ?? {}) as DataObject)
  const unassignedStudents = computed(() => (allocationPreview.value?.unassigned ?? []) as DataObject[])
  const scopeStudents = computed(() => (scopeData.value?.students ?? []) as DataObject[])
  const scopeRooms = computed(() => (scopeData.value?.rooms ?? []) as DataObject[])
  const scopeMajorOptions = computed(() => [...new Map(scopeStudents.value.map((student) => [String(student.major_id), { id: String(student.major_id), label: `${student.major_code} · ${student.major_name}` }])).values()])
  const scopeGradeOptions = computed(() => [...new Set(scopeStudents.value.map((student) => String(student.grade_year ?? '')).filter(Boolean))].sort())
  const scopeBuildingOptions = computed(() => [...new Map(scopeRooms.value.map((room) => [String(room.building_id), { id: String(room.building_id), label: String(room.building_name) }])).values()])
  const scopeFloorOptions = computed(() => [...new Set(scopeRooms.value.map((room) => String(room.floor_number ?? '')).filter(Boolean))].sort((a, b) => Number(a) - Number(b)))

  const filteredStudents = computed(() => {
    const keyword = studentFilter.value.trim().toLowerCase()
    return scopeStudents.value.filter((student) => {
      if (keyword && ![student.student_number, student.student_name, student.major_name].some((value) => String(value ?? '').toLowerCase().includes(keyword))) return false
      if (studentGenderFilter.value && String(student.gender) !== studentGenderFilter.value) return false
      if (studentCategoryFilter.value && String(student.student_category) !== studentCategoryFilter.value) return false
      if (studentDegreeFilter.value && String(student.degree_level ?? '') !== studentDegreeFilter.value) return false
      if (studentMajorFilter.value && String(student.major_id) !== studentMajorFilter.value) return false
      if (studentGradeFilter.value && String(student.grade_year ?? '') !== studentGradeFilter.value) return false
      return true
    })
  })
  const filteredRooms = computed(() => {
    const keyword = roomFilter.value.trim().toLowerCase()
    return scopeRooms.value.filter((room) => {
      if (keyword && ![room.building_code, room.building_name, room.room_number, room.floor_number].some((value) => String(value ?? '').toLowerCase().includes(keyword))) return false
      if (roomGenderFilter.value && String(room.gender_restriction) !== roomGenderFilter.value) return false
      if (roomScopeFilter.value && String(room.resident_scope) !== roomScopeFilter.value) return false
      if (roomBuildingFilter.value && String(room.building_id) !== roomBuildingFilter.value) return false
      if (roomFloorFilter.value && String(room.floor_number) !== roomFloorFilter.value) return false
      return true
    })
  })

  onMounted(load)
  function movePublishFlow(next: PublishFlowState) { publishFlowState.value = transitionPublishFlow(publishFlowState.value, next) }
  function recoverPublishFlow() { if (publishFlowState.value === 'FAILED' || publishFlowState.value === 'SUCCEEDED') movePublishFlow('IDLE') }

  async function load() {
    try {
      const [batchResponse, templateResponse] = await Promise.all([
        api.get<ListSuccessResponse>('/api/v1/admin/batches'),
        api.get<ListSuccessResponse>('/api/v1/admin/batch-rule-templates'),
      ])
      batches.value = (batchResponse.data.data ?? []) as DataObject[]
      ruleTemplates.value = ((templateResponse.data.data ?? []) as DataObject[]).filter((item) => Boolean(item.enabled))
      if (!ruleTemplates.value.some((item) => Number(item.id) === form.ruleTemplateId)) {
        const defaultTemplate = ruleTemplates.value.find((item) => Boolean(item.is_default)) ?? ruleTemplates.value[0]
        form.ruleTemplateId = Number(defaultTemplate?.id ?? 0)
      }
      if (!bedModeAuthorized.value && form.selectionMode === 'BED') form.selectionMode = 'ROOM'
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '批次加载失败'
      throw reason
    }
  }

  async function createBatch() {
    if (publishFlowBusy.value) return
    recoverPublishFlow(); error.value = ''; message.value = ''
    if (form.selectionMode === 'BED' && !bedModeAuthorized.value) { error.value = '当前服务未开放选择床位模式。'; return }
    const submitted = { ...form }
    movePublishFlow('CREATING_DRAFT')
    let createdBatch: DataObject | null = null
    try {
      const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/batches', {
        batchCode: submitted.batchCode, batchName: submitted.batchName,
        startAt: new Date(submitted.startAt).toISOString(), endAt: new Date(submitted.endAt).toISOString(),
        ruleTemplateId: submitted.ruleTemplateId, selectionMode: submitted.selectionMode,
        separateStudentCategories: submitted.separateStudentCategories,
      })
      const created = (response.data.data ?? {}) as DataObject
      const batchId = Number(created.id)
      if (!Number.isInteger(batchId) || batchId <= 0) throw new Error('批次创建成功，但服务端没有返回有效批次编号。')
      createdBatch = {
        ...created, id: batchId, batch_code: submitted.batchCode, batch_name: submitted.batchName,
        batch_status: 'DRAFT', selection_mode: submitted.selectionMode,
        separate_student_categories: submitted.separateStudentCategories,
        start_at: submitted.startAt, end_at: submitted.endAt, eligible_count: 0,
      }
      form.batchCode = ''; form.batchName = ''; movePublishFlow('IDLE')
      await load()
      createdBatch = batches.value.find((item) => Number(item.id) === batchId) ?? createdBatch
      message.value = `草稿“${submitted.batchName}”已创建，请在当前窗口配置参与学生和宿舍。`
      await openScope(createdBatch)
    } catch (reason) {
      if (publishFlowState.value === 'CREATING_DRAFT') movePublishFlow('FAILED')
      error.value = reason instanceof Error ? reason.message : '批次创建失败'
      if (createdBatch) message.value = '草稿已经创建，范围加载失败时可从批次列表继续配置，不要重复创建。'
    }
  }

  async function openScope(batch: DataObject, continuePublish = false) {
    scopeDialog.value = true; scopeLoading.value = true; scopeBatch.value = batch; scopeData.value = null
    selectedStudentIds.value = []; selectedRoomIds.value = []; resetScopeFilters(); publishAfterScope.value = continuePublish; error.value = ''
    try {
      const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/scope`)
      const data = (response.data.data ?? {}) as DataObject
      scopeData.value = data
      selectedStudentIds.value = ((data.students ?? []) as DataObject[]).filter((student) => Boolean(student.selected)).map((student) => Number(student.id))
      selectedRoomIds.value = ((data.rooms ?? []) as DataObject[]).filter((room) => Boolean(room.selected) && Boolean(room.selectable)).map((room) => Number(room.id))
    } catch (reason) { error.value = reason instanceof Error ? reason.message : '批次范围加载失败'; resetScopeDialog() }
    finally { scopeLoading.value = false }
  }
  function resetScopeFilters() {
    studentFilter.value = ''; studentGenderFilter.value = ''; studentCategoryFilter.value = ''; studentDegreeFilter.value = ''; studentMajorFilter.value = ''; studentGradeFilter.value = ''
    roomFilter.value = ''; roomGenderFilter.value = ''; roomScopeFilter.value = ''; roomBuildingFilter.value = ''; roomFloorFilter.value = ''
  }
  function closeScope() { if (publishFlowBusy.value || publishFlowState.value === 'WAITING_CONFIRMATION') return; recoverPublishFlow(); resetScopeDialog() }
  function resetScopeDialog() { scopeDialog.value = false; scopeBatch.value = null; scopeData.value = null; publishAfterScope.value = false }
  function toggleStudent(id: number) { selectedStudentIds.value = toggleId(selectedStudentIds.value, id) }
  function toggleRoom(id: number) { selectedRoomIds.value = toggleId(selectedRoomIds.value, id) }
  function selectAllStudents() { selectedStudentIds.value = uniqueIds([...selectedStudentIds.value, ...filteredStudents.value.map((student) => Number(student.id))]) }
  function selectAllRooms() { selectedRoomIds.value = uniqueIds([...selectedRoomIds.value, ...filteredRooms.value.filter((room) => Boolean(room.selectable)).map((room) => Number(room.id))]) }
  function toggleId(values: number[], id: number) { return values.includes(id) ? values.filter((value) => value !== id) : [...values, id] }
  function uniqueIds(values: number[]) { return [...new Set(values.filter((value) => Number.isInteger(value) && value > 0))] }
  async function persistScope(batch: DataObject) { await api.put(`/api/v1/admin/batches/${Number(batch.id)}/scope`, { studentIds: selectedStudentIds.value, roomIds: selectedRoomIds.value }) }
  async function requestPreflight(batch: DataObject) { const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/room-preflight`); return (response.data.data ?? {}) as DataObject }
  function buildPreflightMissingSteps(snapshot: DataObject) {
    const steps: string[] = []
    if (Number(snapshot.roomCount ?? 0) === 0) steps.push('选择至少一间可用宿舍')
    if ((snapshot.blockers ?? []).length > 0) steps.push('处理阻断发布的宿舍状态或床位映射问题')
    if (!steps.length) steps.push('根据预检提示完成剩余发布准备')
    return steps
  }

  async function saveScope() {
    const batch = scopeBatch.value
    if (!batch || publishFlowBusy.value) return
    recoverPublishFlow(); movePublishFlow('SAVING_SCOPE'); error.value = ''
    try { await persistScope(batch); movePublishFlow('SUCCEEDED'); message.value = `已保存${selectedStudentIds.value.length}名学生和${selectedRoomIds.value.length}间宿舍。`; await load(); movePublishFlow('IDLE'); resetScopeDialog() }
    catch (reason) { movePublishFlow('FAILED'); error.value = reason instanceof Error ? reason.message : '批次范围保存失败' }
  }
  async function saveScopeAndContinuePublish() {
    const batch = scopeBatch.value
    if (!batch || publishFlowBusy.value) return
    recoverPublishFlow()
    const missingSteps: string[] = []
    if (selectedStudentIds.value.length === 0) missingSteps.push('至少选择一名参与学生')
    if (selectedRoomIds.value.length === 0) missingSteps.push('至少选择一间可选宿舍')
    if (missingSteps.length) {
      roomPreflight.value = { publishable: false, roomCount: selectedRoomIds.value.length, availableCapacity: 0, rooms: [], blockers: [], missingSteps, completedSteps: ['草稿已创建', '批次规则已绑定'] }
      preflightBatch.value = batch; movePublishFlow('FAILED'); return
    }
    movePublishFlow('SAVING_SCOPE'); error.value = ''
    try {
      await persistScope(batch)
      message.value = `范围已保存：${selectedStudentIds.value.length}名学生、${selectedRoomIds.value.length}间宿舍。`
      movePublishFlow('RUNNING_PREFLIGHT')
      const snapshot = await requestPreflight(batch)
      if (!Boolean(snapshot.publishable)) {
        roomPreflight.value = { ...snapshot, missingSteps: buildPreflightMissingSteps(snapshot), completedSteps: ['草稿已创建', '参与学生范围已保存', '宿舍范围已保存', '批次规则已绑定'] }
        preflightBatch.value = batch; movePublishFlow('FAILED'); return
      }
      await openPublishConfirmationAfterPreflight(batch, snapshot)
    } catch (reason) {
      if (publishFlowState.value === 'SAVING_SCOPE' || publishFlowState.value === 'RUNNING_PREFLIGHT') movePublishFlow('FAILED')
      error.value = reason instanceof Error ? reason.message : '保存范围并执行发布预检失败'
    }
  }
  async function preflight(batch: DataObject) {
    if (publishFlowBusy.value) return
    recoverPublishFlow(); error.value = ''; preflightBatch.value = batch; movePublishFlow('RUNNING_PREFLIGHT')
    try { roomPreflight.value = await requestPreflight(batch); movePublishFlow('IDLE') }
    catch (reason) { movePublishFlow('FAILED'); error.value = reason instanceof Error ? reason.message : '发布预检失败' }
  }
  function closePreflight() { if (runningPreflight.value) return; preflightBatch.value = null; roomPreflight.value = null; recoverPublishFlow() }
  async function reopenScopeFromPreflight() { const batch = preflightBatch.value; closePreflight(); if (scopeDialog.value) return; if (batch) await openScope(batch, true) }
  async function openPublishConfirmationAfterPreflight(batch: DataObject, snapshot: DataObject) { preflightBatch.value = null; roomPreflight.value = null; await nextTick(); publishPreflightSnapshot.value = snapshot; publishConfirmation.value = batch; movePublishFlow('WAITING_CONFIRMATION') }
  function closePublishConfirmation() { if (publishing.value) return; publishConfirmation.value = null; publishPreflightSnapshot.value = null; if (publishFlowState.value === 'WAITING_CONFIRMATION' || publishFlowState.value === 'FAILED') movePublishFlow('IDLE') }
  async function preparePublish(batch: DataObject) {
    if (publishFlowBusy.value) return
    recoverPublishFlow(); error.value = ''; publishConfirmation.value = null; publishPreflightSnapshot.value = null
    if (['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(batch.batch_status))) { message.value = '该批次已经发布，无需重复操作。'; return }
    if (Number(batch.eligible_count ?? 0) === 0) { await openScope(batch, true); return }
    movePublishFlow('RUNNING_PREFLIGHT')
    try {
      const snapshot = await requestPreflight(batch)
      if (Number(snapshot.roomCount ?? 0) === 0) { movePublishFlow('IDLE'); await openScope(batch, true); return }
      if (!Boolean(snapshot.publishable)) { roomPreflight.value = { ...snapshot, missingSteps: buildPreflightMissingSteps(snapshot), completedSteps: ['草稿已创建', '参与学生范围已保存', '宿舍范围已保存', '批次规则已绑定'] }; preflightBatch.value = batch; movePublishFlow('FAILED'); return }
      await openPublishConfirmationAfterPreflight(batch, snapshot)
    } catch (reason) { movePublishFlow('FAILED'); error.value = reason instanceof Error ? reason.message : '批次发布预检失败' }
  }
  async function confirmPublish() {
    const batch = publishConfirmation.value
    if (!batch || publishing.value) return
    movePublishFlow('PUBLISHING'); error.value = ''
    try { await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/PUBLISHED`); await completePublishedFlow(batch) }
    catch (reason) {
      if (await reconcilePublishedState(Number(batch.id))) { await completePublishedFlow(batch); return }
      movePublishFlow('FAILED'); const failure = reason instanceof Error ? reason : new Error('批次发布失败'); error.value = failure.message; throw failure
    }
  }
  async function reconcilePublishedState(batchId: number) { try { await load(); const actual = batches.value.find((item) => Number(item.id) === batchId); return Boolean(actual && ['PUBLISHED', 'OPEN', 'PAUSED'].includes(String(actual.batch_status))) } catch { return false } }
  async function completePublishedFlow(batch: DataObject) { movePublishFlow('SUCCEEDED'); message.value = `批次“${batch.batch_name}”已发布。`; publishConfirmation.value = null; publishPreflightSnapshot.value = null; roomPreflight.value = null; preflightBatch.value = null; resetScopeDialog(); await load(); movePublishFlow('IDLE') }
  async function changeStatus(batch: DataObject, target: string) { if (target === 'PUBLISHED') { await preparePublish(batch); return }; await run(async () => { await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/${target}`); message.value = `批次已切换为“${statusText(target)}”。`; roomPreflight.value = null; preflightBatch.value = null }) }
  function openCopy(batch: DataObject) { copySource.value = batch; copyDialog.value = true; Object.assign(copyForm, { batchCode: '', batchName: '', startAt: '', endAt: '', reason: '' }) }
  function closeCopy() { if (!copying.value) { copyDialog.value = false; copySource.value = null } }
  async function copyBatch() {
    if (!copySource.value) return
    copying.value = true; error.value = ''
    try { await api.post(`/api/v1/admin/batches/${Number(copySource.value.id)}/copy`, { batchCode: copyForm.batchCode, batchName: copyForm.batchName, startAt: new Date(copyForm.startAt).toISOString(), endAt: new Date(copyForm.endAt).toISOString(), reason: copyForm.reason }); message.value = `批次已复制为草稿，并保留${modeText(copySource.value.selection_mode)}与学生类别隔离设置。`; closeCopy(); await load() }
    catch (reason) { error.value = reason instanceof Error ? reason.message : '批次复制失败' }
    finally { copying.value = false }
  }
  async function previewAllocation(batch: DataObject) { try { const response = await api.get<ObjectSuccessResponse>(`/api/v1/admin/batches/${Number(batch.id)}/allocation/preview`, { params: { randomSeed: 20260801 } }); allocationPreview.value = (response.data.data ?? {}) as DataObject; allocationBatchId.value = Number(batch.id) } catch (reason) { error.value = reason instanceof Error ? reason.message : '分配预演失败' } }
  async function commitAllocation() {
    if (!allocationBatchId.value) return
    try { const response = await api.post<ObjectSuccessResponse>(`/api/v1/admin/batches/${allocationBatchId.value}/allocation/commit`, { randomSeed: 20260801, idempotencyKey: crypto.randomUUID() }); const result = (response.data.data ?? {}) as DataObject; allocationPreview.value = { summary: result.summary ?? {}, unassigned: result.unassigned ?? [] }; allocationBatchId.value = null; message.value = Number(((result.summary ?? {}) as DataObject).unassignedCount ?? 0) > 0 ? '统一分配已执行，仍有未分配学生需要处理。' : '统一分配已完成。'; await load() }
    catch (reason) { error.value = reason instanceof Error ? reason.message : '统一分配执行失败' }
  }
  async function download(batch: DataObject) { try { const response = await api.get(`/api/v1/admin/batches/${Number(batch.id)}/assignments.csv`, { responseType: 'blob' }); const url = URL.createObjectURL(response.data); const link = document.createElement('a'); link.href = url; link.download = `assignments-${batch.id}.csv`; link.click(); URL.revokeObjectURL(url) } catch (reason) { error.value = reason instanceof Error ? reason.message : '导出失败' } }
  async function run(action: () => Promise<void>) { error.value = ''; message.value = ''; try { await action(); await load() } catch (reason) { error.value = reason instanceof Error ? reason.message : '操作失败' } }
  function nextActions(status: unknown) { return ({ DRAFT: ['PUBLISHED', 'CANCELLED'], PUBLISHED: ['OPEN', 'CANCELLED'], OPEN: ['PAUSED', 'CLOSED'], PAUSED: ['OPEN', 'CLOSED'], CLOSED: ['ALLOCATING', 'FINISHED'], ALLOCATING: ['FINISHED', 'CLOSED'] } as Record<string, string[]>)[String(status)] ?? [] }
  function modeText(value: unknown) { return String(value) === 'BED' ? '选择床位' : '选择寝室' }
  function statusText(value: unknown) { return ({ DRAFT: '草稿', PUBLISHED: '已发布', OPEN: '选寝中', PAUSED: '已暂停', CLOSED: '已关闭', ALLOCATING: '分配中', FINISHED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[String(value)] ?? String(value) }
  function actionText(value: string) { return ({ PUBLISHED: '发布活动', OPEN: '开放选择', PAUSED: '暂停选择', CLOSED: '结束选择', ALLOCATING: '进入统一分配', FINISHED: '标记完成', CANCELLED: '取消批次' } as Record<string, string>)[value] ?? value }
  function issueText(room: DataObject) { return ((room.issues ?? []) as DataObject[]).map((item) => String(item.message)).join('；') }
  function formatDateTime(value: unknown) { if (!value) return '未设置'; const date = new Date(String(value)); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false }) }
  function batchRuleSummary(batch: DataObject) { const team = Boolean(batch.allow_team) ? `允许${batch.team_min_size ?? 2}—${batch.team_max_size ?? '-'}人组队` : '不允许组队'; const random = Boolean(batch.allow_student_random) ? '允许随机推荐' : '不允许随机推荐'; return `${team}；${random}；临时占用${batch.hold_duration_seconds ?? '-'}秒。` }

  return {
    hasFeature, batches, ruleTemplates, error, message, allocationPreview, allocationBatchId, allocationSummary, unassignedStudents,
    roomPreflight, preflightBatch, preflightRooms, preflightBlockers, preflightMissingSteps, copyDialog, copying, copySource, copyForm,
    scopeDialog, scopeLoading, scopeSaving, scopeBatch, scopeData, selectedStudentIds, selectedRoomIds, studentFilter, studentGenderFilter,
    studentCategoryFilter, studentDegreeFilter, studentMajorFilter, studentGradeFilter, roomFilter, roomGenderFilter, roomScopeFilter,
    roomBuildingFilter, roomFloorFilter, publishAfterScope, publishConfirmation, publishPreflightSnapshot, publishFlowState, publishFlowBusy,
    creatingDraft, runningPreflight, publishing, form, bedModeAuthorized, selectedRuleTemplate, ruleTemplateSummary, scopeStudents, scopeRooms,
    scopeMajorOptions, scopeGradeOptions, scopeBuildingOptions, scopeFloorOptions, filteredStudents, filteredRooms, load, createBatch, openScope,
    closeScope, resetScopeDialog, toggleStudent, toggleRoom, selectAllStudents, selectAllRooms, toggleId, uniqueIds, saveScope,
    saveScopeAndContinuePublish, preflight, closePreflight, reopenScopeFromPreflight, closePublishConfirmation, preparePublish, confirmPublish,
    reconcilePublishedState, changeStatus, openCopy, closeCopy, copyBatch, previewAllocation, commitAllocation, download, run, nextActions,
    modeText, statusText, actionText, issueText, formatDateTime, batchRuleSummary,
  }
}
