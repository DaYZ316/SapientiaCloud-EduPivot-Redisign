export interface Course {
  id: string
  title: string
  description: string | null
  teacherId: string
  teacherName: string | null
  teacherAvatar?: string | null
  level: number
  coverUrl: string | null
  coverFileId: string | null
  teacherIds: string[] | null
  teacherInfos?: {id: string; displayName: string | null; avatarUrl: string | null}[] | null
  semester: string | null
  location: string | null
  courseType: number | null
  isPublic: number
  maxStudents: number
  currentStudents: number
  status: number
  createdAt: string
  updatedAt?: string | null
}

export interface CourseDetail extends Course {
  teacherAvatar?: string | null
  enrolled?: boolean
  updatedAt?: string | null
}

export interface CreateCourseRequest {
  title: string
  description?: string
  level: number
  coverUrl?: string
  coverFileId?: string
  assistantIds?: string[]
  semester?: string
  location?: string
  courseType?: number
  isPublic: number
  maxStudents?: number
}

export interface UpdateCourseRequest {
  title?: string
  description?: string
  level?: number
  coverUrl?: string
  coverFileId?: string
  teacherId?: string
  assistantIds?: string[]
  semester?: string
  location?: string
  courseType?: number
  isPublic: number
  maxStudents?: number
  status?: number
}

export interface CourseFile {
  id: string
  courseId: string
  fileId: string
  visibility: 'PUBLIC' | 'PRIVATE'
  displayName: string
  url: string | null
  createdBy: string
  sortOrder: number
  createdAt: string
  updatedAt?: string | null
}

export interface BindCourseFileRequest {
  fileId: string
  visibility: 'PUBLIC' | 'PRIVATE'
  displayName?: string
  sortOrder?: number
}

export interface CoursePageQuery {
  page?: number
  size?: number
  keyword?: string
  level?: number
  status?: number
  isPublic?: number
  createdAtStart?: string
  createdAtEnd?: string
  updatedAtStart?: string
  updatedAtEnd?: string
}

export type TeacherCourseRole = 'primary' | 'assistant'

export interface Enrollment {
  id: string
  courseId: string
  courseTitle: string | null
  courseCoverUrl: string | null
  studentId: string
  studentName: string | null
  status: number
  enrolledAt: string
  completedAt: string | null
}

export interface EnrollRequest {
  courseId: string
}

export const CourseLevel: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
}

export const CourseStatus: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已归档',
}

export const EnrollmentStatus: Record<number, string> = {
  0: '待审核',
  1: '学习中',
  2: '已结业',
  3: '已退课',
}
