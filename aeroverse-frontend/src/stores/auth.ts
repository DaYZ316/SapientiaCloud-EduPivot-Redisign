import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { loginWithGitHub, loginWithGoogle } from '@/api/auth'
import type { LoginResponse } from '@/types/auth'
import type { UserProfile } from '@/types/user'

const ACCESS_TOKEN_KEY = 'aeroverse.accessToken'
const TOKEN_TYPE_KEY = 'aeroverse.tokenType'
const USER_KEY = 'aeroverse.user'

function readStoredUser() {
  const rawUser = localStorage.getItem(USER_KEY)

  if (!rawUser) {
    return null
  }

  try {
    return JSON.parse(rawUser) as UserProfile
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

function createDemoLogin(): LoginResponse {
  return {
    accessToken: 'demo-access-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'demo@aeroverse.local',
      emailVerified: true,
      displayName: 'AeroVerse Demo User',
      avatarUrl: null,
      locale: 'zh-CN',
      status: 'ACTIVE',
      createdProvider: 'DEMO',
      createdIp: '127.0.0.1',
      lastLoginProvider: 'DEMO',
      lastLoginIp: '127.0.0.1',
      loginCount: 1,
      linkedProviders: ['DEMO'],
    },
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
  const tokenType = ref(localStorage.getItem(TOKEN_TYPE_KEY) ?? 'Bearer')
  const user = ref<UserProfile | null>(readStoredUser())

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  function persistSession(payload: LoginResponse) {
    accessToken.value = payload.accessToken
    tokenType.value = payload.tokenType || 'Bearer'
    user.value = payload.user

    localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
    localStorage.setItem(TOKEN_TYPE_KEY, tokenType.value)
    localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
  }

  async function googleLogin(code: string, redirectUri?: string) {
    const payload = await loginWithGoogle({ code, redirectUri })
    persistSession(payload)
  }

  async function githubLogin(
    code: string,
    redirectUri?: string,
    codeVerifier?: string,
  ) {
    const payload = await loginWithGitHub({ code, redirectUri, codeVerifier })
    persistSession(payload)
  }

  function demoLogin() {
    persistSession(createDemoLogin())
  }

  function logout() {
    accessToken.value = ''
    tokenType.value = 'Bearer'
    user.value = null

    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(TOKEN_TYPE_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    accessToken,
    tokenType,
    user,
    isAuthenticated,
    googleLogin,
    githubLogin,
    demoLogin,
    logout,
  }
})
