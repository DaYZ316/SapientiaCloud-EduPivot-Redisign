import type {UserBasicInfo} from '@/features/user/types/user'

export interface ForumPost {
    id: string
    courseId: string
    sysUserId: string
    userInfo?: UserBasicInfo | null
    title: string
    content: string
    postType: number
    attachmentUrls: string[] | null
    imageUrls: string[] | null
    tags: string[] | null
    viewCount: number
    likeCount: number
    replyCount: number
    shareCount: number
    isTop: number
    isEssence: number
    isLocked: number
    lastReplyId: string | null
    lastReplyTime: string | null
    lastReplyUserId: string | null
    status: number
    chapterId: string | null
    createdAt: string
    updatedAt: string
}

export interface ForumReply {
    id: string
    postId: string
    courseId: string
    sysUserId: string
    userInfo?: UserBasicInfo | null
    content: string
    parentReplyId: string | null
    replyToUserId: string | null
    attachmentUrls: string[] | null
    imageUrls: string[] | null
    likeCount: number
    replyCount: number
    isAccepted: number
    floorNumber: number
    children: ForumReply[] | null
    createdAt: string
    updatedAt: string
}

export interface CreateCourseCommentRequest {
    content: string
    imageUrls?: string[] | null
}

export interface CreateCourseCommentReplyRequest {
    content: string
    parentReplyId?: string | null
    replyToUserId?: string | null
    imageUrls?: string[] | null
}

export interface UpdateForumPostRequest {
    content?: string
    imageUrls?: string[] | null
}

export interface UpdateForumReplyRequest {
    content: string
    imageUrls?: string[] | null
}
