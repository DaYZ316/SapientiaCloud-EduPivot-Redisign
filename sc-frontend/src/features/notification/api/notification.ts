import {request} from '@/shared/api/request'
import {sseUrl, subscribeSse} from '@/shared/api/sseManager'
import type {PageResponse} from '@/shared/types/common'

export interface Notification {
    id: string
    type: number
    title: string
    content: string
    senderId: string | null
    targetType: number
    createdAt: string
    updatedAt?: string | null
    isRead: boolean
    readAt: string | null
}

export interface UnreadCount {
    total: number
    system: number
    teaching: number
}

export interface SendNotificationRequest {
    type: number
    title: string
    content: string
    targetType: number
    userIds?: string[]
}

export interface NotificationSubscription {
    close: () => void
}

export interface SsePayload {
    notification: Notification
    unreadCount: number
}

export function getNotifications(page: number = 1, size: number = 10, type?: number, sentByMe?: boolean) {
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    if (type !== undefined) {
        params.append('type', type.toString())
    }
    if (sentByMe) {
        params.append('sentByMe', 'true')
    }
    return request<PageResponse<Notification>>({method: 'GET', url: `/api/notifications?${params.toString()}`})
}

export function getUnreadCount() {
    return request<UnreadCount>({method: 'GET', url: '/api/notifications/unread-count'})
}

export function markAsRead(id: string) {
    return request<void>({method: 'PUT', url: `/api/notifications/${id}/read`})
}

export function markAllAsRead(type?: number) {
    const params = new URLSearchParams()
    if (type !== undefined) {
        params.append('type', type.toString())
    }
    const qs = params.toString()
    return request<void>({method: 'PUT', url: `/api/notifications/read-all${qs ? `?${qs}` : ''}`})
}

export function deleteNotification(id: string) {
    return request<void>({method: 'DELETE', url: `/api/notifications/${id}`})
}

export function recallNotification(id: string) {
    return request<void>({method: 'DELETE', url: `/api/notifications/${id}/recall`})
}

export function deleteAllNotifications(type?: number) {
    const params = type !== undefined ? `?type=${type}` : ''
    return request<void>({method: 'DELETE', url: `/api/notifications/all${params}`})
}

export function sendNotification(data: SendNotificationRequest) {
    return request<string>({method: 'POST', url: '/api/notifications', data})
}

export function subscribeNotifications(
    onMessage: (payload: SsePayload) => void,
    replayOnReconnect?: () => void | Promise<void>,
): NotificationSubscription {
    return subscribeSse<SsePayload>({
        key: 'notifications',
        url: sseUrl('/api/notifications/subscribe'),
        eventNames: ['notification'],
        replayOnReconnect,
        onMessage,
    })
}
