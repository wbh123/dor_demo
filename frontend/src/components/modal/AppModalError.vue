<script setup lang="ts">
defineProps<{ message?: string }>()
</script>

<template><p v-if="message" class="alert error app-modal-error" role="alert">{{ message }}</p></template>

<style scoped>.app-modal-error{margin:0}</style>
