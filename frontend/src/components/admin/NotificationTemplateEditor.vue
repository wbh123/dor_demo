<script setup lang="ts">
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

const props = defineProps<{ modelValue: NotificationTemplateDraft; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: NotificationTemplateDraft]; save: [] }>()
const variables = ['studentName','studentNumber','batchName','buildingName','roomNumber','bedCode','openAt','closeAt','actionUrl']

function update<K extends keyof NotificationTemplateDraft>(key: K, value: NotificationTemplateDraft[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function insert(target: 'contentZhCn'|'contentEnUs', name: string) {
  update(target, `${props.modelValue[target]}{{${name}}}`)
}
</script>

<template>
  <div class="notification-template-editor">
    <div class="form-grid two-column">
      <label><span>模板编号</span><input class="input" :value="modelValue.templateCode" @input="update('templateCode', ($event.target as HTMLInputElement).value)" /></label>
      <label><span>模板名称</span><input class="input" :value="modelValue.templateName" @input="update('templateName', ($event.target as HTMLInputElement).value)" /></label>
    </div>
    <div class="variable-toolbar"><strong>可用变量</strong><button v-for="name in variables" :key="name" class="button ghost small" type="button" @click="insert('contentZhCn', name)">{{ name }}</button></div>
    <div class="language-grid">
      <section><header><strong>汉语</strong><span>zh-CN</span></header><input class="input" :value="modelValue.titleZhCn" placeholder="中文标题" @input="update('titleZhCn', ($event.target as HTMLInputElement).value)" /><textarea class="input" rows="7" :value="modelValue.contentZhCn" placeholder="中文内容" @input="update('contentZhCn', ($event.target as HTMLTextAreaElement).value)" /></section>
      <section><header><strong>英语</strong><span>en-US</span></header><input class="input" :value="modelValue.titleEnUs" placeholder="English title" @input="update('titleEnUs', ($event.target as HTMLInputElement).value)" /><textarea class="input" rows="7" :value="modelValue.contentEnUs" placeholder="English content" @input="update('contentEnUs', ($event.target as HTMLTextAreaElement).value)" /></section>
    </div>
    <label><span>创建原因</span><textarea class="input" rows="3" :value="modelValue.creationReason" @input="update('creationReason', ($event.target as HTMLTextAreaElement).value)" /></label>
    <label class="inline-check"><input type="checkbox" :checked="modelValue.enabled" @change="update('enabled', ($event.target as HTMLInputElement).checked)" /><span>启用该修订</span></label>
    <div class="button-row"><button class="button primary" type="button" :disabled="busy" @click="emit('save')">{{ busy ? '保存中…' : '保存不可变修订' }}</button></div>
  </div>
</template>

<style scoped>
.notification-template-editor{display:grid;gap:14px}.variable-toolbar{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.language-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.language-grid section{display:grid;gap:10px;padding:14px;border:1px solid var(--border);border-radius:14px}.language-grid header{display:flex;justify-content:space-between}.language-grid header span{color:var(--text-muted);font-size:12px}.notification-template-editor>label{display:grid;gap:7px}.inline-check{display:flex!important;align-items:center;gap:8px}@media(max-width:760px){.language-grid{grid-template-columns:1fr}}
</style>
