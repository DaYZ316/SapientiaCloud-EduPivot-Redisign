import type {LoginResponse} from '@/features/auth/types/auth'
import {desktopBridge, isDesktopApp} from '@/shared/platform/desktop'

export const ACCESS_TOKEN_KEY = 'edupivot.accessToken'
export const SESSION_CLEARED_EVENT = 'edupivot:session-cleared'
export const SESSION_UPDATED_EVENT = 'edupivot:session-updated'

const REFRESH_TOKEN_KEY = 'edupivot.refreshToken'
const TOKEN_TYPE_KEY = 'edupivot.tokenType'

let desktopAccessToken = ''
let desktopTokenType = 'Bearer'

export function getAccessToken() {
    return isDesktopApp() ? desktopAccessToken : localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
}

export function getRefreshToken() {
    return isDesktopApp() ? '' : localStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
}

export function getTokenType() {
    return isDesktopApp() ? desktopTokenType : localStorage.getItem(TOKEN_TYPE_KEY) ?? 'Bearer'
}

export async function persistClientSession(payload: LoginResponse) {
    desktopAccessToken = payload.accessToken
    desktopTokenType = payload.tokenType || 'Bearer'

    if (isDesktopApp()) {
        await desktopBridge()?.session.storeRefreshToken(payload.refreshToken)
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

export function clearClientSession(emitEvent = true) {
    desktopAccessToken = ''
    desktopTokenType = 'Bearer'

    if (isDesktopApp()) {
        void desktopBridge()?.session.clear()
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
