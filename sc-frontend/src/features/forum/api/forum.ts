import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {
    CreateCourseCommentReplyRequest,
    CreateCourseCommentRequest,
    ForumPost,
    ForumReply,
    UpdateForumPostRequest,
    UpdateForumReplyRequest,
} from '@/features/forum/types/forum'

export function getCourseComments(courseId: string, params: { page?: number; size?: number } = {}) {
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

export function updatePost(id: string, data: UpdateForumPostRequest) {
    return request<void>({method: 'PUT', url: "/api/forums/posts/" + id, data, silent: true})
}

export function deletePost(id: string) {
    return request<void>({method: 'DELETE', url: "/api/forums/posts/" + id, silent: true})
}

export function getCommentReplies(postId: string) {
    return request<ForumReply[]>({method: 'GET', url: "/api/forums/comments/" + postId + "/replies/tree"})
}

export function createCommentReply(postId: string, data: CreateCourseCommentReplyRequest) {
    return request<string>({method: 'POST', url: "/api/forums/comments/" + postId + "/replies", data, silent: true})
}

export function updateReply(id: string, data: UpdateForumReplyRequest) {
    return request<void>({method: 'PUT', url: "/api/forums/replies/" + id, data, silent: true})
}

export function deleteReply(id: string) {
    return request<void>({method: 'DELETE', url: "/api/forums/replies/" + id, silent: true})
}
