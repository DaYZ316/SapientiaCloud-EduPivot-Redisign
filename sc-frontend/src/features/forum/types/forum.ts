export interface Forum {
  id: string
  courseId: string
  forumName: string
  description: string | null
  forumType: number
  postCount: number
  replyCount: number
  status: number
  tags: string[] | null
  createdAt: string
  updatedAt: string
}

export interface ForumPost {
  id: string
  forumId: string
  courseId: string
  sysUserId: string
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
  forumId: string
  courseId: string
  sysUserId: string
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

export interface CreateForumRequest {
  courseId: string
  forumName: string
  description?: string
  forumType?: number
  tags?: string[]
}

export interface CreatePostRequest {
  forumId: string
  courseId: string
  title: string
  content: string
  postType?: number
  attachmentUrls?: string[] | null
  imageUrls?: string[] | null
  tags?: string[]
  chapterId?: string | null
}

export interface CreateReplyRequest {
  postId: string
  forumId: string
  courseId: string
  content: string
  parentReplyId?: string | null
  replyToUserId?: string | null
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

export interface UpdateForumReplyRequest {
  content: string
  imageUrls?: string[] | null
}

export const ForumType: Record<number, string> = {
  0: '综合讨论',
  1: '课程问答',
  2: '作业讨论',
  3: '公告通知',
}

export const PostType: Record<number, string> = {
  0: '普通帖子',
  1: '提问',
  2: '分享',
  3: '资源',
}
