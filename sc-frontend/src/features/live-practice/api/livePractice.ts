import {fetchEventSource} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY, refreshSession, request} from '@/shared/api/request'
import type {
    CreateLivePracticeRequest,
    LivePracticeEvent,
    LivePracticeGroup,
    LivePracticeSubscription,
    LivePracticeWorkbookItem,
    SubmitLivePracticeAnswerRequest,
    LivePracticeSubmission,
} from '@/features/live-practice/types/livePractice'

class UnauthorizedSseError extends Error {
    constructor() {
        super('SSE connection unauthorized')
        this.name = 'UnauthorizedSseError'
    }
}

export function createLivePractice(sessionId: string, data: CreateLivePracticeRequest) {
    return request<string>({
        method: 'POST',
        url: `/api/class-sessions/${sessionId}/live-practices`,
        data,
        silent: true,
    })
}

export function listClassSessionLivePractices(sessionId: string) {
    return request<LivePracticeGroup[]>({
        method: 'GET',
        url: `/api/class-sessions/${sessionId}/live-practices`,
        silent: true,
    })
}

export function getLivePractice(groupId: string) {
    return request<LivePracticeGroup>({method: 'GET', url: `/api/live-practices/${groupId}`, silent: true})
}

export function submitLivePracticeAnswer(
    groupId: string,
    questionSnapshotId: string,
    data: SubmitLivePracticeAnswerRequest,
) {
    return request<LivePracticeSubmission>({
        method: 'POST',
        url: `/api/live-practices/${groupId}/questions/${questionSnapshotId}/submissions`,
        data,
        silent: true,
    })
}

export function getLivePracticeWorkbook(courseId: string) {
    return request<LivePracticeWorkbookItem[]>({
        method: 'GET',
        url: `/api/courses/${courseId}/live-practice-workbook`,
        silent: true,
    })
}

export function getTeacherLivePractices(courseId: string) {
    return request<LivePracticeGroup[]>({
        method: 'GET',
        url: `/api/courses/${courseId}/live-practices/teacher`,
        silent: true,
    })
}

export function subscribeLivePractices(onMessage: (event: LivePracticeEvent) => void): LivePracticeSubscription {
    const controller = new AbortController()
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
    void connectLivePracticeStream(`${baseUrl}/api/live-practices/subscribe`, controller, onMessage)

    return {
        close: () => controller.abort(),
    }
}

async function connectLivePracticeStream(
    url: string,
    controller: AbortController,
    onMessage: (event: LivePracticeEvent) => void,
) {
    let retriedAfterRefresh = false

    while (!controller.signal.aborted) {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY)
        if (!token) {
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
                        return
                    }
                    if (response.status === 401) {
                        throw new UnauthorizedSseError()
                    }
                    throw new Error(`Live practice SSE failed: ${response.status}`)
                },
                onmessage(event) {
                    if (event.event !== 'live-practice' || !event.data) return
                    onMessage(JSON.parse(event.data) as LivePracticeEvent)
                },
                onerror(error) {
                    if (error instanceof UnauthorizedSseError) {
                        throw error
                    }
                    return 3000
                },
            })
            await new Promise(resolve => setTimeout(resolve, 1000))
        } catch (error) {
            if (controller.signal.aborted) return
            if (error instanceof UnauthorizedSseError && !retriedAfterRefresh && await refreshSession()) {
                retriedAfterRefresh = true
                continue
            }
            await new Promise(resolve => setTimeout(resolve, 3000))
        }
    }
}
