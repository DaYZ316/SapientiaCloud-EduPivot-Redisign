import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {
  Course,
  CourseDetail,
  CoursePageQuery,
  BindCourseFileRequest,
  CreateCourseRequest,
  EnrollRequest,
  Enrollment,
  CourseFile,
  TeacherCourseRole,
  UpdateCourseRequest,
} from '@/features/course/types/course'

export function createCourse(data: CreateCourseRequest) {
  return request<string>({method: 'POST', url: '/api/courses', data, silent: true})
}

export function getCourses(query: CoursePageQuery = {}) {
  const params = new URLSearchParams()
  if (query.page) params.append('page', query.page.toString())
  if (query.size) params.append('size', query.size.toString())
  if (query.keyword) params.append('keyword', query.keyword)
  if (query.level) params.append('level', query.level.toString())
  if (query.status) params.append('status', query.status.toString())
  if (query.isPublic != null) params.append('isPublic', query.isPublic.toString())
  if (query.createdAtStart) params.append('createdAtStart', query.createdAtStart)
  if (query.createdAtEnd) params.append('createdAtEnd', query.createdAtEnd)
  if (query.updatedAtStart) params.append('updatedAtStart', query.updatedAtStart)
  if (query.updatedAtEnd) params.append('updatedAtEnd', query.updatedAtEnd)

  return request<PageResponse<Course>>({method: 'GET', url: `/api/courses?${params.toString()}`})
}

export function getCourse(id: string) {
  return request<CourseDetail>({method: 'GET', url: `/api/courses/${id}`})
}

export function updateCourse(id: string, data: UpdateCourseRequest) {
  return request<void>({method: 'PUT', url: `/api/courses/${id}`, data, silent: true})
}

export function deleteCourse(id: string) {
  return request<void>({method: 'DELETE', url: `/api/courses/${id}`, silent: true})
}

export function getTeacherCourses(page: number = 1, size: number = 10, role?: TeacherCourseRole) {
  const params = new URLSearchParams()
  params.append('page', page.toString())
  params.append('size', size.toString())
  if (role) params.append('role', role)

  return request<PageResponse<Course>>({method: 'GET', url: `/api/courses/teacher?${params.toString()}`})
}

export function enroll(data: EnrollRequest) {
  return request<string>({method: 'POST', url: '/api/enrollments', data, silent: true})
}

export function getMyEnrollments(page: number = 1, size: number = 10) {
  const params = new URLSearchParams()
  params.append('page', page.toString())
  params.append('size', size.toString())
  return request<PageResponse<Enrollment>>({method: 'GET', url: `/api/enrollments/my?${params.toString()}`})
}

export function getCourseEnrollments(courseId: string, page: number = 1, size: number = 10) {
  const params = new URLSearchParams()
  params.append('page', page.toString())
  params.append('size', size.toString())
  return request<PageResponse<Enrollment>>({method: 'GET', url: `/api/enrollments/course/${courseId}?${params.toString()}`})
}

export function updateEnrollmentStatus(id: string, status: number) {
  return request<void>({method: 'PUT', url: `/api/enrollments/${id}/status`, data: {status}})
}

export function dropCourse(id: string) {
  return request<void>({method: 'DELETE', url: `/api/enrollments/${id}`, silent: true})
}

export function bindCourseFile(courseId: string, data: BindCourseFileRequest) {
  return request<CourseFile>({method: 'POST', url: `/api/courses/${courseId}/files`, data})
}

export async function listCourseFiles(courseId: string, page: number = 1, size: number = 10): Promise<PageResponse<CourseFile>> {
  const params = new URLSearchParams()
  params.append('page', page.toString())
  params.append('size', size.toString())
  const response = await request<PageResponse<CourseFile> | CourseFile[]>({method: 'GET', url: `/api/courses/${courseId}/files?${params.toString()}`})
  if (Array.isArray(response)) {
    const start = (page - 1) * size
    return {
      records: response.slice(start, start + size),
      total: response.length,
      page,
      size,
    }
  }
  const records = response.records || []
  return {
    records,
    total: response.total ?? records.length,
    page: response.page ?? page,
    size: response.size ?? size,
  }
}

export function deleteCourseFile(courseId: string, courseFileId: string) {
  return request<void>({method: 'DELETE', url: `/api/courses/${courseId}/files/${courseFileId}`})
}
