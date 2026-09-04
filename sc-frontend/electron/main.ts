import {createHash, randomBytes, verify} from 'node:crypto'
import {createWriteStream} from 'node:fs'
import {mkdir, readFile, rename, rm, writeFile} from 'node:fs/promises'
import {basename, join} from 'node:path'
import {Readable} from 'node:stream'
import {pipeline} from 'node:stream/promises'

import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Menu,
  net,
  protocol,
  safeStorage,
  session,
  shell,
} from 'electron'
import {
  canonicalProfilePayload,
  normalizeHttpsUrl,
  normalizeProfile,
  type EnvironmentProfile,
} from './profile.js'
import {
  consumeGitHubOAuthCallback,
  isAllowedExternalUrl,
  isTrustedRendererUrl,
  resolveRendererFile,
  type OAuthCallbackResult,
  type PendingOAuthRequest,
} from './security.js'
import {
  readEncryptedRefreshToken,
  readEncryptedSecret,
  writeEncryptedRefreshToken,
  writeEncryptedSecret,
} from './session-store.js'
import {createLocalFileResponse} from './local-file-response.js'

declare const __EDUPIVOT_PROFILE_PUBLIC_KEY__: string
declare const __EDUPIVOT_GITHUB_CLIENT_ID__: string

const APP_PROTOCOL = 'edupivot'
const APP_HOST = 'app'
const PROFILE_PUBLIC_KEY = __EDUPIVOT_PROFILE_PUBLIC_KEY__
const PROFILE_FILE = 'desktop-profiles.json'
const SESSION_FILE = 'desktop-session.bin'
const OAUTH_FILE = 'desktop-oauth.bin'
const OAUTH_STATE_TTL_MS = 5 * 60 * 1000

interface SignedEnvironmentProfile {
  profile: EnvironmentProfile
  signature: string
}

interface StoredProfiles {
  selectedProfileId: string
  importedProfiles: EnvironmentProfile[]
}

interface ApiResponse<T> {
  code: number
  data?: T
}

interface SessionPayload {
  accessToken: string
  refreshToken: string
  tokenType?: string
  user?: unknown
}

let mainWindow: BrowserWindow | null = null
let pendingOAuthRequest: PendingOAuthRequest | null = null
let pendingOAuthResult: OAuthCallbackResult | null = null

protocol.registerSchemesAsPrivileged([
  {
    scheme: APP_PROTOCOL,
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
    },
  },
])

const productionProfile: EnvironmentProfile = {
  id: 'production',
  name: 'EduPivot 正式环境',
  apiOrigin: 'https://edupivot.xyz',
  githubClientId: __EDUPIVOT_GITHUB_CLIENT_ID__,
  githubRedirectUri: 'https://edupivot.xyz/oauth/github/callback',
}

function getProfileFilePath() {
  return join(app.getPath('userData'), PROFILE_FILE)
}

function getSessionFilePath() {
  return join(app.getPath('userData'), SESSION_FILE)
}

function getOAuthFilePath() {
  return join(app.getPath('userData'), OAUTH_FILE)
}

async function loadStoredProfiles(): Promise<StoredProfiles> {
  try {
    const raw = await readFile(getProfileFilePath(), 'utf8')
    const parsed = JSON.parse(raw) as Partial<StoredProfiles>
    const importedProfiles = Array.isArray(parsed.importedProfiles)
      ? parsed.importedProfiles.map(normalizeProfile)
      : []
    return {
      selectedProfileId: typeof parsed.selectedProfileId === 'string' ? parsed.selectedProfileId : productionProfile.id,
      importedProfiles,
    }
  } catch {
    return {selectedProfileId: productionProfile.id, importedProfiles: []}
  }
}

async function saveStoredProfiles(profiles: StoredProfiles) {
  const target = getProfileFilePath()
  const temporary = `${target}.tmp`
  await mkdir(app.getPath('userData'), {recursive: true})
  await writeFile(temporary, JSON.stringify(profiles, null, 2), 'utf8')
  await rename(temporary, target)
}

async function getProfiles() {
  const stored = await loadStoredProfiles()
  return [productionProfile, ...stored.importedProfiles]
}

async function getCurrentProfile() {
  const stored = await loadStoredProfiles()
  const profiles = [productionProfile, ...stored.importedProfiles]
  return profiles.find((profile) => profile.id === stored.selectedProfileId) ?? productionProfile
}

