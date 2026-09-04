import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'

import {afterEach, describe, expect, it} from 'vitest'

import {
  consumeGitHubOAuthCallback,
  isAllowedExternalUrl,
  isTrustedRendererUrl,
  resolveRendererFile,
} from '../electron/security'
import {
  readEncryptedRefreshToken,
  writeEncryptedRefreshToken,
  type SecretProtector,
} from '../electron/session-store'
import {parseByteRange} from '../electron/local-file-response'

const protector: SecretProtector = {
  isEncryptionAvailable: () => true,
  encryptString: (value) => Buffer.from(`protected:${Buffer.from(value).toString('base64')}`),
  decryptString: (value) => {
    const text = value.toString()
    if (!text.startsWith('protected:')) throw new Error('invalid encrypted data')
    return Buffer.from(text.slice('protected:'.length), 'base64').toString()
  },
}

const temporaryDirectories: string[] = []

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, {recursive: true, force: true})))
})

describe('desktop security boundaries', () => {
  it('only trusts the embedded protocol or the fixed development origin', () => {
    expect(isTrustedRendererUrl('edupivot://app/dashboard', true)).toBe(true)
    expect(isTrustedRendererUrl('https://edupivot.xyz/dashboard', true)).toBe(false)
    expect(isTrustedRendererUrl('http://127.0.0.1:5173/dashboard', false)).toBe(true)
    expect(isTrustedRendererUrl('http://localhost:5173/dashboard', false)).toBe(false)
  })

  it('only permits HTTPS and mail external handoffs', () => {
    expect(isAllowedExternalUrl('https://edupivot.xyz/help')).toBe(true)
    expect(isAllowedExternalUrl('mailto:support@edupivot.xyz')).toBe(true)
    expect(isAllowedExternalUrl('file:///C:/Windows/System32/cmd.exe')).toBe(false)
    expect(isAllowedExternalUrl('javascript:alert(1)')).toBe(false)
  })

  it('serves static assets and history routes only from the embedded app host', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'edupivot-protocol-'))
    temporaryDirectories.push(directory)
    await writeFile(join(directory, 'index.html'), '<html></html>')
    await writeFile(join(directory, 'logo.svg'), '<svg></svg>')

    expect(resolveRendererFile(directory, 'edupivot://app/logo.svg')).toBe(join(directory, 'logo.svg'))
    expect(resolveRendererFile(directory, 'edupivot://app/courses/42')).toBe(join(directory, 'index.html'))
    expect(resolveRendererFile(directory, 'edupivot://other/logo.svg')).toBeNull()
    expect(resolveRendererFile(directory, 'edupivot://app/missing.glb')).toBeNull()
  })

  it('parses valid media byte ranges and rejects invalid requests', () => {
    expect(parseByteRange('bytes=10-19', 100)).toEqual({start: 10, end: 19})
    expect(parseByteRange('bytes=90-', 100)).toEqual({start: 90, end: 99})
    expect(parseByteRange('bytes=-10', 100)).toEqual({start: 90, end: 99})
    expect(parseByteRange('bytes=100-120', 100)).toBeNull()
  })

  it('consumes a valid OAuth callback once and rejects mismatched or expired state', () => {
    const pending = {
      state: 'desktop.test-state',
      redirectUri: 'https://edupivot.xyz/login',
      expiresAt: 100,
    }

    expect(consumeGitHubOAuthCallback('edupivot://oauth/callback?code=code&state=wrong', pending, 99).result).toBeNull()
    expect(consumeGitHubOAuthCallback('edupivot://oauth/callback?code=code&state=desktop.test-state', pending, 100).result).toBeNull()

    const consumed = consumeGitHubOAuthCallback('edupivot://oauth/callback?code=code&state=desktop.test-state', pending, 99)
    expect(consumed).toEqual({
      pending: null,
      result: {code: 'code', redirectUri: 'https://edupivot.xyz/login'},
    })
    expect(consumeGitHubOAuthCallback('edupivot://oauth/callback?code=code&state=desktop.test-state', consumed.pending, 99).result).toBeNull()
  })

  it('persists the refresh token only through the configured encryption provider', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'edupivot-session-'))
    temporaryDirectories.push(directory)
    const target = join(directory, 'session.bin')

    await writeEncryptedRefreshToken(target, 'refresh-token-value', protector)
    expect((await readFile(target)).toString()).not.toContain('refresh-token-value')
    await expect(readEncryptedRefreshToken(target, protector)).resolves.toBe('refresh-token-value')
  })

  it('removes an unreadable encrypted session instead of restoring it', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'edupivot-session-'))
    temporaryDirectories.push(directory)
    const target = join(directory, 'session.bin')
    await writeFile(target, 'not-encrypted')

    await expect(readEncryptedRefreshToken(target, protector)).resolves.toBe('')
    await expect(readFile(target)).rejects.toThrow()
  })
})
