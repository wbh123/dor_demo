<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const policy = reactive({
  allowWithoutQuestionnaire: false,
  allowStudentReselect: false,
  directPreferenceWithoutBatchAllowed: true,
  questionnaireBypassFeatureEnabled: false,
  studentReselectFeatureEnabled: false,
  version: 0,
  reason: '',
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/settings/selection-policy')
    const data = (response.data.data ?? {}) as DataObject
    policy.allowWithoutQuestionnaire = Boolean(data.allowWithoutQuestionnaire)
    policy.allowStudentReselect = Boolean(data.allowStudentReselect)
    policy.directPreferenceWithoutBatchAllowed = Boolean(data.directPreferenceWithoutBatchAllowed ?? true)
    policy.questionnaireBypassFeatureEnabled = Boolean(data.questionnaireBypassFeatureEnabled)
    policy.studentReselectFeatureEnabled = Boolean(data.studentReselectFeatureEnabled)
    policy.version = Number(data.version ?? 0)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '选寝策略加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!policy.reason.trim() || saving.value) {
    error.value = '请填写策略修改原因。'
    return
  }
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/selection-policy', {
      allowWithoutQuestionnaire: policy.allowWithoutQuestionnaire,
      allowStudentReselect: policy.allowStudentReselect,
      directPreferenceWithoutBatchAllowed: policy.directPreferenceWithoutBatchAllowed,
      reason: policy.reason.trim(),
    })
    const data = (response.data.data ?? {}) as DataObject
    policy.version = Number(data.version ?? policy.version + 1)
    policy.reason = ''
    message.value = '个人偏好与选寝行为策略已保存。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '选寝策略保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="content-column narrow">
    <header class="page-title"><span class="eyebrow">PREFERENCE ACCESS POLICY</span><h2>个人偏好开放策略</h2><p>控制无批次学生能否提前设置偏好，以及未填写偏好和已选结果的处理方式。</p></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="loading" class="panel empty-state">正在加载策略…</p>
    <section v-else class="panel policy-card">
      <label class="policy-option">
        <input v-model="policy.directPreferenceWithoutBatchAllowed" type="checkbox">
        <span><strong>开放无批次直接设置个人偏好</strong><small>学生没有被任何当前选寝批次包含时，仍可维护跨批次复用的个人偏好。默认开放。</small></span>
      </label>
      <label class="policy-option" :class="{disabled:!policy.questionnaireBypassFeatureEnabled}">
        <input v-model="policy.allowWithoutQuestionnaire" type="checkbox" :disabled="!policy.questionnaireBypassFeatureEnabled">
        <span><strong>允许未填写偏好直接选寝</strong><small>此开关同时受系统管理员功能授权控制。</small></span>
      </label>
      <label class="policy-option" :class="{disabled:!policy.studentReselectFeatureEnabled}">
        <input v-model="policy.allowStudentReselect" type="checkbox" :disabled="!policy.studentReselectFeatureEnabled">
        <span><strong>允许学生取消已有结果并重新选择</strong><small>只对仍处于开放状态的选寝批次生效。</small></span>
      </label>
      <label class="form-stack"><span>策略修改原因</span><textarea v-model="policy.reason" class="input" rows="3" maxlength="500" placeholder="必填，将写入操作审计" /></label>
      <div class="button-row"><button class="button primary" :disabled="saving" @click="save">{{ saving ? '正在保存…' : '保存策略' }}</button></div>
    </section>
  </div>
</template>

<style scoped>
.policy-card{display:grid;gap:14px}.policy-option{display:flex;align-items:flex-start;gap:12px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.policy-option input{width:18px;height:18px;margin-top:2px}.policy-option span{display:grid;gap:5px}.policy-option small{color:var(--muted);line-height:1.55}.policy-option.disabled{opacity:.58}.button-row{justify-content:flex-end}
</style>
