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
  x: number
  y: number
  z: number
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
  [ClassRoomSize.SMALL]: '小型教室',
  [ClassRoomSize.MEDIUM]: '中型教室',
  [ClassRoomSize.LARGE]: '大型教室',
  [ClassRoomSize.XLARGE]: '超大型教室',
}
