import {request} from '@/shared/api/request'
import type {
    GitHubLoginRequest,
    GoogleLoginRequest,
    LoginResponse,
    PasswordLoginRequest,
    RegisterRequest,
} from '@/features/auth/types/auth'

export function loginWithGoogle(payload: GoogleLoginRequest) {
    return request<LoginResponse>({
        url: '/api/auth/google/login',
        method: 'POST',
        data: payload,
        silent: true,
    })
}

export function loginWithGitHub(payload: GitHubLoginRequest) {
    return request<LoginResponse>({
        url: '/api/auth/github/login',
        method: 'POST',
        data: payload,
        silent: true,
    })
}

export function loginWithPassword(payload: PasswordLoginRequest) {
    return request<LoginResponse>({
        url: '/api/auth/password/login',
        method: 'POST',
        data: payload,
        silent: true,
    })
}

export function register(payload: RegisterRequest) {
    return request<LoginResponse>({
        url: '/api/auth/register',
        method: 'POST',
        data: payload,
        silent: true,
    })
}

export function logout(refreshToken?: string) {
    return request<void>({
        url: '/api/auth/logout',
        method: 'POST',
        data: refreshToken ? {refreshToken} : undefined,
    }, false)
}
