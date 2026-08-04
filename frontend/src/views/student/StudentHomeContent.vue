<script setup lang="ts">
import PhoneDialCodeSelect from '../../components/common/PhoneDialCodeSelect.vue'
import { useStudentHomeContent } from './StudentHomeContent.logic'

const {
  profile,
  currentActivity,
  questionnaire,
  assignmentResult,
  invitations,
  notifications,
  dismissedInvitationTokens,
  loading,
  invitationSubmitting,
  phoneSaving,
  showPhoneDialog,
  phoneDialCode,
  phoneLocalNumber,
  error,
  phoneError,
  hasFeature,
  questions,
  profileAnswers,
  profileAnswerEntries,
  questionnaireStarted,
  canEditQuestionnaire,
  profileInsightEnabled,
  canSelectRoom,
  assigned,
  assignment,
  currentActivityId,
  isForeignStudent,
  homeInvitation,
  unreadNotifications,
  answerSummary,
  rawAnswerByCode,
  preferenceProfileSummary,
  preferenceProfileTags,
  load,
  loadGlobalPreferences,
  loadAssignment,
  dismissHomeInvitation,
  respondHomeInvitation,
  markNotificationRead,
  openPhoneEditor,
  savePhoneNumber,
  notificationParameters,
  notificationTitle,
  notificationMessage,
  chooseCurrentActivity,
  activityId,
  parseAnswer,
  numericAnswer,
  timeMinutes,
  local,
  summaryLabel,
  displayAnswer,
  bedTypeText
} = useStudentHomeContent()
</script>

<template src="./StudentHomeContent.template.html"></template>

<style scoped src="./StudentHomeContent.css"></style>
