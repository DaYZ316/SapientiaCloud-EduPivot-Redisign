import {createApp} from 'vue'
import {createPinia} from 'pinia'

import App from './App.vue'
import {router} from './router'
import {i18n} from '@/app/i18n'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {initializeRuntimeEnvironment} from '@/shared/platform/runtime'
import {installGlobalTooltip} from '@/shared/utils/globalTooltip'
import '@/shared/styles/main.scss'

async function bootstrap() {
    await initializeRuntimeEnvironment()

    const app = createApp(App)
    const pinia = createPinia()
    installGlobalTooltip()

    app.config.errorHandler = (err, _instance, info) => {
        console.error('[Vue Error]', info, err)
        if (err instanceof Error) {
            notify.error(err.message)
        }
    }

    app.use(pinia).use(router).use(i18n)
    await useAuthStore(pinia).restoreSession()
    app.mount('#app')
}

void bootstrap()
