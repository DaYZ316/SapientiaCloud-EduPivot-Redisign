import {request} from '@/shared/api/request'
import {sseUrl, subscribeSse} from '@/shared/api/sseManager'
import type {
    CreateLivePracticeRequest,
    LivePracticeEvent,
    LivePracticeGroup,
    LivePracticeSubmission,
    LivePracticeSubscription,
    LivePracticeWorkbookItem,
    SubmitLivePracticeAnswerRequest,
} from '@/features/live-practice/types/livePractice'

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
    return subscribeSse<LivePracticeEvent>({
        key: 'live-practices',
        url: sseUrl('/api/live-practices/subscribe'),
        eventNames: ['live-practice'],
        onMessage,
    })
}
