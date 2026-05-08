import type {PageResponse} from './common'

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED'

export type OauthProvider = 'GOOGLE' | 'GITHUB' | 'DEMO'

export interface UserProfile {
    id: string
    email: string | null
    emailVerified: boolean
    displayName: string | null
    avatarUrl: string | null
    locale: string | null
    status: UserStatus
    createdProvider: OauthProvider | null
    createdIp: string | null
    lastLoginProvider: OauthProvider | null
    lastLoginIp: string | null
    loginCount: number
    linkedProviders: OauthProvider[]
}

export interface UserPageQuery {
    page?: number
    size?: number
    keyword?: string
    status?: UserStatus
}

export interface UpdateUserRequest {
    email?: string | null
    emailVerified?: boolean
    displayName?: string | null
    avatarUrl?: string | null
    locale?: string | null
    status?: UserStatus
}

export type UserPageResponse = PageResponse<UserProfile>
