import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import './style.css'
import './ux-refinement.css'
import './room-selection-refinement.css'
import './phase2-room-layout.css'
import './room-scene-geometry-fix.css'
import './matching-operations.css'
import './student-experience.css'
import './batch-copy.css'

createApp(App).use(pinia).use(router).mount('#app')
