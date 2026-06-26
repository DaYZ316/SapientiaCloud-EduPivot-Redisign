import type {UserProfile} from '@/features/user/types/user'

export interface LoginResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    expiresIn: number
    user: UserProfile | null
}

export interface GoogleLoginRequest {
    code: string
    redirectUri?: string
}

export interface GitHubLoginRequest {
    code: string
    redirectUri?: string
    codeVerifier?: string
}

export interface PasswordLoginRequest {
    email: string
    password: string
}

export interface RegisterRequest {
    email: string
    password: string
    displayName?: string
    role?: number
}

export interface CompleteOnboardingRequest {
    role: number
    displayName: string
}
