import type {DesktopEnvironmentProfile} from '@/shared/platform/desktop'
import {desktopBridge} from '@/shared/platform/desktop'
import {isMobileApp} from '@/shared/platform/mobile'

export interface RuntimeEnvironment extends DesktopEnvironmentProfile {
    isDesktop: boolean
    isMobile: boolean
}

const DEFAULT_DESKTOP_ORIGIN = 'https://edupivot.xyz'
const DEFAULT_WEB_GITHUB_REDIRECT_URI = 'https://edupivot.xyz/oauth/github/callback'
const DEFAULT_MOBILE_GITHUB_REDIRECT_URI = 'https://edupivot.xyz/oauth/github/mobile/callback'

let runtimeEnvironment: RuntimeEnvironment = createWebEnvironment()

export async function initializeRuntimeEnvironment() {
    const desktop = desktopBridge()
    if (!desktop) {
        runtimeEnvironment = createWebEnvironment()
        return runtimeEnvironment
    }

    runtimeEnvironment = createDesktopEnvironment(await desktop.profiles.current())
    return runtimeEnvironment
}

export function getRuntimeEnvironment() {
    return runtimeEnvironment
}

export function apiUrl(path: string) {
    return `${runtimeEnvironment.apiOrigin}${path}`
}

export function socketUrl(path: string, search?: URLSearchParams) {
    const base = runtimeEnvironment.apiOrigin || window.location.origin
    const url = new URL(base)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = path
    url.search = search?.toString() ?? ''
    return url.toString()
}

function createWebEnvironment(): RuntimeEnvironment {
    const mobile = isMobileApp()
    return {
        id: mobile ? 'mobile' : 'web',
        name: mobile ? 'Mobile' : 'Web',
        apiOrigin: (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, ''),
        githubClientId: import.meta.env.VITE_GITHUB_CLIENT_ID,
        githubRedirectUri: mobile
            ? import.meta.env.VITE_MOBILE_GITHUB_REDIRECT_URI || DEFAULT_MOBILE_GITHUB_REDIRECT_URI
            : import.meta.env.VITE_GITHUB_REDIRECT_URI || DEFAULT_WEB_GITHUB_REDIRECT_URI,
        isDesktop: false,
        isMobile: mobile,
    }
}

function createDesktopEnvironment(profile: DesktopEnvironmentProfile): RuntimeEnvironment {
    return {
        ...profile,
        apiOrigin: profile.apiOrigin || DEFAULT_DESKTOP_ORIGIN,
        githubClientId: profile.githubClientId || import.meta.env.VITE_GITHUB_CLIENT_ID,
        githubRedirectUri: profile.githubRedirectUri
            || import.meta.env.VITE_DESKTOP_GITHUB_REDIRECT_URI
            || DEFAULT_WEB_GITHUB_REDIRECT_URI,
        isDesktop: true,
        isMobile: false,
    }
}
