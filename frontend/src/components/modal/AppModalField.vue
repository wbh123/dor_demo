<script setup lang="ts">withDefaults(defineProps<{ label: string; hint?: string; required?: boolean }>(), { hint: '', required: false })</script>
<template><label class="app-modal-field"><span>{{ label }}<b v-if="required" aria-hidden="true">*</b></span><slot /><small v-if="hint">{{ hint }}</small></label></template>
<style scoped>.app-modal-field{display:grid;gap:7px}.app-modal-field>span{font-weight:800}.app-modal-field b{margin-left:4px;color:#c2414d}.app-modal-field small{color:var(--muted,#64748b)}</style>
