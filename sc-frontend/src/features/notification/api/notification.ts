/**
 * 通知相关 API。
 *
 * @author DaYZ
 * @since 2026-06-09
 */

import {fetchEventSource} from '@microsoft/fetch-event-source'

import {refreshSession, request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'

const ACCESS_TOKEN_KEY = 'aeroverse.accessToken'

export interface Notification {
    id: string
    type: number
    title: string
    content: string
    senderId: string | null
    targetType: number
    createdAt: string
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

class UnauthorizedSseError extends Error {
    constructor() {
        super('SSE connection unauthorized')
        this.name = 'UnauthorizedSseError'
    }
}

/**
 * 获取通知列表。
 */
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

/**
 * 获取未读通知数量。
 */
export function getUnreadCount() {
    return request<UnreadCount>({method: 'GET', url: '/api/notifications/unread-count'})
}

/**
 * 标记单条通知为已读。
 */
export function markAsRead(id: string) {
    return request<void>({method: 'PUT', url: `/api/notifications/${id}/read`})
}

/**
 * 标记所有通知为已读。
 */
export function markAllAsRead(type?: number) {
    const params = type !== undefined ? `?type=${type}` : ''
    return request<void>({method: 'PUT', url: `/api/notifications/read-all${params}`})
}

/**
 * 删除单条通知（per-user 软删除）。
 */
export function deleteNotification(id: string) {
    return request<void>({method: 'DELETE', url: `/api/notifications/${id}`})
}

/**
 * 撤回通知（仅发送者可操作，对所有接收者生效）。
 */
export function recallNotification(id: string) {
    return request<void>({method: 'DELETE', url: `/api/notifications/${id}/recall`})
}

/**
 * 删除所有通知（per-user 软删除）。
 */
export function deleteAllNotifications(type?: number) {
    const params = type !== undefined ? `?type=${type}` : ''
    return request<void>({method: 'DELETE', url: `/api/notifications/all${params}`})
}

/**
 * 发送通知（仅管理员和教师可调用）。
 */
export function sendNotification(data: SendNotificationRequest) {
    return request<string>({method: 'POST', url: '/api/notifications', data})
}

/**
 * 订阅 SSE 通知。
 */
export function subscribeNotifications(onMessage: (notification: Notification) => void): NotificationSubscription {
    const controller = new AbortController()
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
    void connectNotificationStream(`${baseUrl}/api/notifications/subscribe`, controller, onMessage)

    return {
        close: () => controller.abort(),
    }
}

async function connectNotificationStream(
    url: string,
    controller: AbortController,
    onMessage: (notification: Notification) => void,
) {
    let retriedAfterRefresh = false

    while (!controller.signal.aborted) {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY)

        if (!token) {
            // 没有 token 时等待一段时间后重试
            await new Promise(resolve => setTimeout(resolve, 5000))
            continue
        }

        try {
            await fetchEventSource(url, {
                headers: {
                    accept: 'text/event-stream',
                    Authorization: `Bearer ${token}`,
                },
                openWhenHidden: true,
                signal: controller.signal,
                async onopen(response) {
                    if (response.ok) {
                        retriedAfterRefresh = false
                        console.log('SSE connected')
                        return
                    }

                    if (response.status === 401) {
                        throw new UnauthorizedSseError()
                    }

                    throw new Error(`SSE connection failed: ${response.status}`)
                },
                onmessage(event) {
                    if (event.event !== 'notification' || !event.data) {
                        return
                    }

                    const notification = JSON.parse(event.data) as Notification
                    onMessage(notification)
                },
                onerror(error) {
                    if (error instanceof UnauthorizedSseError) {
                        throw error
                    }
                    console.error('SSE connection error', error)
                    // 返回数字表示重试延迟（毫秒）
                    return 3000
                },
            })
            // 连接正常关闭，等待后重连
            await new Promise(resolve => setTimeout(resolve, 1000))
        } catch (error) {
            if (controller.signal.aborted) {
                return
            }

            if (error instanceof UnauthorizedSseError && !retriedAfterRefresh && await refreshSession()) {
                retriedAfterRefresh = true
                continue
            }

            console.error('SSE connection error', error)
            // 等待后重连
            await new Promise(resolve => setTimeout(resolve, 3000))
        }
    }
}
