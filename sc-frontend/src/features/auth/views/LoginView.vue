<template>
  <main class="login-page">
    <!-- Left Panel: Video Background & Branding (Desktop Only) -->
    <div class="left-panel">
      <video
        v-if="showLoginVideo"
        :src="loginVideoSrc"
        class="bg-video"
        autoplay
        loop
        muted
        playsinline
      ></video>
      <div v-if="uiPreferences.resolvedTheme === 'dark'" class="dark-video-overlay"></div>
      <div class="left-brand">
        <img class="brand-logo-large" src="/assets/project-logo-dark.png" alt="SapientiaCloud EduPivot"/>
        <h1 class="brand-title">
          智语·云枢<br/>SapientiaCloud EduPivot
        </h1>
      </div>
    </div>

    <!-- Right Panel: Login Form -->
    <div class="right-panel">
      <!-- Preferences Top Right -->
      <div class="login-preferences">
        <button
          type="button"
          class="preference-icon-button"
          :aria-label="t('settings.theme')"
          :title="t('settings.theme')"
          @click="cycleThemePreference"
        >
          <component :is="themePreferenceIcon" :size="19" stroke-width="1.8"/>
        </button>
        <button
          type="button"
          class="preference-icon-button"
          :aria-label="t('common.settings.language')"
          :title="t('common.settings.language')"
          @click="toggleLocale"
        >
          <span class="language-glyph">{{ languageGlyph }}</span>
        </button>
      </div>

      <!-- Login Container -->
      <div class="form-container">
        <!-- Header -->
        <div class="form-header">
          <h2 class="form-title">
            <template v-if="isRegister">{{ t('login.createAccountLine1') }}<br/>{{ t('login.createAccountLine2') }}</template>
            <template v-else>{{ t('login.welcomeBackLine1') }}<br/>{{ t('login.welcomeBackLine2') }}</template>
          </h2>
          <p class="form-subtitle">
            {{ isRegister ? t('login.joinCommunity') : t('login.signInContinue') }}
          </p>
        </div>

        <!-- Login Form -->
        <form class="login-form" :class="{ 'is-register': isRegister }" @submit.prevent="handleSubmit">
          <div v-if="isRegister" class="form-field">
            <input
              v-model="formData.displayName"
              class="input-underline"
              :placeholder="t('login.placeholderDisplayName')"
              type="text"
            />
          </div>

          <div class="form-field">
            <input
              v-model="formData.email"
              class="input-underline"
              :placeholder="t('login.placeholderEmail')"
              type="email"
              autocomplete="email"
            />
          </div>

          <div class="form-field">
            <input
              v-model="formData.password"
              class="input-underline"
              :placeholder="t('login.placeholderPassword')"
              type="password"
              autocomplete="current-password"
            />
            <div v-if="!isRegister" class="forgot-link">
              <button type="button" @click="showForgotPasswordUnavailable">
                {{ t('login.forgotPassword') }}
              </button>
            </div>
          </div>

          <div v-if="isRegister" class="form-field">
            <input
              v-model="formData.confirmPassword"
              class="input-underline"
              :placeholder="t('login.placeholderConfirmPassword')"
              type="password"
            />
          </div>

          <!-- Role Selection (Register only) -->
          <div v-if="isRegister" class="role-selection">
            <label class="role-label">{{ t('login.roleLabel') }}</label>
            <div class="role-cards">
              <button
                type="button"
                class="role-card"
                :class="{ active: formData.role === 1 }"
                @click="formData.role = 1"
              >
                <GraduationCap :size="18" stroke-width="1.8"/>
                <span class="role-name">{{ t('login.roleStudent') }}</span>
              </button>
              <button
                type="button"
                class="role-card"
                :class="{ active: formData.role === 2 }"
                @click="formData.role = 2"
              >
                <BookOpen :size="18" stroke-width="1.8"/>
                <span class="role-name">{{ t('login.roleTeacher') }}</span>
              </button>
            </div>
          </div>

          <button
            :disabled="passwordLoading"
            class="btn-submit"
            type="submit"
          >
            {{ passwordLoading ? t('login.loading') : (isRegister ? t('login.createAccountBtn') : t('login.signIn')) }}
          </button>
        </form>

        <!-- Switch Mode -->
        <div class="switch-mode">
          <button class="btn-switch" type="button" @click="isRegister = !isRegister">
            {{ isRegister ? t('login.switchToLogin') : t('login.switchToRegister') }}
          </button>
        </div>

        <!-- Divider -->
        <div class="divider">
          <span>{{ t('login.or') }}</span>
        </div>

        <!-- Social Login -->
        <div class="social-login">
          <button
            :disabled="githubLoading"
            class="btn-social"
            @click="startGitHubLogin"
          >
            <svg
              aria-hidden="true"
              class="social-icon"
              fill="none"
              focusable="false"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.5"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/>
            </svg>
            <span>{{ githubLoading ? t('login.loading') : 'GitHub' }}</span>
          </button>
          <button
            class="btn-social"
            disabled
          >
            <svg
              aria-hidden="true"
              class="social-icon"
              fill="none"
              focusable="false"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.5"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            <span>Google</span>
          </button>
        </div>
      </div>
    </div>
  </main>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {BookOpen, GraduationCap, Monitor, Moon, Sun} from 'lucide-vue-next'

