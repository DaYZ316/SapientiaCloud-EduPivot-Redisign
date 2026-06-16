import {request} from '@/shared/api/request'
import type {
    UpdateUserRequest,
    UserBasicInfo,
    UserPageQuery,
    UserPageResponse,
    UserProfile,
} from '@/features/user/types/user'

export function getCurrentUser() {
    return request<UserProfile>({
        url: '/api/auth/users/me',
        method: 'GET',
    })
}

export function getUserById(id: string) {
    return request<UserProfile>({
        url: `/api/auth/users/${id}`,
        method: 'GET',
    })
}

export function getUsersBasicInfo(ids: string[]) {
    const query = ids.map(id => `ids=${id}`).join('&')
    return request<UserBasicInfo[]>({
        url: `/api/auth/users/basic?${query}`,
        method: 'GET',
    })
}

export function updateCurrentUser(payload: UpdateUserRequest) {
    return request<UserProfile>({
        url: '/api/auth/users/me',
        method: 'PUT',
        data: payload,
    })
}

export function pageUsers(params: UserPageQuery = {}) {
    return request<UserPageResponse>({
        url: '/api/auth/users',
        method: 'GET',
        params,
    })
}

export function updateUser(id: string, payload: UpdateUserRequest) {
    return request<UserProfile>({
        url: `/api/auth/users/${id}`,
        method: 'PUT',
        data: payload,
        silent: true,
    })
}

export function resetPassword(id: string) {
    return request<void>({
        url: `/api/auth/users/${id}/reset-password`,
        method: 'PUT',
        silent: true,
    })
}

export function listTeachers(params: { page?: number; size?: number; keyword?: string } = {}) {
    return request<UserPageResponse>({
        url: '/api/auth/users/teachers',
        method: 'GET',
        params,
    })
}

export function listAllUsers(params: UserPageQuery = {}) {
    return request<UserPageResponse>({
        url: '/api/auth/users/all',
        method: 'GET',
        params,
    })
}
