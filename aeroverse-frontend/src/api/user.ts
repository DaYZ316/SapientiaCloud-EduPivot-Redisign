import {request} from '@/api/request'
import type {UpdateUserRequest, UserPageQuery, UserPageResponse, UserProfile,} from '@/types/user'

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
    })
}
