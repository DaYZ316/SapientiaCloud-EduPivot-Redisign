import type {AxiosRequestConfig} from 'axios'
import axios, {AxiosError} from 'axios'

import {i18n} from '@/app/i18n'
import {
    clearClientSession,
    getAccessToken,
    getRefreshToken,
    persistClientSession,
    restoreDesktopSession,
} from '@/shared/platform/session'
import {isDesktopApp} from '@/shared/platform/desktop'
import {getRuntimeEnvironment} from '@/shared/platform/runtime'
import {notify} from '@/shared/composables/useGlobalNotification'
import {showSessionExpiredDialog} from '@/shared/composables/useSessionExpiredDialog'
import type {LoginResponse} from '@/features/auth/types/auth'
import type {ApiResponse} from '@/shared/types/common'

const SUCCESS_CODE = 0
const UNAUTHORIZED_CODE = 40100
const FORBIDDEN_CODE = 40300
const PROFILE_INCOMPLETE_CODE = 40310
export {ACCESS_TOKEN_KEY, SESSION_CLEARED_EVENT} from '@/shared/platform/session'
export const PROFILE_INCOMPLETE_EVENT = 'edupivot:profile-incomplete'
export const PROFILE_INCOMPLETE_REDIRECT_KEY = 'edupivot.profileIncompleteRedirect'
let sessionExpiredHandled = false
let voluntaryLogoutInProgress = false
let refreshSessionPromise: Promise<boolean> | null = null

export class ApiError extends Error {
    readonly code?: number

    constructor(message: string, code?: number) {
        super(message)
        this.name = 'ApiError'
        this.code = code
    }
}

export const http = axios.create({
    timeout: 12000,
})

const refreshHttp = axios.create({
    timeout: 12000,
})

http.interceptors.request.use((config) => {
    config.baseURL = getRuntimeEnvironment().apiOrigin
    const token = getAccessToken()

    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }

    return config
})

refreshHttp.interceptors.request.use((config) => {
    config.baseURL = getRuntimeEnvironment().apiOrigin
    return config
})

export interface RequestOptions extends AxiosRequestConfig {
    /** 设为 true 则不自动弹出全局错误通知，由调用方自行处理 */
    silent?: boolean
    suppressSessionExpiredDialog?: boolean
}

export async function request<T>(config: RequestOptions, retryOnUnauthorized = true): Promise<T> {
    const {silent, suppressSessionExpiredDialog, ...axiosConfig} = config
    const requestAccessToken = getAccessToken() || null

    try {
        const response = await http.request<ApiResponse<T>>(axiosConfig)
        const payload = response.data

        if (payload.code !== SUCCESS_CODE) {
            throw new ApiError(payload.message || i18n.global.t('common.request.failed'), payload.code)
        }

        return payload.data
    } catch (error) {
        if (error instanceof ApiError) {
            if (retryOnUnauthorized && error.code === UNAUTHORIZED_CODE) {
                if (hasAccessTokenChanged(requestAccessToken) || await refreshSession()) {
                    return request<T>(config, false)
                }
            }
            if (error.code === PROFILE_INCOMPLETE_CODE) {
                handleProfileIncomplete()
            } else if (isAuthenticationExpired(error.code)) {
                handleSessionExpired(suppressSessionExpiredDialog)
            } else if (!silent) {
                notifyRequestError(error.message, error.code)
            }
            throw error
        }

        const axiosError = error as AxiosError<ApiResponse<unknown>>
        const errorCode = axiosError.response?.data?.code
        if (retryOnUnauthorized
            && isAuthenticationExpired(errorCode, axiosError.response?.status)) {
            if (hasAccessTokenChanged(requestAccessToken) || await refreshSession()) {
                return request<T>(config, false)
            }
        }

        const message = resolveErrorMessage(axiosError)
        if (errorCode === PROFILE_INCOMPLETE_CODE) {
            handleProfileIncomplete()
        } else if (isAuthenticationExpired(errorCode, axiosError.response?.status)) {
            handleSessionExpired(suppressSessionExpiredDialog)
        } else if (!silent) {
            notifyRequestError(message, errorCode, axiosError.response?.status)
        }
        throw new ApiError(message, errorCode)
    }
}

export function resetSessionExpiredHandling() {
    sessionExpiredHandled = false
    voluntaryLogoutInProgress = false
}

export function markVoluntaryLogoutInProgress() {
    voluntaryLogoutInProgress = true
}

function isAuthenticationExpired(code?: number, status?: number) {
    if (code === UNAUTHORIZED_CODE) {
        return true
    }

    return status === 401 && code == null
}

function hasAccessTokenChanged(requestAccessToken: string | null) {
    const currentAccessToken = getAccessToken()

    return Boolean(requestAccessToken && currentAccessToken && currentAccessToken !== requestAccessToken)
}

function handleSessionExpired(suppressDialog = false) {
    clearSession()
    if (suppressDialog || voluntaryLogoutInProgress) {
        return
    }

    if (sessionExpiredHandled) {
        return
    }

    sessionExpiredHandled = true
    showSessionExpiredDialog()
}

function handleProfileIncomplete() {
    if (typeof window !== 'undefined') {
        const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
        if (currentPath !== '/onboarding') {
            sessionStorage.setItem(PROFILE_INCOMPLETE_REDIRECT_KEY, currentPath)
        }
        window.dispatchEvent(new Event(PROFILE_INCOMPLETE_EVENT))
    }
}

function notifyRequestError(message: string, code?: number, status?: number) {
    if (code === FORBIDDEN_CODE || status === 403) {
        notify.warn(message)
        return
    }

    notify.error(message)
}

function resolveErrorMessage(error: AxiosError<ApiResponse<unknown>>): string {
    if (!navigator.onLine) {
        return i18n.global.t('common.request.offline')
    }

    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        return i18n.global.t('common.request.timeout')
    }

    if (!error.response) {
        return i18n.global.t('common.request.networkError')
    }

    return error.response.data?.message
        || error.message
        || i18n.global.t('common.request.networkError')
}

export async function refreshSession() {
    if (voluntaryLogoutInProgress) {
        return false
    }

    if (refreshSessionPromise) {
        return refreshSessionPromise
    }

    refreshSessionPromise = performRefreshSession()
    try {
        return await refreshSessionPromise
    } finally {
        refreshSessionPromise = null
    }
}

async function performRefreshSession() {
    if (isDesktopApp()) {
        const payload = await restoreDesktopSession()
        return Boolean(payload?.accessToken)
    }

    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        return false
    }

    try {
        const response = await refreshHttp.request<ApiResponse<LoginResponse>>({
            method: 'POST',
            url: '/api/auth/refresh',
            data: {refreshToken},
        })
        if (response.data.code !== SUCCESS_CODE || !response.data.data?.accessToken) {
            clearSession()
            return false
        }
        if (voluntaryLogoutInProgress) {
            clearSession()
            return false
        }

        await persistSession(response.data.data)
        return true
    } catch {
        clearSession()
        return false
    }
}

async function persistSession(payload: LoginResponse) {
    sessionExpiredHandled = false
    await persistClientSession(payload)
}

function clearSession() {
    clearClientSession()
}
