<script setup lang="ts">
import type { DataObject } from '../../../api/types'

export interface BatchCreationForm {
  batchCode: string
  batchName: string
  startAt: string
  endAt: string
  selectionMode: 'ROOM' | 'BED'
  separateStudentCategories: boolean
  ruleTemplateId: number
}

const props = defineProps<{
  form: BatchCreationForm
  ruleTemplates: DataObject[]
  bedModeAuthorized: boolean
  ruleTemplateSummary: string
  creating?: boolean
}>()

const emit = defineEmits<{
  'update:form': [value: BatchCreationForm]
  submit: []
}>()

function update<K extends keyof BatchCreationForm>(key: K, value: BatchCreationForm[K]) {
  emit('update:form', { ...props.form, [key]: value })
}
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <div><span class="eyebrow">NEW BATCH</span><h3>创建选寝批次</h3></div>
    </div>
    <form class="batch-create-form" @submit.prevent="emit('submit')">
      <div class="mode-card-grid">
        <button
          type="button"
          class="mode-card"
          :class="{ selected: form.selectionMode === 'ROOM' }"
          @click="update('selectionMode', 'ROOM')"
        >
          <strong>选择寝室</strong><span>学生只确定寝室归属，具体床位由入住成员自行商议。</span><small>基础模式</small>
        </button>
        <button
          type="button"
          class="mode-card"
          :class="{ selected: form.selectionMode === 'BED', disabled: !bedModeAuthorized }"
          :disabled="!bedModeAuthorized"
          @click="update('selectionMode', 'BED')"
        >
          <strong>选择床位</strong><span>学生进入寝室后选择系统确认真实空闲的具体床位。</span><small>{{ bedModeAuthorized ? '已授权' : '当前服务未开通' }}</small>
        </button>
      </div>
      <div class="separation-switch">
        <button
          type="button"
          role="switch"
          :aria-checked="form.separateStudentCategories"
          :class="{ checked: form.separateStudentCategories }"
          @click="update('separateStudentCategories', !form.separateStudentCategories)"
        ><span /></button>
        <div><strong>国内生与国际生分开选寝</strong><p>开启后仅允许国内生使用国内生专用宿舍、国际生使用国际生专用宿舍，混住宿舍不进入本批次。</p></div>
      </div>
      <div class="form-grid three-column">
        <label><span>批次编号</span><input :value="form.batchCode" class="input" required maxlength="32" @input="update('batchCode', ($event.target as HTMLInputElement).value.trim())" /></label>
        <label><span>批次名称</span><input :value="form.batchName" class="input" required maxlength="128" @input="update('batchName', ($event.target as HTMLInputElement).value.trim())" /></label>
        <label><span>规则模板</span><select :value="form.ruleTemplateId" class="input" required @change="update('ruleTemplateId', Number(($event.target as HTMLSelectElement).value))"><option :value="0" disabled>请选择</option><option v-for="item in ruleTemplates" :key="String(item.id)" :value="Number(item.id)">{{ item.rule_name }} · 修订{{ item.revision }}</option></select></label>
        <label><span>开始时间</span><input :value="form.startAt" class="input" type="datetime-local" required @input="update('startAt', ($event.target as HTMLInputElement).value)" /><small>请使用24小时制，例如 18:30</small></label>
        <label><span>结束时间</span><input :value="form.endAt" class="input" type="datetime-local" required @input="update('endAt', ($event.target as HTMLInputElement).value)" /><small>请使用24小时制，例如 21:00</small></label>
        <div class="rule-summary"><strong>规则摘要</strong><span>{{ ruleTemplateSummary }}</span></div>
      </div>
      <button class="button primary" :disabled="creating">{{ creating ? '正在创建草稿…' : '创建草稿并配置范围' }}</button>
    </form>
  </section>
</template>

<style scoped>
.batch-create-form{display:grid;gap:18px}.mode-card-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.mode-card{appearance:none;display:grid;align-content:start;width:100%;min-height:124px;gap:8px;padding:20px;border:1px solid var(--border);border-radius:16px;background:var(--surface);text-align:left;color:inherit;font:inherit;cursor:pointer}.mode-card:focus,.mode-card:focus-visible{outline:3px solid var(--primary);outline-offset:2px}.mode-card span,.mode-card small{color:var(--text-muted)}.mode-card.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}.mode-card.disabled{opacity:.55;cursor:not-allowed}.separation-switch{display:flex;align-items:center;gap:14px;min-width:0;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.separation-switch>div{min-width:0;flex:1}.separation-switch strong{display:block}.separation-switch>button{appearance:none;position:relative;width:50px;height:28px;border:0;border-radius:999px;background:#cbd5e1;flex:0 0 auto;cursor:pointer}.separation-switch>button span{position:absolute;left:3px;top:3px;width:22px;height:22px;border-radius:50%;background:#fff;transition:.2s}.separation-switch>button.checked{background:var(--primary)}.separation-switch>button.checked span{transform:translateX(22px)}.separation-switch p{margin:4px 0 0;color:var(--text-muted)}.rule-summary{align-self:end;display:grid;align-content:center;gap:6px;min-height:88px;padding:12px;border-radius:12px;background:var(--surface-soft)}@media(max-width:720px){.mode-card-grid{grid-template-columns:1fr}}
</style>
