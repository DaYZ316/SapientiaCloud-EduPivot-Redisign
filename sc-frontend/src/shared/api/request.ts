import type {AxiosRequestConfig} from 'axios'
import axios, {AxiosError} from 'axios'

import {i18n} from '@/app/i18n'
import {notify} from '@/shared/composables/useGlobalNotification'
import {showSessionExpiredDialog} from '@/shared/composables/useSessionExpiredDialog'
import type {LoginResponse} from '@/features/auth/types/auth'
import type {ApiResponse} from '@/shared/types/common'

const SUCCESS_CODE = 0
const UNAUTHORIZED_CODE = 40100
const FORBIDDEN_CODE = 40300
const PROFILE_INCOMPLETE_CODE = 40310
export const ACCESS_TOKEN_KEY = 'edupivot.accessToken'
export const SESSION_CLEARED_EVENT = 'edupivot:session-cleared'
export const PROFILE_INCOMPLETE_EVENT = 'edupivot:profile-incomplete'
export const PROFILE_INCOMPLETE_REDIRECT_KEY = 'edupivot.profileIncompleteRedirect'
const REFRESH_TOKEN_KEY = 'edupivot.refreshToken'
const TOKEN_TYPE_KEY = 'edupivot.tokenType'
const USER_KEY = 'edupivot.user'
let sessionExpiredHandled = false
let voluntaryLogoutInProgress = false

export class ApiError extends Error {
    readonly code?: number

    constructor(message: string, code?: number) {
        super(message)
        this.name = 'ApiError'
        this.code = code
    }
}

export const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
    timeout: 12000,
})

const refreshHttp = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
    timeout: 12000,
})

http.interceptors.request.use((config) => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)

    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }

    return config
})

export interface RequestOptions extends AxiosRequestConfig {
    /** 设为 true 则不自动弹出全局错误通知，由调用方自行处理 */
    silent?: boolean
    suppressSessionExpiredDialog?: boolean
}

export async function request<T>(config: RequestOptions, retryOnUnauthorized = true): Promise<T> {
    const {silent, suppressSessionExpiredDialog, ...axiosConfig} = config

    try {
        const response = await http.request<ApiResponse<T>>(axiosConfig)
        const payload = response.data

        if (payload.code !== SUCCESS_CODE) {
            throw new ApiError(payload.message || i18n.global.t('common.request.failed'), payload.code)
        }

        return payload.data
    } catch (error) {
        if (error instanceof ApiError) {
            if (retryOnUnauthorized && error.code === UNAUTHORIZED_CODE && await refreshSession()) {
                return request<T>(config, false)
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
            && isAuthenticationExpired(errorCode, axiosError.response?.status)
            && await refreshSession()) {
            return request<T>(config, false)
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

    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
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

        persistSession(response.data.data)
        return true
    } catch {
        clearSession()
        return false
    }
}

function persistSession(payload: LoginResponse) {
    sessionExpiredHandled = false
    localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
    localStorage.setItem(TOKEN_TYPE_KEY, payload.tokenType || 'Bearer')
    if (payload.user) {
        localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
    }
}

function clearSession() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(TOKEN_TYPE_KEY)
    localStorage.removeItem(USER_KEY)
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(SESSION_CLEARED_EVENT))
    }
}