import {ApiError} from '@/shared/api/request'
import {notify} from '@/shared/composables/useGlobalNotification'
import {setLocale} from '@/app/i18n'
import {useAuthStore} from '@/features/auth/stores/auth'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import type {ThemePreference} from '@/features/user/types/user'

const {t, locale} = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const uiPreferences = useUiPreferencesStore()

const GITHUB_AUTHORIZE_URL = 'https://github.com/login/oauth/authorize'
const OAUTH_STATE_KEY = 'edupivot.oauth.state'
const OAUTH_PROVIDER_KEY = 'edupivot.oauth.provider'
const OAUTH_REDIRECT_KEY = 'edupivot.oauth.redirect'

const githubLoading = ref(false)
const passwordLoading = ref(false)
const isRegister = ref(false)
const showLoginVideo = ref(false)
const loginVideoSrc = computed(() =>
  uiPreferences.resolvedTheme === 'dark'
    ? '/assets/login-bg-dark.mp4'
    : '/assets/login-bg-light.mp4',
)
const themePreferenceIcon = computed(() => {
  if (uiPreferences.themePreference === 'light') {
    return Sun
  }
  if (uiPreferences.themePreference === 'dark') {
    return Moon
  }
  return Monitor
})
const languageGlyph = computed(() => locale.value === 'zh-CN' ? '中' : 'EN')

const formData = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  role: 1,
})

function resolveTargetRoute() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' ? redirect : '/dashboard'
}

function resolveGitHubRedirectUri() {
  return import.meta.env.VITE_GITHUB_REDIRECT_URI || `${window.location.origin}/login`
}

function createOauthState() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function toggleLocale() {
  setLocale(locale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
}

function cycleThemePreference() {
  const nextTheme: Record<ThemePreference, ThemePreference> = {
    system: 'light',
    light: 'dark',
    dark: 'system',
  }
  uiPreferences.setThemePreference(nextTheme[uiPreferences.themePreference])
}

function showForgotPasswordUnavailable() {
  notify.info(t('login.alertForgotPasswordUnavailable'))
}

function validateForm(): boolean {
  if (!formData.email) {
    notify.warn(t('login.alertEmailRequired'))
    return false
  }
  if (!formData.email.includes('@')) {
    notify.warn(t('login.alertEmailInvalid'))
    return false
  }
  if (!formData.password || formData.password.length < 8) {
    notify.warn(t('login.alertPasswordRequired'))
    return false
  }
  if (isRegister.value && formData.password !== formData.confirmPassword) {
    notify.warn(t('login.alertPasswordMismatch'))
    return false
  }
  return true
}

async function handleSubmit() {
  if (!validateForm()) return

  passwordLoading.value = true

  try {
    if (isRegister.value) {
      await authStore.registerUser({
        email: formData.email,
        password: formData.password,
        displayName: formData.displayName || undefined,
        role: formData.role,
      })
      notify.success(t('login.alertRegisterSuccess'))
    } else {
      await authStore.passwordLogin({
        email: formData.email,
        password: formData.password,
      })
      notify.success(t('login.alertLoginSuccess'))
    }
    await router.replace(resolveTargetRoute())
  } catch (error) {
    const errorMessage = error instanceof ApiError ? error.message : t('login.alertLoginFailed')
    notify.error(errorMessage)
  } finally {
    passwordLoading.value = false
  }
}

function startGitHubLogin() {
  const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID

  if (!clientId) {
    notify.warn(t('login.alertGithubNotConfigured'))
    return
  }

  const state = createOauthState()
  sessionStorage.setItem(OAUTH_STATE_KEY, state)
  sessionStorage.setItem(OAUTH_PROVIDER_KEY, 'github')
  sessionStorage.setItem(OAUTH_REDIRECT_KEY, resolveTargetRoute())

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: resolveGitHubRedirectUri(),
    scope: import.meta.env.VITE_GITHUB_SCOPE || 'read:user user:email',
    state,
    allow_signup: 'true',
  })

  window.location.assign(`${GITHUB_AUTHORIZE_URL}?${params.toString()}`)
}

