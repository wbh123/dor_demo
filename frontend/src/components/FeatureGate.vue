<script setup lang="ts">
import { useFeatureAccess } from '../composables/useFeatureAccess'
const props = withDefaults(defineProps<{ feature: string; fallback?: string }>(), { fallback: '' })
const { hasFeature } = useFeatureAccess()
</script>
<template>
  <slot v-if="hasFeature(props.feature)" />
  <span v-else-if="props.fallback" class="feature-disabled">{{ props.fallback }}</span>
</template>
<style scoped>.feature-disabled{color:#64748b;font-size:13px}</style>
