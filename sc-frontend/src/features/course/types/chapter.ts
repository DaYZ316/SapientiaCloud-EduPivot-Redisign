export interface Chapter {
  id: string
  courseId: string
  teacherId: string
  chapterName: string
  parentChapterId: string | null
  description: string | null
  content: string | null
  attachmentUrls: string[] | null
  sortOrder: number
  status: number
  viewCount: number
  likeCount: number
  likedByMe: boolean
  children: Chapter[] | null
  createdAt: string
  updatedAt: string
}

export interface ChapterInteraction {
  chapterId: string
  viewCount: number
  likeCount: number
  likedByMe: boolean
}

export interface CreateChapterRequest {
  courseId: string
  chapterName: string
  parentChapterId?: string | null
  description?: string
  content?: string
  sortOrder?: number
  status?: number
}

export interface UpdateChapterRequest {
  chapterName?: string
  parentChapterId?: string | null
  description?: string
  content?: string
  sortOrder?: number
  status?: number
}

export const ChapterStatus: Record<number, string> = {
  0: '草稿',
  1: '已发布',
}