function verifySignedProfile(serializedProfile: string) {
  if (!PROFILE_PUBLIC_KEY.trim()) {
    throw new Error('当前安装包未配置管理员环境签名公钥。')
  }

  let signed: SignedEnvironmentProfile
  try {
    signed = JSON.parse(serializedProfile) as SignedEnvironmentProfile
  } catch {
    throw new Error('环境配置不是有效的 JSON 文件。')
  }

  const profile = normalizeProfile(signed.profile)
  if (typeof signed.signature !== 'string' || !signed.signature.trim()) {
    throw new Error('环境配置缺少签名。')
  }

  const valid = verify(
    null,
    Buffer.from(canonicalProfilePayload(profile)),
    PROFILE_PUBLIC_KEY,
    Buffer.from(signed.signature, 'base64'),
  )
  if (!valid) {
    throw new Error('环境配置签名无效。')
  }

  return profile
}

async function importProfile(serializedProfile: string) {
  if (serializedProfile.length > 64 * 1024) {
    throw new Error('环境配置文件过大。')
  }

  const profile = verifySignedProfile(serializedProfile)
  if (profile.id === productionProfile.id) {
    throw new Error('正式环境不能被导入配置覆盖。')
  }

  const stored = await loadStoredProfiles()
  const retained = stored.importedProfiles.filter((candidate) => candidate.id !== profile.id)
  retained.push(profile)
  await saveStoredProfiles({...stored, importedProfiles: retained})
  return profile
}

async function selectProfile(id: string) {
  const profiles = await getProfiles()
  if (!profiles.some((profile) => profile.id === id)) {
    throw new Error('未找到所选环境。')
  }

  const stored = await loadStoredProfiles()
  await saveStoredProfiles({...stored, selectedProfileId: id})
  await clearRefreshToken()
  app.relaunch()
  app.exit(0)
}

async function storeRefreshToken(refreshToken: string) {
  await writeEncryptedRefreshToken(getSessionFilePath(), refreshToken, safeStorage)
}

async function readRefreshToken() {
  return readEncryptedRefreshToken(getSessionFilePath(), safeStorage)
}

async function clearRefreshToken() {
  await rm(getSessionFilePath(), {force: true})
}

async function savePendingOAuthRequest(request: PendingOAuthRequest) {
  await writeEncryptedSecret(getOAuthFilePath(), JSON.stringify(request), safeStorage)
}

async function loadPendingOAuthRequest() {
  const raw = await readEncryptedSecret(getOAuthFilePath(), safeStorage)
  if (!raw) {
    return null
  }
  try {
    const value = JSON.parse(raw) as Partial<PendingOAuthRequest>
    if (typeof value.state !== 'string'
        || typeof value.redirectUri !== 'string'
        || typeof value.codeVerifier !== 'string'
        || typeof value.expiresAt !== 'number'
        || value.expiresAt <= Date.now()) {
      await clearPendingOAuthRequest()
      return null
    }
    return value as PendingOAuthRequest
  } catch {
    await clearPendingOAuthRequest()
    return null
  }
}

async function clearPendingOAuthRequest() {
  await rm(getOAuthFilePath(), {force: true})
}

async function refreshSession() {
  const refreshToken = await readRefreshToken()
  if (!refreshToken) {
    return null
  }

  const profile = await getCurrentProfile()
  const response = await fetch(`${profile.apiOrigin}/api/auth/refresh`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({refreshToken}),
  })

  if (!response.ok) {
    await clearRefreshToken()
    return null
  }

  const payload = await response.json() as ApiResponse<SessionPayload>
  if (payload.code !== 0 || !payload.data?.accessToken || !payload.data.refreshToken) {
    await clearRefreshToken()
    return null
  }

  await storeRefreshToken(payload.data.refreshToken)
  return payload.data
}

async function logout() {
  const refreshToken = await readRefreshToken()
  if (!refreshToken) {
    return
  }

  const profile = await getCurrentProfile()
  try {
    await fetch(`${profile.apiOrigin}/api/auth/logout`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({refreshToken}),
    })
  } finally {
    await clearRefreshToken()
  }
}

function getRendererDirectory() {
  return join(app.getAppPath(), 'dist')
}

function registerAppProtocol() {
  protocol.handle(APP_PROTOCOL, async (request) => {
    const target = resolveRendererFile(getRendererDirectory(), request.url)
    if (!target) {
      return new Response('Not found', {status: 404})
    }
    return createLocalFileResponse(target, request)
  })
}

function isTrustedRenderer(webContents: Electron.WebContents) {
  return isTrustedRendererUrl(webContents.getURL(), app.isPackaged)
}

function assertTrustedSender(event: Electron.IpcMainInvokeEvent) {
  if (!isTrustedRenderer(event.sender)) {
    throw new Error('已拒绝来自未受信任页面的桌面请求。')
  }
}

async function openExternalUrl(value: string) {
  if (!isAllowedExternalUrl(value)) {
    throw new Error('仅允许打开 HTTPS 或邮件链接。')
  }
  await shell.openExternal(value)
}

