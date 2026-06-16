<template>
  <RouterView/>
  <SessionExpiredDialog/>
  <GlobalConfirmDialog/>
  <GlobalNotification/>
</template>

<script lang="ts" setup>
import {watch} from 'vue'
import {RouterView} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import GlobalConfirmDialog from '@/shared/components/GlobalConfirmDialog.vue'
import GlobalNotification from '@/shared/components/GlobalNotification.vue'
import SessionExpiredDialog from '@/shared/components/SessionExpiredDialog.vue'

const authStore = useAuthStore()
const uiPreferences = useUiPreferencesStore()

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
</script>
