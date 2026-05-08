import {request} from '@/api/request'
import type {GitHubLoginRequest, GoogleLoginRequest, LoginResponse} from '@/types/auth'

export function loginWithGoogle(payload: GoogleLoginRequest) {
    return request<LoginResponse>({
        url: '/api/auth/google/login',
        method: 'POST',
        data: payload,
    })
}

export function loginWithGitHub(payload: GitHubLoginRequest) {
    return request<LoginResponse>({
        url: '/api/auth/github/login',
        method: 'POST',
        data: payload,
    })
}
