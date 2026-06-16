import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {
  ClassParticipant,
  ClassSession,
  CreateClassSessionRequest,
  JoinClassSessionRequest,
  UpdateClassSessionRequest,
} from '@/features/course/types/classSession'

export function createClassSession(data: CreateClassSessionRequest) {
  return request<string>({method: 'POST', url: '/api/class-sessions', data, silent: true})
}

export function updateClassSession(id: string, data: UpdateClassSessionRequest) {
  return request<void>({method: 'PUT', url: `/api/class-sessions/${id}`, data, silent: true})
}

export function publishClassSession(id: string) {
  return request<void>({method: 'POST', url: `/api/class-sessions/${id}/publish`, silent: true})
}

export function deleteClassSession(id: string) {
  return request<void>({method: 'DELETE', url: `/api/class-sessions/${id}`, silent: true})
}

export function getCourseClassSessions(courseId: string, page: number = 1, size: number = 20) {
  const params = new URLSearchParams()
  params.append('page', page.toString())
  params.append('size', size.toString())
  return request<PageResponse<ClassSession>>({
    method: 'GET',
    url: `/api/class-sessions/course/${courseId}?${params.toString()}`,
  })
}

export function getClassSession(id: string) {
  return request<ClassSession>({method: 'GET', url: `/api/class-sessions/${id}`})
}

export function joinClassSession(id: string, data: JoinClassSessionRequest) {
  return request<ClassParticipant>({method: 'POST', url: `/api/class-sessions/${id}/join`, data, silent: true})
}
