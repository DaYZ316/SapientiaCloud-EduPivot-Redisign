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
        startGitHub: () => Promise<void>
        takeGitHubResult: () => Promise<DesktopGitHubOAuthResult | null>
        onGitHubResult: (listener: (result: DesktopGitHubOAuthResult) => void) => () => void
    }
}

export function desktopBridge(): DesktopBridge | undefined {
    return window.edupivotDesktop
}

export function isDesktopApp() {
    return Boolean(desktopBridge())
}