async function finishGitHubLogin(code: string) {
  githubLoading.value = true

  try {
    await authStore.githubLogin(code, resolveGitHubRedirectUri())
    notify.success(t('login.alertLoginSuccess'))
    await router.replace(sessionStorage.getItem(OAUTH_REDIRECT_KEY) || '/dashboard')
  } catch (error) {
    const errorMessage =
      error instanceof ApiError ? error.message : t('login.alertGithubLoginFailed')
    notify.error(errorMessage)
  } finally {
    sessionStorage.removeItem(OAUTH_STATE_KEY)
    sessionStorage.removeItem(OAUTH_PROVIDER_KEY)
    sessionStorage.removeItem(OAUTH_REDIRECT_KEY)
    githubLoading.value = false
  }
}

onMounted(() => {
  showLoginVideo.value = true

  const code = route.query.code
  const state = route.query.state
  const savedState = sessionStorage.getItem(OAUTH_STATE_KEY)
  const savedProvider = sessionStorage.getItem(OAUTH_PROVIDER_KEY)

  if (typeof code !== 'string' || savedProvider !== 'github') {
    return
  }

  if (savedState && state !== savedState) {
    notify.error(t('login.alertOAuthStateInvalid'))
    return
  }

  void finishGitHubLogin(code)
})
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  background: var(--login-page-bg);
}

/* ---- Left Panel ---- */

.left-panel {
  display: none;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 40%;
  background: var(--login-left-bg);
  align-items: center;
  justify-content: center;
  overflow: hidden;
  z-index: 10;
}

.bg-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dark-video-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  background: rgba(0, 0, 0, 0.15);
}

.left-brand {
  position: relative;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px;
  transform: translateY(-96px);
}

.brand-logo-large {
  width: min(46vw, 360px);
  max-width: 72%;
  height: auto;
  margin-bottom: 28px;
  object-fit: contain;
}

.brand-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 600;
  color: #ffffff;
  line-height: 1.2;
  letter-spacing: 0;
}

/* ---- Right Panel ---- */

.right-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 32px;
  background: var(--login-panel-bg);
  overflow-y: auto;
  height: 100vh;
}

.login-preferences {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
  margin-bottom: 48px;
}

.preference-icon-button {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--login-border);
  border-radius: 50%;
  background: var(--login-panel-bg);
  color: var(--login-text);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.2s;
}

.preference-icon-button:hover {
  background: var(--login-hover-bg);
  border-color: var(--login-text);
  transform: translateY(-1px);
}

.preference-icon-button:focus-visible {
  outline: none;
  border-color: var(--login-text);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--login-text) 12%, transparent);
}

.language-glyph {
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
}

