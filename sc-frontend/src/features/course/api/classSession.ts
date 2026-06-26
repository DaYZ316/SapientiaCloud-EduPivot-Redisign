import {ACCESS_TOKEN_KEY, request} from '@/shared/api/request'
import {subscribeSse, sseUrl} from '@/shared/api/sseManager'
import type {PageResponse} from '@/shared/types/common'
import type {
    ClassBarrage,
    ClassBarrageSubscription,
    ClassParticipant,
    ClassSeatSyncToken,
    ClassSession,
    CreateClassSessionRequest,
    JoinClassSessionRequest,
    LiveKitToken,
    UpdateClassSessionRequest,
} from '@/features/course/types/classSession'
const CLASS_BARRAGE_IDLE_TIMEOUT_MS = 30000

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

export function listClassSessionParticipants(id: string) {
    return request<ClassParticipant[]>({method: 'GET', url: `/api/class-sessions/${id}/participants`, silent: true})
}

export function leaveClassSessionSeat(id: string) {
    return request<void>({method: 'DELETE', url: `/api/class-sessions/${id}/participants/me`, silent: true})
}

export function issueClassSessionSeatSyncToken(id: string) {
    return request<ClassSeatSyncToken>({
        method: 'POST',
        url: `/api/class-sessions/${id}/seat-sync-token`,
        silent: true,
    })
}

export function startClassLive(id: string) {
    return request<ClassSession>({method: 'POST', url: `/api/class-sessions/${id}/live/start`, silent: true})
}

export function pauseClassLive(id: string) {
    return request<ClassSession>({method: 'POST', url: `/api/class-sessions/${id}/live/pause`, silent: true})
}

export function heartbeatClassLive(id: string) {
    return request<void>({method: 'POST', url: `/api/class-sessions/${id}/live/heartbeat`, silent: true})
}

export function heartbeatClassLiveKeepalive(id: string) {
    if (typeof navigator === 'undefined') {
        return false
    }
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
    }
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (token) {
        headers.Authorization = `Bearer ${token}`
    }

    return fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/class-sessions/${id}/live/heartbeat`, {
        method: 'POST',
        headers,
        body: '{}',
        keepalive: true,
    }).catch(() => undefined)
}

export function resumeClassLive(id: string) {
    return request<ClassSession>({method: 'POST', url: `/api/class-sessions/${id}/live/resume`, silent: true})
}

export function stopClassLive(id: string) {
    return request<ClassSession>({method: 'POST', url: `/api/class-sessions/${id}/live/stop`, silent: true})
}

export function issueClassLiveToken(id: string) {
    return request<LiveKitToken>({method: 'POST', url: `/api/class-sessions/${id}/live-token`, silent: true})
}

export function listClassBarrages(id: string, page: number = 1, size: number = 20) {
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    return request<PageResponse<ClassBarrage>>({
        method: 'GET',
        url: `/api/class-sessions/${id}/barrages?${params.toString()}`,
        silent: true,
    })
}

export function sendClassBarrage(id: string, content: string) {
    return request<ClassBarrage>({
        method: 'POST',
        url: `/api/class-sessions/${id}/barrages`,
        data: {content},
        silent: true,
    })
}

export function subscribeClassBarrages(
    id: string,
    onMessage: (message: ClassBarrage) => void,
    replayOnReconnect?: () => void | Promise<void>,
): ClassBarrageSubscription {
    return subscribeSse<ClassBarrage>({
        key: `class-barrage:${id}`,
        url: sseUrl(`/api/class-sessions/${id}/barrages/stream`),
        eventNames: ['barrage'],
        idleTimeoutMs: CLASS_BARRAGE_IDLE_TIMEOUT_MS,
        replayOnReconnect,
        onMessage,
    })
}
