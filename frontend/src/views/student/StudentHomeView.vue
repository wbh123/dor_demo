<script setup lang="ts">
import { computed, ref } from 'vue'
import StudentHomeContent from './StudentHomeContent.vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const contentKey = ref(0)
const showPhoneDialog = ref(false)
const phoneSaving = ref(false)
const phoneError = ref('')
const countryCallingCode = ref('+86')
const nationalNumber = ref('')
const { translateError } = useI18n()

const callingCodeOptions = [
  { value: '+86', label: '中国大陆 +86' },
  { value: '+852', label: '中国香港 +852' },
  { value: '+853', label: '中国澳门 +853' },
  { value: '+886', label: '中国台湾 +886' },
  { value: '+81', label: '日本 +81' },
  { value: '+82', label: '韩国 +82' },
  { value: '+65', label: '新加坡 +65' },
  { value: '+60', label: '马来西亚 +60' },
  { value: '+66', label: '泰国 +66' },
  { value: '+84', label: '越南 +84' },
  { value: '+91', label: '印度 +91' },
  { value: '+92', label: '巴基斯坦 +92' },
  { value: '+7', label: '俄罗斯/哈萨克斯坦 +7' },
  { value: '+1', label: '美国/加拿大 +1' },
  { value: '+44', label: '英国 +44' },
  { value: '+33', label: '法国 +33' },
  { value: '+49', label: '德国 +49' },
  { value: '+61', label: '澳大利亚 +61' },
  { value: '+64', label: '新西兰 +64' },
]

const normalizedPreview = computed(() => {
  const digits = nationalNumber.value.replace(/\D/g, '').replace(/^0+/, '')
  return digits ? `${countryCallingCode.value}${digits}` : countryCallingCode.value
})

async function openPhoneEditor() {
  phoneError.value = ''
  nationalNumber.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/student/profile')
    const profile = (response.data.data ?? {}) as DataObject
    splitExistingPhone(String(profile.phone_number ?? ''))
  } catch {
    countryCallingCode.value = '+86'
  }
  showPhoneDialog.value = true
}

function splitExistingPhone(phone: string) {
  const compact = phone.replace(/[\s-]/g, '')
  const option = [...callingCodeOptions]
    .sort((left, right) => right.value.length - left.value.length)
    .find((item) => compact.startsWith(item.value))
  countryCallingCode.value = option?.value ?? '+86'
  nationalNumber.value = option ? compact.slice(option.value.length) : compact.replace(/^\+/, '')
}

async function savePhoneNumber() {
  phoneError.value = ''
  const digits = nationalNumber.value.replace(/\D/g, '').replace(/^0+/, '')
  if (digits.length < 5 || digits.length > 20) {
    phoneError.value = '请输入5至20位本地手机号码。'
    return
  }
  phoneSaving.value = true
  try {
    await api.put('/api/v1/student/profile', {
      countryCallingCode: countryCallingCode.value,
      nationalNumber: digits,
      phoneNumber: `${countryCallingCode.value}${digits}`,
    })
    showPhoneDialog.value = false
    contentKey.value += 1
  } catch (reason) {
    phoneError.value = translateError(reason)
  } finally {
    phoneSaving.value = false
  }
}
</script>

<template>
  <div class="student-home-wrapper">
    <StudentHomeContent :key="contentKey" />
    <button class="phone-edit-fab" type="button" @click="openPhoneEditor">修改手机号码</button>

    <div v-if="showPhoneDialog" class="modal-overlay phone-modal-overlay" role="presentation" @click.self="showPhoneDialog=false">
      <section class="modal-card phone-modal-card" role="dialog" aria-modal="true" aria-labelledby="phone-dialog-title">
        <header class="phone-modal-header">
          <div><span class="eyebrow">联系方式</span><h3 id="phone-dialog-title">修改手机号码</h3><p>先选择国家或地区码，再填写不含区号的本地号码。</p></div>
          <button class="modal-close" type="button" aria-label="关闭" @click="showPhoneDialog=false">×</button>
        </header>
        <div class="phone-field-grid">
          <label><span>国家或地区码</span><select v-model="countryCallingCode" class="input"><option v-for="option in callingCodeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label><span>本地手机号码</span><input v-model.trim="nationalNumber" class="input" maxlength="24" inputmode="tel" placeholder="例如 13800000000"></label>
        </div>
        <p class="phone-preview">保存格式：{{ normalizedPreview }}</p>
        <p v-if="phoneError" class="alert error">{{ phoneError }}</p>
        <div class="button-row phone-modal-actions"><button class="button ghost" type="button" @click="showPhoneDialog=false">取消</button><button class="button primary" :disabled="phoneSaving" @click="savePhoneNumber">{{ phoneSaving ? '正在保存…' : '保存手机号码' }}</button></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.student-home-wrapper{position:relative}.student-home-wrapper :deep(.light-text-button),.student-home-wrapper :deep(.phone-editor-dialog){display:none!important}.phone-edit-fab{position:fixed;right:24px;bottom:24px;z-index:90;padding:12px 18px;border:0;border-radius:999px;color:#fff;background:var(--primary);box-shadow:0 12px 30px rgba(30,78,140,.28);cursor:pointer}.phone-modal-overlay{z-index:1400;padding:20px;background:rgba(10,24,49,.72);backdrop-filter:blur(8px)}.phone-modal-card{width:min(620px,100%);padding:26px;border-radius:26px;background:var(--panel,#fff);box-shadow:0 28px 80px rgba(8,25,53,.28)}.phone-modal-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.phone-modal-header h3{margin:5px 0}.phone-modal-header p{margin:0;color:var(--muted)}.phone-field-grid{display:grid;grid-template-columns:minmax(190px,.8fr) minmax(240px,1.2fr);gap:14px;margin-top:20px}.phone-field-grid label{display:grid;gap:7px}.phone-preview{padding:10px 12px;border-radius:12px;color:var(--muted);background:var(--soft)}.phone-modal-actions{justify-content:flex-end;margin-top:18px}@media(max-width:640px){.phone-edit-fab{right:14px;bottom:14px}.phone-modal-overlay{align-items:flex-end;padding:10px}.phone-modal-card{padding:20px;border-radius:24px 24px 16px 16px}.phone-field-grid{grid-template-columns:1fr}}
</style>
