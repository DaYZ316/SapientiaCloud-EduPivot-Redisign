<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('settings.notifications.title') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="restoreNotificationDefaults">
            {{ t('settings.resetChanges') }}
          </button>
          <button :disabled="savingNotifications" class="btn-primary" type="button" @click="saveNotificationSettings">
            {{ savingNotifications ? t('settings.saving') : t('settings.notifications.savePreferences') }}
          </button>
        </div>
      </header>

      <section class="settings-panel">
        <div class="settings-panel-body">
          <div v-for="item in notificationItems" :key="item.key" class="setting-item">
            <div class="setting-info">
              <p class="setting-title">{{ item.title }}</p>
              <p class="setting-desc">{{ item.description }}</p>
            </div>
            <label class="toggle">
              <input v-model="notificationSettings[item.key]" type="checkbox"/>
              <span class="toggle-slider"></span>
            </label>
          </div>

          <p v-if="notificationMessage" class="form-message">{{ notificationMessage }}</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'

import {updateCurrentUser} from '@/features/user/api/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'

type NotificationKey = 'courseUpdates' | 'communityActivity' | 'systemNotifications' | 'emailNotifications'

const authStore = useAuthStore()
const {t} = useI18n()

const savingNotifications = ref(false)
const notificationMessage = ref('')

const notificationSettings = reactive<Record<NotificationKey, boolean>>({
  courseUpdates: authStore.user?.notificationEnabled ?? true,
  communityActivity: true,
  systemNotifications: true,
  emailNotifications: true,
})

const notificationItems = computed(() => [
  {
    key: 'courseUpdates' as NotificationKey,
    title: t('settings.notifications.courseUpdates'),
    description: t('settings.notifications.courseUpdatesDesc'),
  },
  {
    key: 'communityActivity' as NotificationKey,
    title: t('settings.notifications.communityActivity'),
    description: t('settings.notifications.communityActivityDesc'),
  },
  {
    key: 'systemNotifications' as NotificationKey,
    title: t('settings.notifications.systemNotifications'),
    description: t('settings.notifications.systemNotificationsDesc'),
  },
  {
    key: 'emailNotifications' as NotificationKey,
    title: t('settings.notifications.emailNotifications'),
    description: t('settings.notifications.emailNotificationsDesc'),
  },
])

function restoreNotificationDefaults() {
  notificationSettings.courseUpdates = authStore.user?.notificationEnabled ?? true
  notificationSettings.communityActivity = true
  notificationSettings.systemNotifications = true
  notificationSettings.emailNotifications = true
  notificationMessage.value = ''
}

async function saveNotificationSettings() {
  savingNotifications.value = true
  notificationMessage.value = ''

  try {
    const updatedUser = await updateCurrentUser({
      notificationEnabled: notificationSettings.courseUpdates,
    })
    authStore.setUser(updatedUser)
    notificationMessage.value = t('settings.notifications.saved')
    notify.success(t('settings.notifications.saved'))
  } catch (error) {
    const msg = error instanceof Error ? error.message : t('settings.notifications.saveFailed')
    notificationMessage.value = msg
    notify.error(msg)
  } finally {
    savingNotifications.value = false
  }
}
</script>

<style scoped src="../styles/settings-page.css"></style>