function configureContentSecurityPolicy(profile: EnvironmentProfile) {
  if (!app.isPackaged) {
    return
  }

  const apiUrl = new URL(profile.apiOrigin)
  const livekitHost = `livekit.${apiUrl.hostname}`
  const livekitOrigin = `https://${livekitHost}`
  const policy = [
    "default-src 'self'",
    `connect-src 'self' ${profile.apiOrigin} ${toWebSocketOrigin(profile.apiOrigin)} ${livekitOrigin} ${toWebSocketOrigin(livekitOrigin)}`,
    `img-src 'self' ${profile.apiOrigin} blob: data:`,
    `media-src 'self' ${profile.apiOrigin} blob:`,
    "font-src 'self' data:",
    "style-src 'self' 'unsafe-inline'",
    "script-src 'self'",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'none'",
    "frame-src 'none'",
  ].join('; ')

  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    if (!details.url.startsWith(`${APP_PROTOCOL}://${APP_HOST}/`)) {
      callback({responseHeaders: details.responseHeaders})
      return
    }
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [policy],
      },
    })
  })
}

function toWebSocketOrigin(origin: string) {
  const url = new URL(origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.origin
}

function configurePermissionHandling() {
  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
    const canUsePermission = isTrustedRenderer(webContents)
      && (permission === 'media' || permission === 'display-capture' || permission === 'fullscreen')
    callback(canUsePermission)
  })
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1024,
    minHeight: 720,
    show: false,
    backgroundMaterial: 'mica',
    webPreferences: {
      preload: join(app.getAppPath(), 'dist-electron', 'preload.cjs'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      webSecurity: true,
    },
  })

  mainWindow.once('ready-to-show', () => mainWindow?.show())
  mainWindow.webContents.setWindowOpenHandler(({url}) => {
    void openExternalUrl(url).catch(() => undefined)
    return {action: 'deny'}
  })
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const isInternal = app.isPackaged
      ? url.startsWith(`${APP_PROTOCOL}://${APP_HOST}/`)
      : url.startsWith('http://127.0.0.1:5173/')
    if (isInternal) {
      return
    }
    event.preventDefault()
    void openExternalUrl(url).catch(() => undefined)
  })

  const devServerUrl = process.env.ELECTRON_RENDERER_URL ?? 'http://127.0.0.1:5173'
  if (app.isPackaged) {
    void mainWindow.loadURL(`${APP_PROTOCOL}://${APP_HOST}/`)
  } else {
    void mainWindow.loadURL(devServerUrl)
  }
}

function registerIpcHandlers() {
  ipcMain.handle('profiles:list', (event) => {
    assertTrustedSender(event)
    return getProfiles()
  })
  ipcMain.handle('profiles:current', (event) => {
    assertTrustedSender(event)
    return getCurrentProfile()
  })
  ipcMain.handle('profiles:import', (event, serializedProfile: unknown) => {
    assertTrustedSender(event)
    if (typeof serializedProfile !== 'string') {
      throw new Error('环境配置内容无效。')
    }
    return importProfile(serializedProfile)
  })
  ipcMain.handle('profiles:select', async (event, id: unknown) => {
    assertTrustedSender(event)
    if (typeof id !== 'string') {
      throw new Error('环境标识无效。')
    }
    await selectProfile(id)
  })

  ipcMain.handle('session:refresh', (event) => {
    assertTrustedSender(event)
    return refreshSession()
  })
  ipcMain.handle('session:store-refresh-token', async (event, refreshToken: unknown) => {
    assertTrustedSender(event)
    if (typeof refreshToken !== 'string') {
      throw new Error('刷新令牌无效。')
    }
    await storeRefreshToken(refreshToken)
  })
  ipcMain.handle('session:clear', async (event) => {
    assertTrustedSender(event)
    await clearRefreshToken()
  })
  ipcMain.handle('session:logout', async (event) => {
    assertTrustedSender(event)
    await logout()
  })

  ipcMain.handle('file:save-url', async (event, payload: unknown) => {
    assertTrustedSender(event)
    return saveRemoteFile(payload)
  })
  ipcMain.handle('shell:open-external', async (event, url: unknown) => {
    assertTrustedSender(event)
    if (typeof url !== 'string') {
      throw new Error('外链地址无效。')
    }
    await openExternalUrl(url)
  })
  ipcMain.handle('oauth:start-github', async (event) => {
    assertTrustedSender(event)
    await startGitHubAuthorization()
  })
  ipcMain.handle('oauth:take-github-result', (event) => {
    assertTrustedSender(event)
    const result = pendingOAuthResult
    pendingOAuthResult = null
    return result
  })
}

