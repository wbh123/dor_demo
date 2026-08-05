<script setup lang="ts">
withDefaults(defineProps<{ empty?: boolean; emptyText?: string }>(), { empty: false, emptyText: '暂无数据' })
</script>
<template><div class="app-modal-list"><p v-if="empty" class="empty-state">{{ emptyText }}</p><slot v-else /></div></template>
<style scoped>.app-modal-list{min-height:0}</style>
