<script setup lang="ts">
import { computed } from 'vue'
import WelcomeMessageEditor from './WelcomeMessageEditor.vue'
import { countryLabel, countryOptions } from '../../utils/countries'

const props = defineProps<{
  modelValue: Record<string, string>
  selectedCountry: string
  tokenExamples?: Record<string, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>]
  'update:selectedCountry': [value: string]
}>()

const configuredCodes = computed(() => Object.keys(props.modelValue)
  .sort((left, right) => countryLabel(left).localeCompare(countryLabel(right), 'zh-CN')))
const availableCountries = computed(() => countryOptions.filter((item) =>
  item.code !== 'CN' && item.code !== 'US' && props.modelValue[item.code] === undefined))
const selectedMessage = computed({
  get: () => props.modelValue[props.selectedCountry] ?? '',
  set: (value: string) => emit('update:modelValue', {
    ...props.modelValue,
    [props.selectedCountry]: value,
  }),
})

function selectConfigured(event: Event) {
  emit('update:selectedCountry', (event.target as HTMLSelectElement).value)
}

function addCountry(event: Event) {
  const code = (event.target as HTMLSelectElement).value
  if (!code) return
  emit('update:modelValue', { ...props.modelValue, [code]: '' })
  emit('update:selectedCountry', code)
  ;(event.target as HTMLSelectElement).value = ''
}

function removeSelected() {
  if (!props.selectedCountry) return
  const next = { ...props.modelValue }
  delete next[props.selectedCountry]
  const remaining = Object.keys(next)
  emit('update:modelValue', next)
  emit('update:selectedCountry', remaining[0] ?? '')
}
</script>

<template>
  <section class="country-welcome-editor">
    <div class="country-select-grid">
      <label>
        <span>已配置国家或地区</span>
        <select class="input" :value="selectedCountry" :disabled="!configuredCodes.length" @change="selectConfigured">
          <option v-if="!configuredCodes.length" value="">暂未配置</option>
          <option v-for="code in configuredCodes" :key="code" :value="code">{{ countryLabel(code) }}</option>
        </select>
      </label>
      <label>
        <span>添加国家或地区</span>
        <select class="input" value="" :disabled="!availableCountries.length" @change="addCountry">
          <option value="">请选择尚未配置的国家或地区</option>
          <option v-for="country in availableCountries" :key="country.code" :value="country.code">{{ country.name }}</option>
        </select>
      </label>
    </div>

    <article v-if="selectedCountry" class="country-editor-card">
      <header>
        <div><strong>{{ countryLabel(selectedCountry) }}</strong><small>该国家或地区登录时优先展示</small></div>
        <button class="button ghost" type="button" @click="removeSelected">删除该国家配置</button>
      </header>
      <WelcomeMessageEditor
        v-model="selectedMessage"
        :token-examples="tokenExamples"
        :placeholder="`填写面向${countryLabel(selectedCountry)}学生的欢迎语`"
      />
    </article>
    <p v-else class="empty-state">通过“添加国家或地区”下拉框新增欢迎语，未配置国家或地区自动使用英语欢迎语。</p>
  </section>
</template>

<style scoped>
.country-welcome-editor{display:grid;gap:16px}.country-select-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.country-select-grid label{display:grid;gap:7px}.country-editor-card{display:grid;gap:12px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--soft)}.country-editor-card header{display:flex;align-items:center;justify-content:space-between;gap:14px}.country-editor-card header div{display:grid;gap:4px}.country-editor-card small{color:var(--muted)}@media(max-width:720px){.country-select-grid{grid-template-columns:1fr}.country-editor-card header{align-items:flex-start;flex-direction:column}}
</style>
