<template>
  <div class="tab-content">
    <div class="settings-workspace">
      <header class="settings-hero">
        <h2>{{ t('common.settings.account') }}</h2>
        <div class="settings-hero-actions">
          <button class="btn-secondary" type="button" @click="resetProfileForm">
            {{ t('settings.resetChanges') }}
          </button>
          <button :disabled="savingProfile" class="btn-primary" type="button" @click="saveProfile">
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
                  :alt="t('settings.profile')"
                  :button-label="t('settings.changeAvatar')"
                  :disabled="!authStore.user?.id"
                  :fallback-label="avatarInitials"
                  :help-text="t('settings.avatarHelp')"
                  :preview-url="avatarPreviewUrl"
                  :scope-id="authStore.user?.id"
                  scope-type="USER"
                  shape="circle"
                  size="avatar"
                  usage="USER_AVATAR"
                  @error="profileMessage = $event"
                  @uploaded="handleAvatarUploaded"
              />
            </div>

            <div class="profile-form-pane">
              <div class="form-grid">
                <div class="form-group">
                  <label for="settings-display-name">{{ t('settings.displayName') }}</label>
                  <input
                      id="settings-display-name"
                      v-model="profileForm.displayName"
                      :placeholder="t('settings.displayNamePlaceholder')"
                      class="input-field"
                      type="text"
                  />
                </div>
                <div class="form-group">
                  <label for="settings-email">{{ t('settings.email') }}</label>
                  <input
                      id="settings-email"
                      v-model="profileForm.email"
                      :placeholder="t('settings.emailPlaceholder')"
                      class="input-field"
                      disabled
                      type="email"
                  />
                </div>
              </div>

              <div class="form-grid">
                <div class="form-group">
                  <label for="settings-phone">{{ t('settings.phone') }}</label>
                  <input
                      id="settings-phone"
                      v-model="profileForm.phone"
                      :placeholder="t('settings.phonePlaceholder')"
                      class="input-field"
                      type="tel"
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
                    :placeholder="t('settings.bioPlaceholder')"
                    class="input-field"
                    rows="3"
                ></textarea>
              </div>

              <section v-if="isStudentProfile" class="role-profile-fields">
                <header class="role-profile-header">
                  <h4>{{ t('settings.studentProfile') }}</h4>
                </header>
                <div class="form-grid">
                  <div class="form-group">
                    <label for="settings-student-no">{{ t('settings.studentNo') }}</label>
                    <input
                        id="settings-student-no"
                        v-model="profileForm.studentNo"
                        class="input-field"
                        disabled
                        type="text"
                    />
                  </div>
                  <div class="form-group">
                    <label for="settings-student-grade">{{ t('settings.grade') }}</label>
                    <input
                        id="settings-student-grade"
                        v-model="profileForm.grade"
                        :placeholder="t('settings.gradePlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                </div>
                <div class="form-grid">
                  <div class="form-group">
                    <label for="settings-student-major">{{ t('settings.major') }}</label>
                    <input
                        id="settings-student-major"
                        v-model="profileForm.major"
                        :placeholder="t('settings.majorPlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                  <div class="form-group">
                    <label for="settings-student-school">{{ t('settings.school') }}</label>
                    <input
                        id="settings-student-school"
                        v-model="profileForm.school"
                        :placeholder="t('settings.schoolPlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                </div>
              </section>

              <section v-else-if="isTeacherProfile" class="role-profile-fields">
                <header class="role-profile-header">
                  <h4>{{ t('settings.teacherProfile') }}</h4>
                </header>
                <div class="form-grid">
                  <div class="form-group">
                    <label for="settings-employee-no">{{ t('settings.employeeNo') }}</label>
                    <input
                        id="settings-employee-no"
                        v-model="profileForm.employeeNo"
                        class="input-field"
                        disabled
                        type="text"
                    />
                  </div>
                  <div class="form-group">
                    <label for="settings-department">{{ t('settings.department') }}</label>
                    <input
                        id="settings-department"
                        v-model="profileForm.department"
                        :placeholder="t('settings.departmentPlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                </div>
                <div class="form-grid">
                  <div class="form-group">
                    <label for="settings-title">{{ t('settings.teacherTitle') }}</label>
                    <input
                        id="settings-title"
                        v-model="profileForm.teacherTitle"
                        :placeholder="t('settings.teacherTitlePlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                  <div class="form-group">
                    <label for="settings-teacher-school">{{ t('settings.school') }}</label>
                    <input
                        id="settings-teacher-school"
                        v-model="profileForm.school"
                        :placeholder="t('settings.schoolPlaceholder')"
                        class="input-field"
                        type="text"
                    />
                  </div>
                </div>
              </section>

              <p v-if="profileMessage" class="form-message">{{ profileMessage }}</p>
            </div>
          </div>
        </div>
      </section>

      <div class="settings-grid two-column">
        <section class="settings-panel">
          <header class="settings-panel-header">
            <h3>{{ t(hasLocalPassword ? 'settings.changePassword' : 'settings.setPassword') }}</h3>
          </header>
          <div class="settings-panel-body">
            <p v-if="!hasLocalPassword" class="help-text">
              {{ t('settings.setPasswordHelp') }}
            </p>
            <div v-if="hasLocalPassword" class="form-group">
              <label for="settings-current-password">{{ t('settings.currentPassword') }}</label>
              <input
                  id="settings-current-password"
                  v-model="passwordForm.current"
                  :placeholder="t('settings.currentPasswordPlaceholder')"
                  class="input-field"
                  type="password"
              />
            </div>
            <div class="form-group">
              <label for="settings-new-password">{{ t('settings.newPassword') }}</label>
              <input
                  id="settings-new-password"
                  v-model="passwordForm.newPassword"
                  :placeholder="t('settings.newPasswordPlaceholder')"
                  class="input-field"
                  type="password"
              />
            </div>
            <div class="form-group">
              <label for="settings-confirm-password">{{ t('settings.confirmPassword') }}</label>
              <input
                  id="settings-confirm-password"
                  v-model="passwordForm.confirm"
                  :placeholder="t('settings.confirmPasswordPlaceholder')"
                  class="input-field"
                  type="password"
              />
            </div>
            <p v-if="passwordMessage" class="form-message">{{ passwordMessage }}</p>
            <div class="section-actions">
              <button :disabled="savingPassword" class="btn-secondary" type="button" @click="changePassword">
                {{
                  savingPassword ? t('settings.saving') : t(hasLocalPassword ? 'settings.updatePassword' : 'settings.setPasswordAction')
                }}
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
import {computed, markRaw, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {Chrome, Github, KeyRound} from 'lucide-vue-next'

import {changeCurrentUserPassword, getCurrentUser, updateCurrentUser} from '@/features/user/api/user'
import BaseDatePicker from '@/shared/components/BaseDatePicker.vue'
import BaseImageUploader from '@/shared/components/BaseImageUploader.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import type {FileAsset} from '@/features/storage/types/storage'
import type {OauthProvider, UpdateUserRequest} from '@/features/user/types/user'
import {getAvatarInitials} from '@/shared/utils/avatar'

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
  studentNo: authStore.user?.studentInfo?.studentNo ?? '',
  grade: authStore.user?.studentInfo?.grade ?? '',
  major: authStore.user?.studentInfo?.major ?? '',
  employeeNo: authStore.user?.teacherInfo?.employeeNo ?? '',
  department: authStore.user?.teacherInfo?.department ?? '',
  teacherTitle: authStore.user?.teacherInfo?.title ?? '',
  school: authStore.user?.studentInfo?.school ?? authStore.user?.teacherInfo?.school ?? '',
})

