<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('common.settings.account') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="resetProfileForm">
            {{ t('settings.resetChanges') }}
          </button>
          <button class="btn-primary" :disabled="savingProfile" type="button" @click="saveProfile">
            {{ savingProfile ? t('settings.saving') : t('settings.saveChanges') }}
          </button>
        </div>
      </header>

      <section class="settings-panel profile-card-panel">
        <header class="settings-panel-header">
          <h3>{{ t('settings.profile') }}</h3>
        </header>

        <div class="settings-panel-body profile-card-body">
          <div class="profile-card-shell">
            <div class="profile-avatar-pane">
              <BaseImageUploader
                v-model="profileForm.avatarFileId"
                usage="USER_AVATAR"
                scope-type="USER"
                :scope-id="authStore.user?.id"
                :preview-url="avatarPreviewUrl"
                :fallback-url="defaultAvatarSrc"
                :alt="t('settings.profile')"
                :button-label="t('settings.changeAvatar')"
                :help-text="t('settings.avatarHelp')"
                :disabled="!authStore.user?.id"
                shape="circle"
                size="avatar"
                @uploaded="handleAvatarUploaded"
                @error="profileMessage = $event"
              />
            </div>

            <div class="profile-form-pane">
              <div class="form-grid">
                <div class="form-group">
                  <label for="settings-display-name">{{ t('settings.displayName') }}</label>
                  <input
                    id="settings-display-name"
                    v-model="profileForm.displayName"
                    class="input-field"
                    type="text"
                    :placeholder="t('settings.displayNamePlaceholder')"
                  />
                </div>
                <div class="form-group">
                  <label for="settings-email">{{ t('settings.email') }}</label>
                  <input
                    id="settings-email"
                    v-model="profileForm.email"
                    class="input-field"
                    type="email"
                    :placeholder="t('settings.emailPlaceholder')"
                    disabled
                  />
                </div>
              </div>

              <div class="form-grid">
                <div class="form-group">
                  <label for="settings-phone">{{ t('settings.phone') }}</label>
                  <input
                    id="settings-phone"
                    v-model="profileForm.phone"
                    class="input-field"
                    type="tel"
                    :placeholder="t('settings.phonePlaceholder')"
                  />
                </div>
                <div class="form-group">
                  <label for="settings-birthday">{{ t('settings.birthday') }}</label>
                  <BaseDatePicker
                    id="settings-birthday"
                    v-model="profileForm.birthday"
                    :placeholder="t('settings.datePlaceholder')"
                  />
                </div>
              </div>

              <div class="form-group">
                <label for="settings-bio">{{ t('settings.bio') }}</label>
                <textarea
                  id="settings-bio"
                  v-model="profileForm.bio"
                  class="input-field"
                  :placeholder="t('settings.bioPlaceholder')"
                  rows="3"
                ></textarea>
              </div>

              <p v-if="profileMessage" class="form-message">{{ profileMessage }}</p>
            </div>
          </div>
        </div>
      </section>

      <div class="settings-grid two-column">
        <section class="settings-panel">
          <header class="settings-panel-header">
            <h3>{{ t('settings.changePassword') }}</h3>
          </header>
          <div class="settings-panel-body">
            <div class="form-group">
              <label for="settings-current-password">{{ t('settings.currentPassword') }}</label>
              <input
                id="settings-current-password"
                v-model="passwordForm.current"
                class="input-field"
                type="password"
                :placeholder="t('settings.currentPasswordPlaceholder')"
              />
            </div>
            <div class="form-group">
              <label for="settings-new-password">{{ t('settings.newPassword') }}</label>
              <input
                id="settings-new-password"
                v-model="passwordForm.newPassword"
                class="input-field"
                type="password"
                :placeholder="t('settings.newPasswordPlaceholder')"
              />
            </div>
            <div class="form-group">
              <label for="settings-confirm-password">{{ t('settings.confirmPassword') }}</label>
              <input
                id="settings-confirm-password"
                v-model="passwordForm.confirm"
                class="input-field"
                type="password"
                :placeholder="t('settings.confirmPasswordPlaceholder')"
              />
            </div>
            <p v-if="passwordMessage" class="form-message">{{ passwordMessage }}</p>
            <div class="section-actions">
              <button class="btn-secondary" type="button" @click="changePassword">
                {{ t('settings.updatePassword') }}
              </button>
            </div>
          </div>
        </section>

        <section class="settings-panel">
          <header class="settings-panel-header">
            <h3>{{ t('settings.connectedAccounts') }}</h3>
          </header>
          <div class="settings-panel-body">
            <div v-for="account in connectedAccounts" :key="account.name" class="connected-account">
              <div class="account-info">
                <component :is="account.icon" :size="20" stroke-width="1.8"/>
                <span class="account-name">{{ account.name }}</span>
              </div>
              <span class="status-badge-outline">
                {{ account.connected ? t('settings.connected') : t('settings.notConnected') }}
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, markRaw, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {Chrome, Github, KeyRound} from 'lucide-vue-next'

