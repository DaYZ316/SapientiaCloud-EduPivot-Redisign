export interface CourseInvitation {
    id: string
    courseId: string
    courseTitle: string | null
    courseCoverUrl: string | null
    inviterId: string
    inviterName: string | null
    inviterAvatar: string | null
    inviteeId: string
    inviteeName: string | null
    status: number
    message: string | null
    createdAt: string
}

export interface InviteAssistantRequest {
    courseId: string
    inviteeId: string
    message?: string
}

export interface InvitationPageQuery {
    page?: number
    size?: number
    status?: number
}

export const InvitationStatus: Record<number, string> = {
    0: '待处理',
    1: '已接受',
    2: '已拒绝',
    3: '已撤回',
}
