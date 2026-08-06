<script setup lang="ts">
// @ts-nocheck
import { ref } from 'vue'
import BatchAllocationPreviewDialog from '../../features/admin-batch/components/BatchAllocationPreviewDialog.vue'
import BatchCopyDialog from '../../features/admin-batch/components/BatchCopyDialog.vue'
import BatchCreationPanel from '../../features/admin-batch/components/BatchCreationPanel.vue'
import BatchListPanel from '../../features/admin-batch/components/BatchListPanel.vue'
import BatchPreflightDialog from '../../features/admin-batch/components/BatchPreflightDialog.vue'
import BatchPublishConfirmationDialog from '../../features/admin-batch/components/BatchPublishConfirmationDialog.vue'
import BatchScopeDialog from '../../features/admin-batch/components/BatchScopeDialog.vue'
import { useAdminBatchView } from './AdminBatchView.logic'

const {
  batches,
  ruleTemplates,
  error,
  message,
  allocationPreview,
  allocationBatchId,
  allocationSummary,
  unassignedStudents,
  roomPreflight,
  preflightBatch,
  preflightRooms,
  preflightBlockers,
  preflightMissingSteps,
  copyDialog,
  copying,
  copySource,
  copyForm,
  scopeDialog,
  scopeLoading,
  scopeSaving,
  scopeBatch,
  selectedStudentIds,
  selectedRoomIds,
  studentFilter,
  studentGenderFilter,
  studentCategoryFilter,
  studentDegreeFilter,
  studentMajorFilter,
  studentGradeFilter,
  roomFilter,
  roomGenderFilter,
  roomScopeFilter,
  roomBuildingFilter,
  roomFloorFilter,
  publishAfterScope,
  publishConfirmation,
  publishPreflightSnapshot,
  publishFlowState,
  publishFlowBusy,
  creatingDraft,
  runningPreflight,
  publishing,
  form,
  bedModeAuthorized,
  ruleTemplateSummary,
  scopeMajorOptions,
  scopeGradeOptions,
  scopeBuildingOptions,
  scopeFloorOptions,
  filteredStudents,
  filteredRooms,
  createBatch,
  openScope,
  closeScope,
  toggleStudent,
  toggleRoom,
  selectAllStudents,
  selectAllRooms,
  saveScope,
  saveScopeAndContinuePublish,
  preflight,
  closePreflight,
  reopenScopeFromPreflight,
  closePublishConfirmation,
  confirmPublish,
  changeStatus,
  openCopy,
  closeCopy,
  copyBatch,
  previewAllocation,
  commitAllocation: commitAllocationRequest,
  download,
  nextActions,
  modeText,
  statusText,
  actionText,
  issueText,
  formatDateTime,
  batchRuleSummary,
} = useAdminBatchView()

const allocationCommitting = ref(false)

async function commitAllocation() {
  if (allocationCommitting.value) return
  allocationCommitting.value = true
  try {
    await commitAllocationRequest()
  } finally {
    allocationCommitting.value = false
  }
}
</script>

<template src="./AdminBatchView.template.html"></template>

<style scoped src="./AdminBatchView.css"></style>
