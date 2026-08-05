<script setup lang="ts">
export interface AuditFilters {
  occurredFrom: string
  occurredTo: string
  operatorId: string
  operatorRole: string
  module: string
  actionType: string
  targetType: string
  targetId: string
  success: string
  errorCode: string
  requestId: string
  networkAddress: string
  keyword: string
}

const props = defineProps<{ modelValue: AuditFilters; busy?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: AuditFilters]
  search: []
  reset: []
}>()

function update(key: keyof AuditFilters, value: string) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <div class="audit-filter-bar">
    <label><span>开始时间</span><input class="input" type="datetime-local" :value="modelValue.occurredFrom" @input="update('occurredFrom', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>结束时间</span><input class="input" type="datetime-local" :value="modelValue.occurredTo" @input="update('occurredTo', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>操作人编号</span><input class="input" :value="modelValue.operatorId" @input="update('operatorId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>角色</span><select class="input" :value="modelValue.operatorRole" @change="update('operatorRole', ($event.target as HTMLSelectElement).value)"><option value="">全部</option><option value="ADMIN">业务管理员</option><option value="STUDENT">学生</option></select></label>
    <label><span>模块</span><input class="input" :value="modelValue.module" @input="update('module', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>操作类型</span><input class="input" :value="modelValue.actionType" @input="update('actionType', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>目标类型</span><input class="input" :value="modelValue.targetType" @input="update('targetType', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>目标编号</span><input class="input" :value="modelValue.targetId" @input="update('targetId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>结果</span><select class="input" :value="modelValue.success" @change="update('success', ($event.target as HTMLSelectElement).value)"><option value="">全部</option><option value="true">成功</option><option value="false">失败</option></select></label>
    <label><span>错误代码</span><input class="input" :value="modelValue.errorCode" @input="update('errorCode', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>请求编号</span><input class="input" :value="modelValue.requestId" @input="update('requestId', ($event.target as HTMLInputElement).value)" /></label>
    <label><span>网络地址</span><input class="input" :value="modelValue.networkAddress" @input="update('networkAddress', ($event.target as HTMLInputElement).value)" /></label>
    <label class="span-2"><span>关键词</span><input class="input" :value="modelValue.keyword" placeholder="操作、资源、原因" @input="update('keyword', ($event.target as HTMLInputElement).value)" /></label>
    <div class="button-row filter-actions"><button class="button ghost" type="button" :disabled="busy" @click="emit('reset')">重置</button><button class="button primary" type="button" :disabled="busy" @click="emit('search')">{{ busy ? '查询中…' : '查询审计' }}</button></div>
  </div>
</template>

<style scoped>
.audit-filter-bar{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.audit-filter-bar label{display:grid;gap:6px}.audit-filter-bar label>span{font-size:12px;font-weight:700;color:var(--text-muted)}.span-2{grid-column:span 2}.filter-actions{align-self:end;justify-content:flex-end}@media(max-width:1000px){.audit-filter-bar{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:620px){.audit-filter-bar{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
