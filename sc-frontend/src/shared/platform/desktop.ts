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

export interface DesktopBridge {
    profiles: {
        list: () => Promise<DesktopEnvironmentProfile[]>
        current: () => Promise<DesktopEnvironmentProfile>
        import: (signedProfile: string) => Promise<DesktopEnvironmentProfile>
        select: (id: string) => Promise<void>
    }
    session: {
        refresh: () => Promise<DesktopSessionPayload | null>
        storeRefreshToken: (refreshToken: string) => Promise<void>
        clear: () => Promise<void>
        logout: () => Promise<void>
    }
    file: {
        saveUrl: (url: string, filename: string) => Promise<boolean>
    }
    shell: {
        openExternal: (url: string) => Promise<void>
    }
    oauth: {
        startGitHub: (request: {clientId: string; redirectUri: string}) => Promise<void>
        onGitHubResult: (listener: (result: {code: string; redirectUri: string}) => void) => () => void
    }
}

export function desktopBridge(): DesktopBridge | undefined {
    return window.edupivotDesktop
}

export function isDesktopApp() {
    return Boolean(desktopBridge())
}
