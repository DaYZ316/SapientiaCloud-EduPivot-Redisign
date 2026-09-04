import type {DesktopEnvironmentProfile} from '@/shared/platform/desktop'
import {desktopBridge} from '@/shared/platform/desktop'

export interface RuntimeEnvironment extends DesktopEnvironmentProfile {
    isDesktop: boolean
}

const DEFAULT_DESKTOP_ORIGIN = 'https://edupivot.xyz'
const DEFAULT_DESKTOP_GITHUB_REDIRECT_URI = 'https://edupivot.xyz/login'

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
    return {
        id: 'web',
        name: 'Web',
        apiOrigin: (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, ''),
        githubClientId: import.meta.env.VITE_GITHUB_CLIENT_ID,
        githubRedirectUri: import.meta.env.VITE_GITHUB_REDIRECT_URI,
        isDesktop: false,
    }
}

function createDesktopEnvironment(profile: DesktopEnvironmentProfile): RuntimeEnvironment {
    return {
        ...profile,
        apiOrigin: profile.apiOrigin || DEFAULT_DESKTOP_ORIGIN,
        githubClientId: profile.githubClientId || import.meta.env.VITE_GITHUB_CLIENT_ID,
        githubRedirectUri: profile.githubRedirectUri
            || import.meta.env.VITE_DESKTOP_GITHUB_REDIRECT_URI
            || DEFAULT_DESKTOP_GITHUB_REDIRECT_URI,
        isDesktop: true,
    }
}
