<script setup lang="ts">defineProps<{ text: string }>()</script>
<template><details class="app-modal-help"><summary>查看说明</summary><p>{{ text }}</p></details></template>
<style scoped>.app-modal-help{color:var(--muted,#64748b)}summary{cursor:pointer;font-weight:700}p{line-height:1.65}</style>