async function saveRemoteFile(payload: unknown) {
  if (!payload || typeof payload !== 'object') {
    throw new Error('下载请求无效。')
  }

  const {url, filename} = payload as {url?: unknown; filename?: unknown}
  if (typeof url !== 'string' || typeof filename !== 'string') {
    throw new Error('下载请求无效。')
  }

  const profile = await getCurrentProfile()
  const remoteUrl = new URL(url)
  if (remoteUrl.protocol !== 'https:' || remoteUrl.origin !== profile.apiOrigin) {
    throw new Error('仅允许保存当前服务提供的 HTTPS 文件。')
  }

  const saveOptions = {
    defaultPath: basename(filename).replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_') || 'download',
  }
  const result = mainWindow
    ? await dialog.showSaveDialog(mainWindow, saveOptions)
    : await dialog.showSaveDialog(saveOptions)
  if (result.canceled || !result.filePath) {
    return false
  }

  const response = await net.fetch(remoteUrl.toString(), {redirect: 'error'})
  if (!response.ok || !response.body) {
    throw new Error(`文件下载失败：${response.status}`)
  }

  await pipeline(Readable.fromWeb(response.body as never), createWriteStream(result.filePath))
  return true
}

async function startGitHubAuthorization() {
  const profile = await getCurrentProfile()
  const clientId = profile.githubClientId?.trim()
  if (!clientId) {
    throw new Error('当前环境未配置 GitHub OAuth client id。')
  }
  const normalizedRedirectUri = normalizeHttpsUrl(profile.githubRedirectUri ?? `${profile.apiOrigin}/oauth/github/callback`)
  if (new URL(normalizedRedirectUri).origin !== new URL(profile.apiOrigin).origin) {
    throw new Error('GitHub 回调地址不属于当前服务环境。')
  }

  const codeVerifier = randomBytes(64).toString('base64url')
  const state = `desktop.${randomBytes(32).toString('base64url')}`
  pendingOAuthRequest = {
    state,
    redirectUri: normalizedRedirectUri,
    codeVerifier,
    expiresAt: Date.now() + OAUTH_STATE_TTL_MS,
  }
  pendingOAuthResult = null
  await savePendingOAuthRequest(pendingOAuthRequest)

  const url = new URL('https://github.com/login/oauth/authorize')
  url.search = new URLSearchParams({
    client_id: clientId,
    redirect_uri: normalizedRedirectUri,
    scope: 'read:user user:email',
    state,
    code_challenge: createHash('sha256').update(codeVerifier).digest('base64url'),
    code_challenge_method: 'S256',
    allow_signup: 'true',
  }).toString()
  await shell.openExternal(url.toString())
}

async function handleProtocolUrl(value: string) {
  const pending = pendingOAuthRequest ?? await loadPendingOAuthRequest()
  const consumed = consumeGitHubOAuthCallback(value, pending)
  if (!consumed.result) {
    return
  }

  pendingOAuthRequest = consumed.pending
  await clearPendingOAuthRequest()
  pendingOAuthResult = consumed.result
  mainWindow?.show()
  mainWindow?.focus()
  mainWindow?.webContents.send('oauth:github-result', consumed.result)
}

function registerProtocolClient() {
  if (process.defaultApp && process.argv.length >= 2) {
    app.setAsDefaultProtocolClient(APP_PROTOCOL, process.execPath, [process.argv[1]])
    return
  }
  app.setAsDefaultProtocolClient(APP_PROTOCOL)
}

function findProtocolUrl(argumentsList: string[]) {
  return argumentsList.find((argument) => argument.startsWith(`${APP_PROTOCOL}://`))
}

const hasSingleInstanceLock = app.requestSingleInstanceLock()
if (!hasSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', (_event, commandLine) => {
    const protocolUrl = findProtocolUrl(commandLine)
    if (protocolUrl) {
      void handleProtocolUrl(protocolUrl)
    }
    mainWindow?.show()
    mainWindow?.focus()
  })

  app.on('open-url', (event, url) => {
    event.preventDefault()
    void handleProtocolUrl(url)
  })

  app.whenReady().then(async () => {
    app.setName('SapientiaCloud EduPivot')
    Menu.setApplicationMenu(null)
    registerProtocolClient()
    const profile = await getCurrentProfile()
    registerAppProtocol()
    configureContentSecurityPolicy(profile)
    configurePermissionHandling()
    registerIpcHandlers()
    createMainWindow()
    pendingOAuthRequest = await loadPendingOAuthRequest()

    const protocolUrl = findProtocolUrl(process.argv)
    if (protocolUrl) {
      await handleProtocolUrl(protocolUrl)
    }
  })

  app.on('window-all-closed', () => {
    app.quit()
  })
}
