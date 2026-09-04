export interface EnvironmentProfile {
  id: string
  name: string
  apiOrigin: string
  githubClientId?: string
  githubRedirectUri?: string
}

export function normalizeProfile(value: unknown): EnvironmentProfile {
  if (!value || typeof value !== 'object') {
    throw new Error('环境配置格式无效。')
  }

  const profile = value as Partial<EnvironmentProfile>
  const id = textValue(profile.id)
  const name = textValue(profile.name)
  if (!/^[a-z0-9][a-z0-9-]{1,63}$/i.test(id) || !name || name.length > 80) {
    throw new Error('环境配置的名称或标识无效。')
  }

  const apiOrigin = normalizeHttpsOrigin(profile.apiOrigin)
  const githubClientId = optionalText(profile.githubClientId)
  const githubRedirectUri = profile.githubRedirectUri ? normalizeHttpsUrl(profile.githubRedirectUri) : undefined
  if (githubRedirectUri && new URL(githubRedirectUri).origin !== new URL(apiOrigin).origin) {
    throw new Error('GitHub 回调地址必须属于该环境的 HTTPS 域名。')
  }

  return {id, name, apiOrigin, githubClientId, githubRedirectUri}
}

export function canonicalProfilePayload(profile: EnvironmentProfile) {
  return JSON.stringify({
    id: profile.id,
    name: profile.name,
    apiOrigin: profile.apiOrigin,
    githubClientId: profile.githubClientId ?? '',
    githubRedirectUri: profile.githubRedirectUri ?? '',
  })
}

export function normalizeHttpsUrl(value: unknown) {
  const url = new URL(textValue(value))
  if (url.protocol !== 'https:' || url.username || url.password || url.hash) {
    throw new Error('回调地址必须是有效的 HTTPS 地址。')
  }
  return url.toString()
}

function normalizeHttpsOrigin(value: unknown) {
  const url = new URL(textValue(value))
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('服务地址必须是无路径、无凭据的 HTTPS Origin。')
  }
  return url.origin
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function optionalText(value: unknown) {
  const text = textValue(value)
  return text || undefined
}
