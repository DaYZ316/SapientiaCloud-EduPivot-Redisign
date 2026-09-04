<template>
  <main class="oauth-return-page">
    <section class="oauth-return-card">
      <template v-if="nativeReturn">
        <h1>正在返回 EduPivot</h1>
        <p>授权结果将发送回客户端。若没有自动打开，请点击下方按钮。</p>
        <button class="return-button" type="button" @click="returnToNativeApp">返回客户端</button>
      </template>
      <template v-else>
        <h1>{{ statusTitle }}</h1>
        <p>{{ statusDescription }}</p>
      </template>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import {consumeWebGitHubCallback, readGitHubOAuthCallback} from '@/features/auth/githubOAuth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const statusTitle = ref('正在完成 GitHub 登录')
const statusDescription = ref('请稍候…')

const callback = computed(() => readGitHubOAuthCallback(route.query))
const nativeReturn = computed(() => callback.value.state.startsWith('desktop.') || callback.value.state.startsWith('mobile.'))

onMounted(() => {
  if (nativeReturn.value) {
    window.setTimeout(returnToNativeApp, 80)
    return
  }
  void finishWebLogin()
})

async function finishWebLogin() {
  const received = callback.value
  // The authorization code must not remain in the browser's visible history
  // while the token exchange is in progress.
  window.history.replaceState(null, document.title, window.location.pathname)

  try {
    const completion = consumeWebGitHubCallback(received)
    await authStore.githubLogin(completion.code, completion.redirectUri, completion.codeVerifier)
    statusTitle.value = 'GitHub 登录成功'
    statusDescription.value = '正在进入 EduPivot…'
    await router.replace(completion.target)
  } catch (error) {
    statusTitle.value = 'GitHub 登录未完成'
    statusDescription.value = error instanceof Error ? error.message : '请返回登录页后重新尝试。'
    window.setTimeout(() => {
      void router.replace('/login')
    }, 2400)
  }
}

function returnToNativeApp() {
  const params = new URLSearchParams()
  ;['code', 'state', 'error', 'error_description'].forEach((key) => {
    const value = route.query[key]
    if (typeof value === 'string') {
      params.set(key, value)
    }
  })
  window.history.replaceState(null, document.title, window.location.pathname)
  window.location.assign(`edupivot://oauth/callback?${params.toString()}`)
}
</script>

<style scoped>
.oauth-return-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--login-page-bg);
}

.oauth-return-card {
  width: min(100%, 460px);
  padding: 40px;
  border: 1px solid var(--login-border);
  border-radius: 20px;
  background: var(--login-panel-bg);
  color: var(--login-text);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.12);
}

h1 {
  margin: 0 0 16px;
  font-family: var(--font-heading);
  font-size: 30px;
}

p {
  margin: 0;
  color: var(--login-muted);
  line-height: 1.6;
}

.return-button {
  width: 100%;
  margin-top: 28px;
  padding: 13px 18px;
  border: 0;
  border-radius: 10px;
  background: var(--login-text);
  color: var(--login-panel-bg);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}
</style>
