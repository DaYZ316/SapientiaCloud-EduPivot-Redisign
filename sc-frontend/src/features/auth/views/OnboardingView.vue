<template>
  <main class="onboarding-page">
    <section class="onboarding-panel">
      <header class="onboarding-header">
        <p class="eyebrow">
          {{ t('login.onboardingEyebrow') }}
        </p>
        <h1>{{ t('login.onboardingTitle') }}</h1>
        <p>{{ t('login.onboardingSubtitle') }}</p>
      </header>

      <form
          class="onboarding-form"
          @submit.prevent="submit"
      >
        <div class="form-group">
          <label for="onboarding-display-name">{{ t('settings.displayName') }}</label>
          <input
              id="onboarding-display-name"
              v-model.trim="displayName"
              :placeholder="t('settings.displayNamePlaceholder')"
              class="input-field"
              type="text"
          >
        </div>

        <div class="form-group">
          <span class="field-label">{{ t('login.roleLabel') }}</span>
          <div class="role-grid">
            <button
                :class="{ active: role === 1 }"
                class="role-option"
                type="button"
                @click="role = 1"
            >
              <GraduationCap
                  :size="20"
                  stroke-width="1.8"
              />
              <span>{{ t('login.roleStudent') }}</span>
            </button>
            <button
                :class="{ active: role === 2 }"
                class="role-option"
                type="button"
                @click="role = 2"
            >
              <BookOpen
                  :size="20"
                  stroke-width="1.8"
              />
              <span>{{ t('login.roleTeacher') }}</span>
            </button>
          </div>
        </div>

        <p
            v-if="message"
            class="form-message"
        >
          {{ message }}
        </p>

        <button
            :disabled="saving"
            class="btn-primary"
            type="submit"
        >
          {{ saving ? t('settings.saving') : t('login.onboardingSubmit') }}
        </button>
      </form>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {BookOpen, GraduationCap} from 'lucide-vue-next'

import {ApiError} from '@/shared/api/request'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useAuthStore} from '@/features/auth/stores/auth'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const displayName = ref(authStore.user?.displayName?.trim() || '')
const role = ref<number | null>(authStore.user?.role === 1 || authStore.user?.role === 2 ? authStore.user.role : null)
const saving = ref(false)
const message = ref('')

function redirectTarget() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect !== '/onboarding' ? redirect : '/dashboard'
}

async function submit() {
  if (!displayName.value) {
    message.value = t('login.onboardingNameRequired')
    notify.warn(message.value)
    return
  }
  if (role.value == null) {
    message.value = t('login.onboardingRoleRequired')
    notify.warn(message.value)
    return
  }

  saving.value = true
  message.value = ''

  try {
    await authStore.completeOnboarding(role.value, displayName.value)
    notify.success(t('login.onboardingSuccess'))
    await router.replace(redirectTarget())
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : t('settings.saveFailed')
    notify.error(message.value)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.onboarding-panel {
  width: min(100%, 520px);
  padding: 32px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  background: var(--color-surface-card);
}

.onboarding-header {
  margin-bottom: 28px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.onboarding-header h1 {
  margin: 0 0 12px;
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 400;
  line-height: 1.15;
  letter-spacing: 0;
}

.onboarding-header p:last-child {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.onboarding-form,
.form-group {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.onboarding-form {
  gap: 22px;
}

.form-group label,
.field-label {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
}

.input-field {
  width: 100%;
  min-height: 46px;
  padding: 0 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  font: inherit;
}

.input-field:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.role-option {
  min-height: 64px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  cursor: pointer;
}

.role-option.active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.form-message {
  margin: 0;
  color: var(--color-error);
  font-size: 14px;
}

.btn-primary {
  min-height: 46px;
  justify-content: center;
}

@media (max-width: 640px) {
  .onboarding-page {
    padding: 20px;
  }

  .onboarding-panel {
    padding: 24px;
  }

  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
