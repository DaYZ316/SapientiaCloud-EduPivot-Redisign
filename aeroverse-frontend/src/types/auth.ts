import type {UserProfile} from './user'

export interface LoginResponse {
    accessToken: string
    tokenType: string
    expiresIn: number
    user: UserProfile
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
