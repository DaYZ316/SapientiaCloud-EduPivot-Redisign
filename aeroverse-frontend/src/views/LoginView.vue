<template>
  <main class="login-page">
    <section :aria-label="t('login.pageAria')" class="login-hero">
      <div class="brand-mark">
        <MapPinned :size="28" stroke-width="2.2"/>
      </div>
      <p class="eyebrow">{{ t('login.brand') }}</p>
      <h1>{{ t('login.title') }}</h1>
      <p class="subtitle">
        {{ t('login.subtitle') }}
      </p>
      <div class="hero-metrics">
        <span>{{ t('login.metrics.vue') }}</span>
        <span>{{ t('login.metrics.typescript') }}</span>
        <span>{{ t('login.metrics.vite') }}</span>
      </div>
    </section>

    <section :aria-label="t('login.panelAria')" class="login-panel">
      <NCard :bordered="false" class="login-card" embedded>
        <div class="panel-heading">
          <h2>{{ t('login.panelTitle') }}</h2>
          <p>{{ t('login.panelDescription') }}</p>
        </div>

        <div class="oauth-buttons">
          <NButton
              :loading="githubLoading"
              block
              class="oauth-button github-button"
              size="large"
              type="primary"
              @click="startGitHubLogin"
          >
            <template #icon>
              <Github :size="19"/>
            </template>
            {{ t('login.githubButton') }}
          </NButton>

          <NButton block class="oauth-button" disabled size="large">
            <template #icon>
              <Chrome :size="19"/>
            </template>
            {{ t('login.googleButton') }}
          </NButton>
        </div>
      </NCard>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {Chrome, Github, MapPinned} from 'lucide-vue-next'
import {NButton, NCard, useMessage} from 'naive-ui'

import {ApiError} from '@/api/request'
import {useAuthStore} from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()
const {t} = useI18n()

const GITHUB_AUTHORIZE_URL = 'https://github.com/login/oauth/authorize'
const OAUTH_STATE_KEY = 'aeroverse.oauth.state'
const OAUTH_PROVIDER_KEY = 'aeroverse.oauth.provider'
const OAUTH_REDIRECT_KEY = 'aeroverse.oauth.redirect'

const githubLoading = ref(false)

function resolveTargetRoute() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' ? redirect : '/success'
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

function startGitHubLogin() {
  const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID

  if (!clientId) {
    message.warning(t('login.githubClientMissing'))
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
    message.success(t('login.loginSuccess'))
    await router.replace(sessionStorage.getItem(OAUTH_REDIRECT_KEY) || '/success')
  } catch (error) {
    const errorMessage =
        error instanceof ApiError ? error.message : t('login.githubLoginFailed')
    message.error(errorMessage)
  } finally {
    sessionStorage.removeItem(OAUTH_STATE_KEY)
    sessionStorage.removeItem(OAUTH_PROVIDER_KEY)
    sessionStorage.removeItem(OAUTH_REDIRECT_KEY)
    githubLoading.value = false
  }
}

onMounted(() => {
  const code = route.query.code
  const state = route.query.state
  const savedState = sessionStorage.getItem(OAUTH_STATE_KEY)
  const savedProvider = sessionStorage.getItem(OAUTH_PROVIDER_KEY)

  if (typeof code !== 'string' || savedProvider !== 'github') {
    return
  }

  if (savedState && state !== savedState) {
    message.error(t('login.githubStateInvalid'))
    return
  }

  void finishGitHubLogin(code)
})
</script>