const savingProfile = ref(false)
const savingPassword = ref(false)
const profileMessage = ref('')
const passwordMessage = ref('')
const avatarPreviewUrl = ref(authStore.user?.avatarUrl ?? '')

const passwordForm = reactive({
  current: '',
  newPassword: '',
  confirm: '',
})

const linkedProviders = computed(() => new Set(authStore.user?.linkedProviders ?? []))

const avatarInitials = computed(() => getAvatarInitials(profileForm.displayName))
const isStudentProfile = computed(() => authStore.user?.role === 1)
const isTeacherProfile = computed(() => authStore.user?.role === 2)
const hasLocalPassword = computed(() => linkedProviders.value.has('LOCAL'))
const connectedAccounts = computed(() => [
  {name: 'Google', provider: 'GOOGLE' as OauthProvider, icon: markRaw(Chrome)},
  {name: 'GitHub', provider: 'GITHUB' as OauthProvider, icon: markRaw(Github)},
  {name: 'Campus SSO', provider: 'LOCAL' as OauthProvider, icon: markRaw(KeyRound)},
].map((account) => ({
  ...account,
  connected: linkedProviders.value.has(account.provider),
})))

onMounted(async () => {
  try {
    authStore.setUser(await getCurrentUser())
    resetProfileForm()
  } catch {
    // Keep the locally cached profile when refresh is unavailable.
  }
})

