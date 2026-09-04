import type {LoginResponse} from '@/features/auth/types/auth'
import {desktopBridge, isDesktopApp} from '@/shared/platform/desktop'
import {isMobileApp} from '@/shared/platform/mobile'
import {
    readMobileSecureValue,
    removeMobileSecureValue,
    writeMobileSecureValue,
} from '@/shared/platform/mobileSecureStore'
import {getRuntimeEnvironment} from '@/shared/platform/runtime'

export const ACCESS_TOKEN_KEY = 'edupivot.accessToken'
export const SESSION_CLEARED_EVENT = 'edupivot:session-cleared'
export const SESSION_UPDATED_EVENT = 'edupivot:session-updated'

const REFRESH_TOKEN_KEY = 'edupivot.refreshToken'
const TOKEN_TYPE_KEY = 'edupivot.tokenType'
const MOBILE_REFRESH_TOKEN_KEY = 'refresh-token'

let desktopAccessToken = ''
let desktopTokenType = 'Bearer'
let mobileAccessToken = ''
let mobileRefreshToken = ''
let mobileTokenType = 'Bearer'

export function getAccessToken() {
    if (isDesktopApp()) {
        return desktopAccessToken
    }
    if (isMobileApp()) {
        return mobileAccessToken
    }
    return localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
}

export function getRefreshToken() {
    if (isDesktopApp()) {
        return ''
    }
    if (isMobileApp()) {
        return mobileRefreshToken
    }
    return localStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
}

export function getTokenType() {
    if (isDesktopApp()) {
        return desktopTokenType
    }
    if (isMobileApp()) {
        return mobileTokenType
    }
    return localStorage.getItem(TOKEN_TYPE_KEY) ?? 'Bearer'
}

export async function persistClientSession(payload: LoginResponse) {
    desktopAccessToken = payload.accessToken
    desktopTokenType = payload.tokenType || 'Bearer'

    if (isDesktopApp()) {
        await desktopBridge()?.session.storeRefreshToken(payload.refreshToken)
    } else if (isMobileApp()) {
        mobileAccessToken = payload.accessToken
        mobileRefreshToken = payload.refreshToken
        mobileTokenType = desktopTokenType
        await writeMobileSecureValue(MOBILE_REFRESH_TOKEN_KEY, payload.refreshToken)
    } else {
        localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
        localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
        localStorage.setItem(TOKEN_TYPE_KEY, desktopTokenType)
    }
    dispatchSessionEvent(SESSION_UPDATED_EVENT)
}

export async function restoreDesktopSession() {
    const desktop = desktopBridge()
    if (!desktop) {
        return null
    }

    const payload = await desktop.session.refresh() as LoginResponse | null
    if (!payload) {
        clearClientSession()
        return null
    }

    await persistClientSession(payload)
    return payload
}

export async function restoreMobileSession() {
    if (!isMobileApp()) {
        return null
    }

    try {
        mobileRefreshToken = await readMobileSecureValue(MOBILE_REFRESH_TOKEN_KEY)
        if (!mobileRefreshToken) {
            return null
        }

        const response = await fetch(`${getRuntimeEnvironment().apiOrigin}/api/auth/refresh`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({refreshToken: mobileRefreshToken}),
        })
        if (!response.ok) {
            clearClientSession()
            return null
        }

        const payload = await response.json() as {code?: number; data?: LoginResponse}
        if (payload.code !== 0 || !payload.data?.accessToken || !payload.data.refreshToken) {
            clearClientSession()
            return null
        }

        await persistClientSession(payload.data)
        return payload.data
    } catch {
        clearClientSession()
        return null
    }
}

export function clearClientSession(emitEvent = true) {
    desktopAccessToken = ''
    desktopTokenType = 'Bearer'
    mobileAccessToken = ''
    mobileRefreshToken = ''
    mobileTokenType = 'Bearer'

    if (isDesktopApp()) {
        void desktopBridge()?.session.clear()
    } else if (isMobileApp()) {
        void removeMobileSecureValue(MOBILE_REFRESH_TOKEN_KEY)
    } else {
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
        localStorage.removeItem(TOKEN_TYPE_KEY)
    }
    if (emitEvent) {
        dispatchSessionEvent(SESSION_CLEARED_EVENT)
    }
}

function dispatchSessionEvent(name: string) {
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(name))
    }
}
