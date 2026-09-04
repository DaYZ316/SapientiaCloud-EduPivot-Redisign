import {getRuntimeEnvironment} from '@/shared/platform/runtime'

const GITHUB_AUTHORIZE_URL = 'https://github.com/login/oauth/authorize'
const OAUTH_STATE_TTL_MS = 5 * 60 * 1000
const WEB_PENDING_KEY = 'edupivot.github-oauth.web-pending'

export interface GitHubOAuthPendingRequest {
    state: string
    codeVerifier: string
    redirectUri: string
    target: string
    expiresAt: number
}

export interface GitHubOAuthCallback {
    code: string
    state: string
    error?: string
    errorDescription?: string
}

export interface GitHubOAuthCompletion {
    code: string
    codeVerifier: string
    redirectUri: string
    target: string
}

export async function createGitHubOAuthRequest(
    statePrefix: 'web' | 'desktop' | 'mobile',
    redirectUri: string,
    target = '/dashboard',
) {
    const clientId = getRuntimeEnvironment().githubClientId?.trim()
    if (!clientId) {
        throw new Error('GitHub OAuth client id 未配置。')
    }

    const normalizedRedirectUri = new URL(redirectUri).toString()
    const pending: GitHubOAuthPendingRequest = {
        state: `${statePrefix}.${randomUrlSafeValue(32)}`,
        codeVerifier: randomUrlSafeValue(64),
        redirectUri: normalizedRedirectUri,
        target: normalizeTarget(target),
        expiresAt: Date.now() + OAUTH_STATE_TTL_MS,
    }
    const codeChallenge = await createCodeChallenge(pending.codeVerifier)
    const url = new URL(GITHUB_AUTHORIZE_URL)
    url.search = new URLSearchParams({
        client_id: clientId,
        redirect_uri: pending.redirectUri,
        scope: import.meta.env.VITE_GITHUB_SCOPE || 'read:user user:email',
        state: pending.state,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        allow_signup: 'true',
    }).toString()

    return {pending, authorizationUrl: url.toString()}
}

export async function startWebGitHubAuthorization(target: string) {
    const redirectUri = getRuntimeEnvironment().githubRedirectUri
    if (!redirectUri) {
        throw new Error('GitHub OAuth 回调地址未配置。')
    }
    const {pending, authorizationUrl} = await createGitHubOAuthRequest(
        'web',
        redirectUri,
        target,
    )
    sessionStorage.setItem(WEB_PENDING_KEY, JSON.stringify(pending))
    window.location.assign(authorizationUrl)
}

export function consumeWebGitHubCallback(callback: GitHubOAuthCallback): GitHubOAuthCompletion {
    const pending = readWebPending()
    sessionStorage.removeItem(WEB_PENDING_KEY)

    if (!pending || pending.expiresAt <= Date.now() || !callback.state.startsWith('web.') || callback.state !== pending.state) {
        throw new Error('GitHub 登录状态已失效或不匹配。请重新发起登录。')
    }
    if (callback.error) {
        throw new Error(callback.errorDescription || `GitHub 授权失败：${callback.error}`)
    }
    if (!callback.code) {
        throw new Error('GitHub 未返回授权码。')
    }

    return {
        code: callback.code,
        codeVerifier: pending.codeVerifier,
        redirectUri: pending.redirectUri,
        target: pending.target,
    }
}

export function readGitHubOAuthCallback(query: Record<string, unknown>): GitHubOAuthCallback {
    const value = (key: string) => typeof query[key] === 'string' ? query[key] : ''
    return {
        code: value('code'),
        state: value('state'),
        error: value('error') || undefined,
        errorDescription: value('error_description') || undefined,
    }
}

export function normalizeTarget(value: string) {
    return value.startsWith('/') && !value.startsWith('//') ? value : '/dashboard'
}

async function createCodeChallenge(codeVerifier: string) {
    if (!globalThis.crypto?.subtle) {
        throw new Error('当前运行环境不支持 GitHub 登录所需的 PKCE。')
    }
    const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier))
    return toBase64Url(new Uint8Array(digest))
}

function randomUrlSafeValue(bytes: number) {
    if (!globalThis.crypto?.getRandomValues) {
        throw new Error('当前运行环境无法生成安全的 GitHub 登录状态。')
    }
    const value = new Uint8Array(bytes)
    globalThis.crypto.getRandomValues(value)
    return toBase64Url(value)
}

function toBase64Url(bytes: Uint8Array) {
    let binary = ''
    bytes.forEach((value) => {
        binary += String.fromCharCode(value)
    })
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function readWebPending(): GitHubOAuthPendingRequest | null {
    try {
        const raw = sessionStorage.getItem(WEB_PENDING_KEY)
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
