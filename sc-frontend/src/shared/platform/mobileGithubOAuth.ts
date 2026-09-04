import {App} from '@capacitor/app'
import {Browser} from '@capacitor/browser'

import {
    createGitHubOAuthRequest,
    normalizeTarget,
    type GitHubOAuthCompletion,
    type GitHubOAuthPendingRequest,
    readGitHubOAuthCallback,
} from '@/features/auth/githubOAuth'
import {isMobileApp} from '@/shared/platform/mobile'
import {getRuntimeEnvironment} from '@/shared/platform/runtime'
import {
    readMobileSecureValue,
    removeMobileSecureValue,
    writeMobileSecureValue,
} from '@/shared/platform/mobileSecureStore'

const MOBILE_PENDING_KEY = 'github-oauth-pending'
const MOBILE_CALLBACK_EVENT = 'edupivot:mobile-github-oauth-callback'

let initialized = false
export type MobileGitHubOAuthCompletion = GitHubOAuthCompletion | {error: string}

let pendingCompletion: MobileGitHubOAuthCompletion | null = null

export async function initializeMobileGitHubOAuth() {
    if (!isMobileApp() || initialized) {
        return
    }
    initialized = true

    await App.addListener('appUrlOpen', ({url}) => {
        void receiveMobileGitHubCallback(url)
    })

    const launch = await App.getLaunchUrl()
    if (launch?.url) {
        await receiveMobileGitHubCallback(launch.url)
    }
}

export async function startMobileGitHubAuthorization(target: string) {
    const redirectUri = getRuntimeEnvironment().githubRedirectUri
    if (!redirectUri) {
        throw new Error('GitHub OAuth 回调地址未配置。')
    }
    const {pending, authorizationUrl} = await createGitHubOAuthRequest(
        'mobile',
        redirectUri,
        target,
    )
    await writeMobileSecureValue(MOBILE_PENDING_KEY, JSON.stringify(pending))
    await Browser.open({url: authorizationUrl, presentationStyle: 'fullscreen'})
}

export function takeMobileGitHubCompletion() {
    const completion = pendingCompletion
    pendingCompletion = null
    return completion
}

export function onMobileGitHubCompletion(listener: () => void) {
    window.addEventListener(MOBILE_CALLBACK_EVENT, listener)
    return () => window.removeEventListener(MOBILE_CALLBACK_EVENT, listener)
}

async function receiveMobileGitHubCallback(value: string) {
    const callback = parseMobileCallback(value)
    if (!callback) {
        return
    }

    const pending = await readPending()
    if (!pending
        || pending.expiresAt <= Date.now()
        || !callback.state.startsWith('mobile.')
        || callback.state !== pending.state) {
        return
    }
    await removeMobileSecureValue(MOBILE_PENDING_KEY)

    pendingCompletion = callback.error || !callback.code
        ? {error: callback.errorDescription || callback.error || 'GitHub 未返回授权码。'}
        : {
            code: callback.code,
            codeVerifier: pending.codeVerifier,
            redirectUri: pending.redirectUri,
            target: normalizeTarget(pending.target),
        }
    await Browser.close().catch(() => undefined)
    window.dispatchEvent(new Event(MOBILE_CALLBACK_EVENT))
}

function parseMobileCallback(value: string) {
    try {
        const url = new URL(value)
        const redirectUri = getRuntimeEnvironment().githubRedirectUri
        if (!redirectUri) {
            return null
        }
        const expected = new URL(redirectUri)
        const isCustomProtocol = url.protocol === 'edupivot:'
            && url.hostname === 'oauth'
            && url.pathname === '/callback'
        const isVerifiedAppLink = url.protocol === expected.protocol
            && url.origin === expected.origin
            && url.pathname === expected.pathname
        return isCustomProtocol || isVerifiedAppLink ? readGitHubOAuthCallback(Object.fromEntries(url.searchParams)) : null
    } catch {
        return null
    }
}

async function readPending(): Promise<GitHubOAuthPendingRequest | null> {
    try {
        const raw = await readMobileSecureValue(MOBILE_PENDING_KEY)
        if (!raw) {
            return null
        }
        const value = JSON.parse(raw) as Partial<GitHubOAuthPendingRequest>
        if (typeof value.state !== 'string'
            || typeof value.codeVerifier !== 'string'
            || typeof value.redirectUri !== 'string'
            || typeof value.target !== 'string'
            || typeof value.expiresAt !== 'number') {
            return null
        }
        return value as GitHubOAuthPendingRequest
    } catch {
        return null
    }
}