import {updateCurrentUser} from '@/features/user/api/user'
import BaseDatePicker from '@/shared/components/BaseDatePicker.vue'
import BaseImageUploader from '@/shared/components/BaseImageUploader.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import type {FileAsset} from '@/features/storage/types/storage'
import type {OauthProvider, UpdateUserRequest} from '@/features/user/types/user'

const authStore = useAuthStore()
const {t} = useI18n()

const profileForm = reactive({
  displayName: authStore.user?.displayName ?? '',
  email: authStore.user?.email ?? '',
  phone: authStore.user?.phone ?? '',
  bio: authStore.user?.bio ?? '',
  gender: authStore.user?.gender ?? 0,
  birthday: authStore.user?.birthday ?? '',
  avatarFileId: authStore.user?.avatarFileId ?? '',
})

const savingProfile = ref(false)
const profileMessage = ref('')
const passwordMessage = ref('')
const avatarPreviewUrl = ref(authStore.user?.avatarUrl ?? '')

const passwordForm = reactive({
  current: '',
  newPassword: '',
  confirm: '',
})

const linkedProviders = computed(() => new Set(authStore.user?.linkedProviders ?? []))

const defaultAvatarSrc = computed(() => {
  switch (authStore.user?.role) {
    case 0: return '/assets/avatar-admin-default.png'
    case 2: return '/assets/avatar-teacher-default.png'
    default: return '/assets/avatar-student-default.png'
  }
})
const connectedAccounts = computed(() => [
  {name: 'Google', provider: 'GOOGLE' as OauthProvider, icon: markRaw(Chrome)},
  {name: 'GitHub', provider: 'GITHUB' as OauthProvider, icon: markRaw(Github)},
  {name: 'Campus SSO', provider: 'LOCAL' as OauthProvider, icon: markRaw(KeyRound)},
].map((account) => ({
  ...account,
  connected: linkedProviders.value.has(account.provider),
})))

function buildProfilePayload(): UpdateUserRequest {
  return {
    displayName: profileForm.displayName || null,
    phone: profileForm.phone || null,
    bio: profileForm.bio || null,
    gender: profileForm.gender,
    birthday: profileForm.birthday || null,
    avatarFileId: profileForm.avatarFileId || null,
  }
}

function resetProfileForm() {
  profileForm.displayName = authStore.user?.displayName ?? ''
  profileForm.email = authStore.user?.email ?? ''
  profileForm.phone = authStore.user?.phone ?? ''
  profileForm.bio = authStore.user?.bio ?? ''
  profileForm.gender = authStore.user?.gender ?? 0
  profileForm.birthday = authStore.user?.birthday ?? ''
  profileForm.avatarFileId = authStore.user?.avatarFileId ?? ''
  avatarPreviewUrl.value = authStore.user?.avatarUrl ?? ''
  profileMessage.value = ''
}

function handleAvatarUploaded(asset: FileAsset) {
  profileForm.avatarFileId = asset.id
  if (asset.url) {
    avatarPreviewUrl.value = asset.url
  }
  profileMessage.value = ''
}

async function saveProfile() {
  savingProfile.value = true
  profileMessage.value = ''

  try {
    const updatedUser = await updateCurrentUser(buildProfilePayload())
    authStore.setUser(updatedUser)
    profileMessage.value = t('settings.profileSaved')
    notify.success(t('settings.profileSaved'))
  } catch (error) {
    const msg = error instanceof Error ? error.message : t('settings.saveFailed')
    profileMessage.value = msg
    notify.error(msg)
  } finally {
    savingProfile.value = false
  }
}

function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirm) {
    passwordMessage.value = t('settings.alert.passwordMismatch')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    passwordMessage.value = t('settings.alert.passwordTooShort')
    return
  }

  passwordMessage.value = t('settings.alert.passwordUpdated')
  passwordForm.current = ''
  passwordForm.newPassword = ''
  passwordForm.confirm = ''
}
</script>

<style scoped src="../styles/settings-page.css"></style>
