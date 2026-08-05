<template><div class="app-modal-columns"><slot /></div></template>
<style scoped>.app-modal-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:start}@media(max-width:760px){.app-modal-columns{grid-template-columns:1fr}}</style>