.form-container {
  width: 100%;
  max-width: 420px;
  margin: 0 auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* ---- Header ---- */

.form-header {
  text-align: left;
  margin-bottom: 48px;
}

.form-title {
  margin: 0 0 24px;
  font-family: var(--font-heading);
  font-size: 64px;
  font-weight: 700;
  color: var(--login-text);
  line-height: 1.1;
  letter-spacing: 0;
  text-transform: uppercase;
}

.form-subtitle {
  margin: 0;
  font-family: var(--font-body);
  font-size: 16px;
  color: var(--login-muted);
  line-height: 1.5;
}

/* ---- Form ---- */

.login-form {
  margin-bottom: 32px;
}

.form-field {
  margin-bottom: 32px;
  position: relative;
}

.input-underline {
  width: 100%;
  padding: 12px 0;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--login-field-border);
  font-family: var(--font-body);
  font-size: 16px;
  color: var(--login-text);
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.input-underline:focus {
  border-bottom-color: var(--login-text);
}

.input-underline::placeholder {
  color: var(--login-muted);
}

.forgot-link {
  position: absolute;
  right: 0;
  top: -24px;
}

.forgot-link button {
  padding: 0;
  background: transparent;
  border: 0;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--login-text);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s;
}

.forgot-link button:hover {
  color: var(--login-muted);
}

/* ---- Submit Button ---- */

.btn-submit {
  width: 100%;
  padding: 16px 24px;
  background: var(--login-action-bg);
  color: var(--login-action-text);
  border: none;
  border-radius: 9999px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 16px;
}

.btn-submit:hover:not(:disabled) {
  background: var(--login-action-hover-bg);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ---- Register Compact Mode ---- */

.login-form.is-register {
  margin-bottom: 20px;
}

.login-form.is-register .form-field {
  margin-bottom: 16px;
}

.login-form.is-register .input-underline {
  padding: 10px 0;
  font-size: 15px;
}

.login-form.is-register .btn-submit {
  margin-top: 12px;
  padding: 14px 24px;
}

/* ---- Role Selection ---- */

.role-selection {
  margin-bottom: 20px;
}

.role-label {
  display: block;
  margin-bottom: 10px;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 600;
  color: var(--login-text);
}

.role-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.role-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: var(--login-panel-bg);
  border: 1px solid var(--login-text);
  border-radius: 12px;
  color: var(--login-text);
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.role-card:hover {
  background: var(--login-hover-bg);
}

.role-card.active {
  background: var(--login-action-bg);
  color: var(--login-action-text);
}

.role-card.active :deep(svg) {
  color: var(--login-action-text);
}

.role-name {
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 600;
}

/* ---- Switch Mode ---- */

.switch-mode {
  text-align: center;
  margin-bottom: 32px;
}

.btn-switch {
  background: none;
  border: none;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--login-muted);
  cursor: pointer;
  transition: color 0.2s;
}

.btn-switch:hover {
  color: var(--login-text);
}

/* ---- Divider ---- */

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--login-field-border);
}

.divider span {
  padding: 0 16px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--login-muted);
  background: var(--login-panel-bg);
  position: relative;
  z-index: 1;
}

/* ---- Social Login ---- */

.social-login {
  display: flex;
  gap: 16px;
}

.btn-social {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px 16px;
  background: transparent;
  border: 1px solid var(--login-field-border);
  border-radius: 9999px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--login-text);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-social:hover:not(:disabled) {
  background: var(--login-hover-bg);
  border-color: var(--login-text);
}

.btn-social:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.social-icon {
  width: 20px;
  height: 20px;
}

/* ---- Responsive ---- */

@media (min-width: 768px) {
  .left-panel {
    display: flex;
  }

  .right-panel {
    margin-left: 40%;
    width: 60%;
    padding: 48px;
  }
}

@media (min-width: 1024px) {
  .right-panel {
    padding: 64px;
  }

  .form-title {
    font-size: 72px;
  }
}

@media (max-width: 640px) {
  .right-panel {
    padding: 24px;
  }

  .login-preferences {
    flex-wrap: wrap;
    margin-bottom: 36px;
  }

  .form-title {
    font-size: 48px;
  }

  .social-login {
    flex-direction: column;
  }

  .role-cards {
    grid-template-columns: 1fr;
  }
}
</style>
