import {existsSync} from 'node:fs'
import {extname, join, normalize, resolve} from 'node:path'

const APP_PROTOCOL = 'edupivot'
const APP_HOST = 'app'
const OAUTH_HOST = 'oauth'

export interface PendingOAuthRequest {
  state: string
  redirectUri: string
  expiresAt: number
}

export interface OAuthCallbackResult {
  code: string
  redirectUri: string
}

export function resolveRendererFile(rendererDirectory: string, requestUrl: string) {
  const url = new URL(requestUrl)
  if (url.hostname !== APP_HOST) {
    return null
  }

  const decodedPath = decodeURIComponent(url.pathname)
  const relativePath = decodedPath === '/' ? 'index.html' : decodedPath.replace(/^\/+/, '')
  const root = resolve(rendererDirectory)
  const candidate = resolve(root, normalize(relativePath))
  if (!candidate.startsWith(`${root}\\`) && candidate !== root) {
    return null
  }

  if (existsSync(candidate) && extname(candidate)) {
    return candidate
  }
  if (!extname(candidate)) {
    return join(root, 'index.html')
  }
  return null
}

export function isTrustedRendererUrl(currentUrl: string, packaged: boolean) {
  return packaged
    ? currentUrl.startsWith(`${APP_PROTOCOL}://${APP_HOST}/`)
    : currentUrl.startsWith('http://127.0.0.1:5173/')
}

export function isAllowedExternalUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'mailto:'
  } catch {
    return false
  }
}

export function consumeGitHubOAuthCallback(
  value: string,
  pending: PendingOAuthRequest | null,
  now = Date.now(),
): {pending: PendingOAuthRequest | null; result: OAuthCallbackResult | null} {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return {pending, result: null}
  }

  if (url.protocol !== `${APP_PROTOCOL}:` || url.hostname !== OAUTH_HOST || url.pathname !== '/callback') {
    return {pending, result: null}
  }

  const code = url.searchParams.get('code')
  const state = url.searchParams.get('state')
  if (!code || !state || !pending || pending.expiresAt <= now || state !== pending.state) {
    return {pending, result: null}
  }

  return {
    pending: null,
    result: {code, redirectUri: pending.redirectUri},
  }
}
