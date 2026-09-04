import {sign} from 'node:crypto'
import {readFile, writeFile} from 'node:fs/promises'

const [inputPath, outputPath] = process.argv.slice(2)
const privateKey = process.env.EDUPIVOT_DESKTOP_PROFILE_SIGNING_KEY

if (!inputPath || !outputPath || !privateKey) {
  throw new Error('Usage: EDUPIVOT_DESKTOP_PROFILE_SIGNING_KEY="<Ed25519 PEM>" node scripts/sign-desktop-profile.mjs profile.json profile.signed.json')
}

const profile = JSON.parse(await readFile(inputPath, 'utf8'))
const canonicalProfile = {
  id: requiredString(profile.id, 'id'),
  name: requiredString(profile.name, 'name'),
  apiOrigin: normalizeHttpsOrigin(profile.apiOrigin),
  githubClientId: optionalString(profile.githubClientId),
  githubRedirectUri: normalizeOptionalCallback(profile.githubRedirectUri),
}

if (!/^[a-z0-9][a-z0-9-]{1,63}$/i.test(canonicalProfile.id) || canonicalProfile.name.length > 80) {
  throw new Error('Profile id or name is invalid.')
}

if (canonicalProfile.githubRedirectUri
    && new URL(canonicalProfile.githubRedirectUri).origin !== new URL(canonicalProfile.apiOrigin).origin) {
  throw new Error('GitHub redirect URI must belong to the API origin.')
}

const signature = sign(null, Buffer.from(JSON.stringify(canonicalProfile)), privateKey).toString('base64')
await writeFile(outputPath, `${JSON.stringify({profile: canonicalProfile, signature}, null, 2)}\n`, 'utf8')

function requiredString(value, name) {
  const normalized = optionalString(value)
  if (!normalized) {
    throw new Error(`Profile field ${name} is required.`)
  }
  return normalized
}

function optionalString(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeHttpsOrigin(value) {
  const url = new URL(requiredString(value, 'apiOrigin'))
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('apiOrigin must be a pathless HTTPS origin.')
  }
  return url.origin
}

function normalizeOptionalCallback(value) {
  const callback = optionalString(value)
  if (!callback) return ''

  const url = new URL(callback)
  if (url.protocol !== 'https:' || url.username || url.password || url.hash) {
    throw new Error('githubRedirectUri must be a valid HTTPS URL.')
  }
  return url.toString()
}
