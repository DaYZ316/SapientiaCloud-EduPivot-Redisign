import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {
    completeOnboarding as completeOnboardingRequest,
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
import {
    clearClientSession,
    getAccessToken,
    getRefreshToken,
    getTokenType,
    persistClientSession,
    restoreDesktopSession,
    restoreMobileSession,
    SESSION_UPDATED_EVENT,
} from '@/shared/platform/session'
import {desktopBridge, isDesktopApp} from '@/shared/platform/desktop'
import {isMobileApp} from '@/shared/platform/mobile'

const USER_KEY = 'edupivot.user'

function readStoredUser() {
    if (isDesktopApp()) {
        return null
    }

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
    const accessToken = ref(getAccessToken())
    const refreshToken = ref(getRefreshToken())
    const tokenType = ref(getTokenType())
    const user = ref<UserProfile | null>(readStoredUser())

    const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

    async function persistSession(payload: LoginResponse) {
        resetSessionExpiredHandling()
        await persistClientSession(payload)
        syncSessionTokens()
        if (payload.user) {
            setUser(payload.user)
        }
    }

    function setUser(profile: UserProfile) {
        user.value = profile
        if (!isDesktopApp()) {
            localStorage.setItem(USER_KEY, JSON.stringify(profile))
        }
    }

    async function googleLogin(code: string, redirectUri?: string) {
        const payload = await loginWithGoogle({code, redirectUri})
        await persistSession(payload)
    }

    async function githubLogin(
        code: string,
        redirectUri?: string,
        codeVerifier?: string,
    ) {
        const payload = await loginWithGitHub({code, redirectUri, codeVerifier})
        await persistSession(payload)
        setUser(await getCurrentUser())
    }

    async function passwordLogin(request: PasswordLoginRequest) {
        const payload = await loginWithPassword(request)
        await persistSession(payload)
    }

    async function registerUser(request: RegisterRequest) {
        const payload = await register(request)
        await persistSession(payload)
    }

    async function completeOnboarding(role: number, displayName: string) {
        const payload = await completeOnboardingRequest({role, displayName})
        await persistSession(payload)
    }

    function clearSession() {
        closeAllSseConnections()
        accessToken.value = ''
        refreshToken.value = ''
        tokenType.value = 'Bearer'
        user.value = null

        clearClientSession(false)
        localStorage.removeItem(USER_KEY)
        sessionStorage.removeItem('enrollmentSuppressConfirm')
    }

    window.addEventListener(SESSION_CLEARED_EVENT, () => {
        if (!accessToken.value && !refreshToken.value && !user.value) return

        clearSession()
    })

    window.addEventListener(SESSION_UPDATED_EVENT, () => {
        syncSessionTokens()
    })

    async function restoreSession() {
        if (!isDesktopApp() && !isMobileApp()) {
            return
        }

        try {
            const payload = isDesktopApp()
                ? await restoreDesktopSession()
                : await restoreMobileSession()
            if (!payload) {
                return
            }
            syncSessionTokens()
            if (payload.user) {
                setUser(payload.user as UserProfile)
            } else {
                setUser(await getCurrentUser())
            }
        } catch {
            clearSession()
        }
    }

    function syncSessionTokens() {
        accessToken.value = getAccessToken()
        refreshToken.value = getRefreshToken()
        tokenType.value = getTokenType()
    }

    async function logout() {
        const currentRefreshToken = refreshToken.value

        markVoluntaryLogoutInProgress()
        try {
            if (isDesktopApp()) {
                await desktopBridge()?.session.logout()
            } else {
                await logoutRequest(currentRefreshToken || undefined)
            }
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
        completeOnboarding,
        setUser,
        restoreSession,
        clearSession,
        logout,
    }
})
