<template>
  <RouterView/>
  <SessionExpiredDialog/>
  <GlobalConfirmDialog/>
  <GlobalNotification/>
  <LivePracticePopup/>
</template>

<script lang="ts" setup>
import {watch} from 'vue'
import {RouterView} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {useAiStore} from '@/features/ai/stores/ai'
import {useLivePracticeEvents} from '@/features/live-practice/composables/useLivePracticeEvents'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import {useUnreadCount} from '@/shared/composables/useUnreadCount'
import {closeAllSseConnections} from '@/shared/api/sseManager'
import GlobalConfirmDialog from '@/shared/components/GlobalConfirmDialog.vue'
import GlobalNotification from '@/shared/components/GlobalNotification.vue'
import SessionExpiredDialog from '@/shared/components/SessionExpiredDialog.vue'
import LivePracticePopup from '@/features/live-practice/components/LivePracticePopup.vue'

const authStore = useAuthStore()
const aiStore = useAiStore()
const uiPreferences = useUiPreferencesStore()
const unreadCount = useUnreadCount()
const livePracticeEvents = useLivePracticeEvents()

uiPreferences.initializeTheme()

watch(
    () => authStore.user ? authStore.user.theme ?? 'system' : null,
    (theme) => {
      if (theme) {
        uiPreferences.setThemePreference(theme)
      }
    },
    {immediate: true},
)

watch(
    () => authStore.user?.id ?? null,
    (userId, previousUserId) => {
      if (userId !== previousUserId) {
        aiStore.resetSessionState()
      }
    },
)

watch(
    () => authStore.isAuthenticated,
    (isAuthenticated) => {
      if (isAuthenticated) {
        unreadCount.start()
        livePracticeEvents.start()
        return
      }

      unreadCount.stop()
      livePracticeEvents.stop()
      closeAllSseConnections()
    },
    {immediate: true},
)
</script>
