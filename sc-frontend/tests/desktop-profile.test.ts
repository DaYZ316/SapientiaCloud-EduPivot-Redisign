import {generateKeyPairSync, sign, verify} from 'node:crypto'

import {describe, expect, it} from 'vitest'

import {canonicalProfilePayload, normalizeProfile} from '../electron/profile'

const profile = {
  id: 'campus-test',
  name: 'Campus Test',
  apiOrigin: 'https://edupivot-test.example.edu',
  githubClientId: 'public-client-id',
  githubRedirectUri: 'https://edupivot-test.example.edu/login',
}

describe('desktop environment profile', () => {
  it('normalizes a trusted HTTPS profile', () => {
    expect(normalizeProfile(profile)).toEqual(profile)
  })

  it('rejects an API origin with a path or insecure protocol', () => {
    expect(() => normalizeProfile({...profile, apiOrigin: 'http://edupivot-test.example.edu'})).toThrow('HTTPS')
    expect(() => normalizeProfile({...profile, apiOrigin: 'https://edupivot-test.example.edu/api'})).toThrow('Origin')
  })

  it('rejects a GitHub callback outside the selected environment', () => {
    expect(() => normalizeProfile({...profile, githubRedirectUri: 'https://other.example.edu/login'})).toThrow('属于')
  })

  it('produces a stable payload that verifies with the administrator key', () => {
    const {privateKey, publicKey} = generateKeyPairSync('ed25519')
    const normalized = normalizeProfile(profile)
    const payload = Buffer.from(canonicalProfilePayload(normalized))
    const signature = sign(null, payload, privateKey)

    expect(verify(null, payload, publicKey, signature)).toBe(true)
  })
})
