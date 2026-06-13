import {request} from '@/shared/api/request'
import type {Chapter, CreateChapterRequest, UpdateChapterRequest} from '@/features/course/types/chapter'

export function getChapterTree(courseId: string) {
  return request<Chapter[]>({method: 'GET', url: "/api/chapters/course/" + courseId + "/tree"})
}

export function getChapter(id: string) {
  return request<Chapter>({method: 'GET', url: "/api/chapters/" + id})
}

export function createChapter(data: CreateChapterRequest) {
  return request<string>({method: 'POST', url: '/api/chapters', data, silent: true})
}

export function updateChapter(id: string, data: UpdateChapterRequest) {
  return request<void>({method: 'PUT', url: "/api/chapters/" + id, data, silent: true})
}

export function deleteChapter(id: string) {
  return request<void>({method: 'DELETE', url: "/api/chapters/" + id, silent: true})
}