function buildProfilePayload(): UpdateUserRequest {
  const payload: UpdateUserRequest = {
    displayName: profileForm.displayName || null,
    phone: profileForm.phone || null,
    bio: profileForm.bio || null,
    gender: profileForm.gender,
    birthday: profileForm.birthday || null,
    avatarFileId: profileForm.avatarFileId || null,
  }

  if (isStudentProfile.value) {
    payload.studentInfo = {
      grade: profileForm.grade,
      major: profileForm.major,
      school: profileForm.school,
    }
  }

  if (isTeacherProfile.value) {
    payload.teacherInfo = {
      department: profileForm.department,
      title: profileForm.teacherTitle,
      school: profileForm.school,
    }
  }

  return payload
}

function resetProfileForm() {
  profileForm.displayName = authStore.user?.displayName ?? ''
  profileForm.email = authStore.user?.email ?? ''
  profileForm.phone = authStore.user?.phone ?? ''
  profileForm.bio = authStore.user?.bio ?? ''
  profileForm.gender = authStore.user?.gender ?? 0
  profileForm.birthday = authStore.user?.birthday ?? ''
  profileForm.avatarFileId = authStore.user?.avatarFileId ?? ''
  profileForm.studentNo = authStore.user?.studentInfo?.studentNo ?? ''
  profileForm.grade = authStore.user?.studentInfo?.grade ?? ''
  profileForm.major = authStore.user?.studentInfo?.major ?? ''
  profileForm.employeeNo = authStore.user?.teacherInfo?.employeeNo ?? ''
  profileForm.department = authStore.user?.teacherInfo?.department ?? ''
  profileForm.teacherTitle = authStore.user?.teacherInfo?.title ?? ''
  profileForm.school = authStore.user?.studentInfo?.school ?? authStore.user?.teacherInfo?.school ?? ''
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

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirm) {
    passwordMessage.value = t('settings.alert.passwordMismatch')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    passwordMessage.value = t('settings.alert.passwordTooShort')
    return
  }
  if (hasLocalPassword.value && !passwordForm.current) {
    passwordMessage.value = t('settings.alert.currentPasswordRequired')
    return
  }

  const wasSettingInitialPassword = !hasLocalPassword.value
  savingPassword.value = true
  passwordMessage.value = ''
  try {
    const updatedUser = await changeCurrentUserPassword({
      currentPassword: hasLocalPassword.value ? passwordForm.current : null,
      newPassword: passwordForm.newPassword,
    })
    authStore.setUser(updatedUser)
    passwordForm.current = ''
    passwordForm.newPassword = ''
    passwordForm.confirm = ''
    passwordMessage.value = t(wasSettingInitialPassword ? 'settings.alert.passwordSet' : 'settings.alert.passwordUpdated')
    notify.success(passwordMessage.value)
  } catch (error) {
    const msg = error instanceof Error ? error.message : t('settings.alert.passwordUpdateFailed')
    passwordMessage.value = msg
    notify.error(msg)
  } finally {
    savingPassword.value = false
  }
}
</script>

<style scoped src="../styles/settings-page.css"></style>
