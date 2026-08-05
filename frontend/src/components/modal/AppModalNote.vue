<script setup lang="ts">defineProps<{ text: string }>()</script>
<template><small class="app-modal-note">{{ text }}</small></template>
<style scoped>.app-modal-note{color:var(--muted,#64748b);line-height:1.55}</style>
