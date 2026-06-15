import type {PageResponse} from '@/shared/types/common'

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED'

export type OauthProvider = 'GOOGLE' | 'GITHUB' | 'LOCAL'

export type ThemePreference = 'light' | 'dark' | 'system'

export interface StudentInfo {
    id: string
    studentNo: string | null
    grade: string | null
    major: string | null
    school: string | null
}

export interface TeacherInfo {
    id: string
    employeeNo: string | null
    department: string | null
    title: string | null
    school: string | null
}

export interface UserProfile {
    id: string
    email: string | null
    emailVerified: boolean
    displayName: string | null
    avatarUrl: string | null
    avatarFileId: string | null
    locale: string | null
    status: UserStatus
    phone: string | null
    bio: string | null
    gender: number | null
    birthday: string | null
    theme: ThemePreference | null
    notificationEnabled: boolean
    createdProvider: OauthProvider | null
    createdIp: string | null
    lastLoginProvider: OauthProvider | null
    lastLoginIp: string | null
    loginCount: number
    linkedProviders: OauthProvider[]
    createdAt?: string | null
    updatedAt?: string | null
    lastLoginAt?: string | null
    role: number | null
    studentInfo: StudentInfo | null
    teacherInfo: TeacherInfo | null
}

export interface UserPageQuery {
    page?: number
    size?: number
    keyword?: string
    status?: UserStatus
    role?: number
}

export interface UpdateUserRequest {
    email?: string | null
    emailVerified?: boolean
    displayName?: string | null
    avatarUrl?: string | null
    avatarFileId?: string | null
    locale?: string | null
    phone?: string | null
    bio?: string | null
    gender?: number | null
    birthday?: string | null
    theme?: ThemePreference | null
    notificationEnabled?: boolean
    status?: UserStatus
}

export type UserPageResponse = PageResponse<UserProfile>

export interface UserBasicInfo {
    id: string
    displayName: string | null
    avatarUrl: string | null
    role?: number | null
}
