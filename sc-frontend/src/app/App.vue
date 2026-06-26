<template>
  <RouterView/>
  <SessionExpiredDialog/>
  <GlobalConfirmDialog/>
  <GlobalNotification/>
  <LivePracticePopup/>
  <ClassroomLiveGlobalMiniWindow/>
</template>

<script lang="ts" setup>
import {onBeforeUnmount, watch} from 'vue'
import {RouterView, useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {useAiStore} from '@/features/ai/stores/ai'
import {useLivePracticeEvents} from '@/features/live-practice/composables/useLivePracticeEvents'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import {useUnreadCount} from '@/shared/composables/useUnreadCount'
import {closeAllSseConnections} from '@/shared/api/sseManager'
import {PROFILE_INCOMPLETE_EVENT, PROFILE_INCOMPLETE_REDIRECT_KEY} from '@/shared/api/request'
import GlobalConfirmDialog from '@/shared/components/GlobalConfirmDialog.vue'
import GlobalNotification from '@/shared/components/GlobalNotification.vue'
import SessionExpiredDialog from '@/shared/components/SessionExpiredDialog.vue'
import LivePracticePopup from '@/features/live-practice/components/LivePracticePopup.vue'
import ClassroomLiveGlobalMiniWindow from '@/features/classroom/components/ClassroomLiveGlobalMiniWindow.vue'

const authStore = useAuthStore()
const aiStore = useAiStore()
const uiPreferences = useUiPreferencesStore()
const unreadCount = useUnreadCount()
const livePracticeEvents = useLivePracticeEvents()
const router = useRouter()
const route = useRoute()

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

function handleProfileIncomplete() {
  if (route.name === 'onboarding') {
    return
  }
  const redirect = sessionStorage.getItem(PROFILE_INCOMPLETE_REDIRECT_KEY) || route.fullPath
  sessionStorage.removeItem(PROFILE_INCOMPLETE_REDIRECT_KEY)
  void router.replace({
    name: 'onboarding',
    query: {
      redirect,
    },
  })
}

if (typeof window !== 'undefined') {
  window.addEventListener(PROFILE_INCOMPLETE_EVENT, handleProfileIncomplete)
  if (sessionStorage.getItem(PROFILE_INCOMPLETE_REDIRECT_KEY)) {
    void router.isReady().then(handleProfileIncomplete)
  }
}

onBeforeUnmount(() => {
  window.removeEventListener(PROFILE_INCOMPLETE_EVENT, handleProfileIncomplete)
})
</script>
