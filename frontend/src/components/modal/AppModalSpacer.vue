<script setup lang="ts">withDefaults(defineProps<{ size?: 'small' | 'medium' | 'large' }>(), { size: 'medium' })</script>
<template><div class="app-modal-spacer" :class="`size-${size}`" aria-hidden="true" /></template>
<style scoped>.size-small{height:8px}.size-medium{height:16px}.size-large{height:24px}</style>
