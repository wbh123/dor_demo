<script setup lang="ts">
export interface ReportDefinition {
  name: string
  fields: string[]
  filters: Record<string, unknown>
  sorts: string[]
  metrics: string[]
  locale: 'zh-CN'|'en-US'
}
const props = defineProps<{
  modelValue: ReportDefinition
  fields: string[]
  filters: string[]
  sorts: string[]
  metrics: string[]
  busy?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: ReportDefinition]; save: []; export: [] }>()

function update<K extends keyof ReportDefinition>(key: K, value: ReportDefinition[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function toggle(key: 'fields'|'sorts'|'metrics', value: string) {
  const current = props.modelValue[key]
  update(key, current.includes(value) ? current.filter((item) => item !== value) : [...current, value])
}
</script>

<template>
  <div class="report-builder">
    <div class="form-grid two-column"><label><span>报表名称</span><input class="input" :value="modelValue.name" @input="update('name', ($event.target as HTMLInputElement).value)" /></label><label><span>语言</span><select class="input" :value="modelValue.locale" @change="update('locale', ($event.target as HTMLSelectElement).value as 'zh-CN'|'en-US')"><option value="zh-CN">汉语</option><option value="en-US">英语</option></select></label></div>
    <section><header><strong>字段白名单</strong><span>只能选择系统允许的字段，不能输入结构化查询语言。</span></header><div class="choice-grid"><label v-for="field in fields" :key="field"><input type="checkbox" :checked="modelValue.fields.includes(field)" @change="toggle('fields', field)" />{{ field }}</label></div></section>
    <section><header><strong>预设指标</strong><span>指标使用固定口径版本。</span></header><div class="choice-grid"><label v-for="metric in metrics" :key="metric"><input type="checkbox" :checked="modelValue.metrics.includes(metric)" @change="toggle('metrics', metric)" />{{ metric }}</label></div></section>
    <section><header><strong>排序白名单</strong></header><div class="choice-grid"><label v-for="sort in sorts" :key="sort"><input type="checkbox" :checked="modelValue.sorts.includes(sort)" @change="toggle('sorts', sort)" />{{ sort }}</label></div></section>
    <p class="report-note">可用筛选条件：{{ filters.join('、') || '加载中' }}。筛选值由页面结构化输入，不接受任意查询语句。</p>
    <div class="button-row"><button class="button secondary" type="button" :disabled="busy" @click="emit('save')">保存报表模板</button><button class="button primary" type="button" :disabled="busy" @click="emit('export')">异步生成报表</button></div>
  </div>
</template>

<style scoped>
.report-builder{display:grid;gap:16px}.report-builder section{display:grid;gap:10px;padding:14px;border:1px solid var(--border);border-radius:14px}.report-builder section>header{display:flex;justify-content:space-between;gap:12px}.report-builder section>header span,.report-note{color:var(--text-muted);font-size:12px}.choice-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:8px}.choice-grid label{display:flex;align-items:center;gap:7px;padding:8px;border-radius:9px;background:var(--surface-soft)}.report-note{margin:0}
</style>
