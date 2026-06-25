import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {
    loginWithGitHub,
    loginWithGoogle,
    loginWithPassword,
    logout as logoutRequest,
    register
} from '@/features/auth/api/auth'
import type {LoginResponse, PasswordLoginRequest, RegisterRequest} from '@/features/auth/types/auth'
import {getCurrentUser} from '@/features/user/api/user'
import type {UserProfile} from '@/features/user/types/user'
import {markVoluntaryLogoutInProgress, resetSessionExpiredHandling, SESSION_CLEARED_EVENT} from '@/shared/api/request'
import {closeAllSseConnections} from '@/shared/api/sseManager'

const ACCESS_TOKEN_KEY = 'edupivot.accessToken'
const REFRESH_TOKEN_KEY = 'edupivot.refreshToken'
const TOKEN_TYPE_KEY = 'edupivot.tokenType'
const USER_KEY = 'edupivot.user'

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

export const useAuthStore = defineStore('auth', () => {
    const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
    const refreshToken = ref(localStorage.getItem(REFRESH_TOKEN_KEY) ?? '')
    const tokenType = ref(localStorage.getItem(TOKEN_TYPE_KEY) ?? 'Bearer')
    const user = ref<UserProfile | null>(readStoredUser())

    const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

    function persistSession(payload: LoginResponse) {
        resetSessionExpiredHandling()
        accessToken.value = payload.accessToken
        refreshToken.value = payload.refreshToken
        tokenType.value = payload.tokenType || 'Bearer'
        if (payload.user) {
            user.value = payload.user
            localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
        }

        localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
        localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
        localStorage.setItem(TOKEN_TYPE_KEY, tokenType.value)
    }

    function setUser(profile: UserProfile) {
        user.value = profile
        localStorage.setItem(USER_KEY, JSON.stringify(profile))
    }

    async function googleLogin(code: string, redirectUri?: string) {
        const payload = await loginWithGoogle({code, redirectUri})
        persistSession(payload)
    }

    async function githubLogin(
        code: string,
        redirectUri?: string,
        codeVerifier?: string,
    ) {
        const payload = await loginWithGitHub({code, redirectUri, codeVerifier})
        persistSession(payload)
        setUser(await getCurrentUser())
    }

    async function passwordLogin(request: PasswordLoginRequest) {
        const payload = await loginWithPassword(request)
        persistSession(payload)
    }

    async function registerUser(request: RegisterRequest) {
        const payload = await register(request)
        persistSession(payload)
    }

    function clearSession() {
        closeAllSseConnections()
        accessToken.value = ''
        refreshToken.value = ''
        tokenType.value = 'Bearer'
        user.value = null

        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
        localStorage.removeItem(TOKEN_TYPE_KEY)
        localStorage.removeItem(USER_KEY)
        sessionStorage.removeItem('enrollmentSuppressConfirm')
    }

    window.addEventListener(SESSION_CLEARED_EVENT, () => {
        if (!accessToken.value && !refreshToken.value && !user.value) return

        clearSession()
    })

    async function logout() {
        const currentRefreshToken = refreshToken.value

        markVoluntaryLogoutInProgress()
        try {
            await logoutRequest(currentRefreshToken || undefined)
        } catch {
            // Local session should be cleared even if the server-side revoke fails.
        } finally {
            clearSession()
        }
    }

    return {
        accessToken,
        refreshToken,
        tokenType,
        user,
        isAuthenticated,
        googleLogin,
        githubLogin,
        passwordLogin,
        registerUser,
        setUser,
        clearSession,
        logout,
    }
})
