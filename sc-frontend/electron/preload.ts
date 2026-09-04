import {contextBridge, ipcRenderer} from 'electron'

export interface DesktopEnvironmentProfile {
  id: string
  name: string
  apiOrigin: string
  githubClientId?: string
  githubRedirectUri?: string
}

export interface DesktopSessionPayload {
  accessToken: string
  refreshToken: string
  tokenType?: string
  user?: unknown
}

export type DesktopGitHubOAuthResult =
  | {success: true; code: string; codeVerifier: string; redirectUri: string}
  | {success: false; message: string}

const desktopBridge = {
  profiles: {
    list: (): Promise<DesktopEnvironmentProfile[]> => ipcRenderer.invoke('profiles:list'),
    current: (): Promise<DesktopEnvironmentProfile> => ipcRenderer.invoke('profiles:current'),
    import: (signedProfile: string): Promise<DesktopEnvironmentProfile> => ipcRenderer.invoke('profiles:import', signedProfile),
    select: (id: string): Promise<void> => ipcRenderer.invoke('profiles:select', id),
  },
  session: {
    refresh: (): Promise<DesktopSessionPayload | null> => ipcRenderer.invoke('session:refresh'),
    storeRefreshToken: (refreshToken: string): Promise<void> => ipcRenderer.invoke('session:store-refresh-token', refreshToken),
    clear: (): Promise<void> => ipcRenderer.invoke('session:clear'),
    logout: (): Promise<void> => ipcRenderer.invoke('session:logout'),
  },
  file: {
    saveUrl: (url: string, filename: string): Promise<boolean> => ipcRenderer.invoke('file:save-url', {url, filename}),
  },
  shell: {
    openExternal: (url: string): Promise<void> => ipcRenderer.invoke('shell:open-external', url),
  },
  oauth: {
    startGitHub: (): Promise<void> => ipcRenderer.invoke('oauth:start-github'),
    takeGitHubResult: (): Promise<DesktopGitHubOAuthResult | null> => ipcRenderer.invoke('oauth:take-github-result'),
    onGitHubResult: (listener: (result: DesktopGitHubOAuthResult) => void) => {
      const channel = 'oauth:github-result'
      const wrappedListener = (_event: Electron.IpcRendererEvent, result: DesktopGitHubOAuthResult) => listener(result)
      ipcRenderer.on(channel, wrappedListener)
      return () => ipcRenderer.removeListener(channel, wrappedListener)
    },
  },
}

contextBridge.exposeInMainWorld('edupivotDesktop', desktopBridge)
