<script setup lang="ts">
interface Option {
  value: string
  label: string
  description: string
}

defineProps<{
  modelValue: string
  options: Option[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="three-state-toggle" role="radiogroup">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="modelValue === option.value"
      :class="{ active: modelValue === option.value }"
      @click="emit('update:modelValue', option.value)"
    >
      <strong>{{ option.label }}</strong>
      <span>{{ option.description }}</span>
    </button>
  </div>
</template>

<style scoped>
.three-state-toggle{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.three-state-toggle button{display:grid;gap:5px;min-height:86px;padding:14px;border:1px solid var(--line);border-radius:14px;text-align:left;color:inherit;background:var(--soft);cursor:pointer}.three-state-toggle button.active{border-color:#5684c9;background:#eef5ff;box-shadow:0 0 0 2px rgba(86,132,201,.12)}.three-state-toggle span{color:var(--muted);font-size:12px;line-height:1.5}@media(max-width:760px){.three-state-toggle{grid-template-columns:1fr}.three-state-toggle button{min-height:0}}
</style>
