import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import 'primeicons/primeicons.css'

import App from './App.vue'
import router from './router'
import { Chocolate } from './theme/chocolate'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: Chocolate,
    options: { darkModeSelector: false }, // v1 is light mode only
  },
})

app.mount('#app')
