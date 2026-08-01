import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import './style.css'
import './ux-refinement.css'
import './room-selection-refinement.css'
import './phase2-room-layout.css'

createApp(App).use(pinia).use(router).mount('#app')
