import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {
  Forum,
  ForumPost,
  ForumReply,
  CreateForumRequest,
  CreatePostRequest,
  CreateReplyRequest,
  CreateCourseCommentRequest,
  CreateCourseCommentReplyRequest,
} from '@/features/forum/types/forum'

export function getForums() {
  return request<Forum[]>({method: 'GET', url: '/api/forums'})
}

export function getForum(id: string) {
  return request<Forum>({method: 'GET', url: "/api/forums/" + id})
}

export function getCourseForums(courseId: string) {
  return request<Forum[]>({method: 'GET', url: "/api/forums/course/" + courseId})
}

export function getCourseComments(courseId: string, params: {page?: number; size?: number} = {}) {
  const searchParams = new URLSearchParams()
  if (params.page) searchParams.append('page', params.page.toString())
  if (params.size) searchParams.append('size', params.size.toString())
  const query = searchParams.toString()
  return request<PageResponse<ForumPost>>({
    method: 'GET',
    url: "/api/forums/course/" + courseId + "/comments" + (query ? "?" + query : ''),
  })
}

export function createCourseComment(courseId: string, data: CreateCourseCommentRequest) {
  return request<string>({method: 'POST', url: "/api/forums/course/" + courseId + "/comments", data, silent: true})
}

export function createForum(data: CreateForumRequest) {
  return request<string>({method: 'POST', url: '/api/forums', data, silent: true})
}

export function updateForum(id: string, data: Partial<CreateForumRequest>) {
  return request<void>({method: 'PUT', url: "/api/forums/" + id, data, silent: true})
}

export function deleteForum(id: string) {
  return request<void>({method: 'DELETE', url: "/api/forums/" + id, silent: true})
}

export function getPosts(params: {forumId?: string; courseId?: string; page?: number; size?: number; sort?: string; keyword?: string}) {
  const searchParams = new URLSearchParams()
  if (params.forumId) searchParams.append('forumId', params.forumId)
  if (params.courseId) searchParams.append('courseId', params.courseId)
  if (params.page) searchParams.append('page', params.page.toString())
  if (params.size) searchParams.append('size', params.size.toString())
  if (params.sort) searchParams.append('sort', params.sort)
  if (params.keyword) searchParams.append('keyword', params.keyword)
  return request<PageResponse<ForumPost>>({method: 'GET', url: "/api/forums/posts?" + searchParams.toString()})
}

export function getPost(id: string) {
  return request<ForumPost>({method: 'GET', url: "/api/forums/posts/" + id})
}

export function createPost(data: CreatePostRequest) {
  return request<string>({method: 'POST', url: '/api/forums/posts', data, silent: true})
}

export function updatePost(id: string, data: Partial<CreatePostRequest>) {
  return request<void>({method: 'PUT', url: "/api/forums/posts/" + id, data, silent: true})
}

export function deletePost(id: string) {
  return request<void>({method: 'DELETE', url: "/api/forums/posts/" + id, silent: true})
}

export function getHotPosts() {
  return request<ForumPost[]>({method: 'GET', url: '/api/forums/posts/hot'})
}

export function getLatestPosts() {
  return request<ForumPost[]>({method: 'GET', url: '/api/forums/posts/latest'})
}

export function togglePostLike(id: string) {
  return request<void>({method: 'POST', url: "/api/forums/posts/" + id + "/like", silent: true})
}

export function togglePostTop(id: string) {
  return request<void>({method: 'PUT', url: "/api/forums/posts/" + id + "/top", silent: true})
}

export function togglePostEssence(id: string) {
  return request<void>({method: 'PUT', url: "/api/forums/posts/" + id + "/essence", silent: true})
}

export function togglePostLock(id: string) {
  return request<void>({method: 'PUT', url: "/api/forums/posts/" + id + "/lock", silent: true})
}

export function viewPost(id: string) {
  return request<void>({method: 'POST', url: "/api/forums/posts/" + id + "/view", silent: true})
}

export function getReplyTree(postId: string) {
  return request<ForumReply[]>({method: 'GET', url: "/api/forums/replies/post/" + postId + "/tree"})
}

export function getCommentReplies(postId: string) {
  return request<ForumReply[]>({method: 'GET', url: "/api/forums/comments/" + postId + "/replies/tree"})
}

export function createReply(data: CreateReplyRequest) {
  return request<string>({method: 'POST', url: '/api/forums/replies', data, silent: true})
}

export function createCommentReply(postId: string, data: CreateCourseCommentReplyRequest) {
  return request<string>({method: 'POST', url: "/api/forums/comments/" + postId + "/replies", data, silent: true})
}

export function acceptReply(id: string) {
  return request<void>({method: 'PUT', url: "/api/forums/replies/" + id + "/accept", silent: true})
}

export function unacceptReply(id: string) {
  return request<void>({method: 'PUT', url: "/api/forums/replies/" + id + "/unaccept", silent: true})
}

export function toggleReplyLike(id: string) {
  return request<void>({method: 'POST', url: "/api/forums/replies/" + id + "/like", silent: true})
}
