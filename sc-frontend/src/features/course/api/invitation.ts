import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {CourseInvitation, InvitationPageQuery, InviteAssistantRequest,} from '@/features/course/types/invitation'

export function sendInvitation(data: InviteAssistantRequest) {
    return request<string>({method: 'POST', url: '/api/invitations', data, silent: true})
}

export function getReceivedInvitations(query: InvitationPageQuery = {}) {
    const params = new URLSearchParams()
    if (query.page) params.append('page', query.page.toString())
    if (query.size) params.append('size', query.size.toString())
    if (query.status != null) params.append('status', query.status.toString())

    return request<PageResponse<CourseInvitation>>({
        method: 'GET',
        url: `/api/invitations/received?${params.toString()}`
    })
}

export function getSentInvitations(query: InvitationPageQuery = {}) {
    const params = new URLSearchParams()
    if (query.page) params.append('page', query.page.toString())
    if (query.size) params.append('size', query.size.toString())
    if (query.status != null) params.append('status', query.status.toString())

    return request<PageResponse<CourseInvitation>>({method: 'GET', url: `/api/invitations/sent?${params.toString()}`})
}

export function acceptInvitation(id: string) {
    return request<void>({method: 'PUT', url: `/api/invitations/${id}/accept`, silent: true})
}

export function declineInvitation(id: string) {
    return request<void>({method: 'PUT', url: `/api/invitations/${id}/decline`, silent: true})
}

export function withdrawInvitation(id: string) {
    return request<void>({method: 'DELETE', url: `/api/invitations/${id}`, silent: true})
}
