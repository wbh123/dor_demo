<script setup lang="ts">withDefaults(defineProps<{ maxHeight?: string }>(), { maxHeight: '360px' })</script>
<template><div class="app-modal-scrollable" :style="{ maxHeight }"><slot /></div></template>
<style scoped>.app-modal-scrollable{min-height:0;overflow:auto;overscroll-behavior:contain}</style>
