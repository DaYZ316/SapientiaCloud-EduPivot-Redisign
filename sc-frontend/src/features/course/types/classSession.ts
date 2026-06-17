export interface ClassSession {
    id: string
    courseId: string
    teacherId: string
    title: string
    description: string | null
    scheduledStartAt: string
    scheduledEndAt: string
    publishedAt: string | null
    roomSize: number
    liveRoomName: string
    status: number
    statusText: string
    joined: boolean
    createdAt: string
    updatedAt: string
}

export interface ClassParticipant {
    id: string
    sessionId: string
    userId: string
    role: number
    seatIndex: number | null
    x: number
    y: number
    z: number
    displayName: string | null
    avatarUrl: string | null
    joinedAt: string
}

export interface CreateClassSessionRequest {
    courseId: string
    title: string
    description?: string
    scheduledStartAt: string
    scheduledEndAt: string
    roomSize: number
}

export interface UpdateClassSessionRequest {
    title?: string
    description?: string
    scheduledStartAt?: string
    scheduledEndAt?: string
    roomSize?: number
}

export interface JoinClassSessionRequest {
    x: number
    y: number
    z?: number
    seatIndex?: number
}

export interface ClassSeatSyncToken {
    token: string
    expiresInSeconds: number
}

export type ClassSessionFormPayload = Omit<CreateClassSessionRequest, 'courseId'>

export const ClassSessionStatus = {
    PREPARING: 0,
    UPCOMING: 1,
    LIVE: 2,
    FINISHED: 3,
} as const

export const ClassRoomSize = {
    SMALL: 0,
    MEDIUM: 1,
    LARGE: 2,
    XLARGE: 3,
} as const

export const ClassRoomSizeLabel: Record<number, string> = {
    [ClassRoomSize.SMALL]: 'Small classroom',
    [ClassRoomSize.MEDIUM]: 'Medium classroom',
    [ClassRoomSize.LARGE]: 'Large classroom',
    [ClassRoomSize.XLARGE]: 'Extra large classroom',
}
