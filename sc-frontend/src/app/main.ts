import {createApp} from 'vue'
import {createPinia} from 'pinia'

import App from './App.vue'
import {router} from './router'
import {i18n} from '@/app/i18n'
import {notify} from '@/shared/composables/useGlobalNotification'
import '@/shared/styles/main.scss'

const app = createApp(App)

app.config.errorHandler = (err, _instance, info) => {
    console.error('[Vue Error]', info, err)
    if (err instanceof Error) {
        notify.error(err.message)
    }
}

app.use(createPinia()).use(router).use(i18n).mount('#app')
