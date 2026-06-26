import {onUnmounted, watch, type Ref} from 'vue'

import {heartbeatClassLiveKeepalive, pauseClassLive} from '@/features/course/api/classSession'
import {ClassLiveStatus, type ClassSession} from '@/features/course/types/classSession'

type TeacherLiveSessionGuardOptions = {
    session: Ref<ClassSession>
    isTeacher: Ref<boolean>
}

type GuardedLiveSession = {
    sessionId: string
    liveStatus: number
}

export const TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT = 'edupivot:teacher-live-unexpected-paused'
export const CLASSROOM_LIVE_REMOTE_PAUSED_EVENT = 'edupivot:classroom-live-remote-paused'

let guardedSession: GuardedLiveSession | null = null
let pageLifecycleListenersRegistered = false
let pageLifecycleLeaving = false

export function useTeacherLiveSessionGuard(options: TeacherLiveSessionGuardOptions) {
    const stopWatch = watch(
        () => ({
            sessionId: options.session.value.id,
            liveStatus: options.session.value.liveStatus,
            isTeacher: options.isTeacher.value,
        }),
        ({sessionId, liveStatus, isTeacher}) => {
            if (isTeacher && isActiveLiveStatus(liveStatus)) {
                guardedSession = {sessionId, liveStatus}
                registerPageLifecycleListeners()
                return
            }
            if (guardedSession?.sessionId === sessionId) {
                guardedSession = null
            }
        },
        {immediate: true},
    )

    onUnmounted(stopWatch)
}

export function pauseTeacherLiveBecauseOfUnexpectedDisconnect(sessionId: string) {
    if (guardedSession?.sessionId !== sessionId || !isActiveLiveStatus(guardedSession.liveStatus)) {
        return Promise.resolve(null)
    }
    guardedSession = {
        ...guardedSession,
        liveStatus: ClassLiveStatus.PAUSED,
    }
    return pauseClassLive(sessionId)
        .then((session) => {
            if (typeof window === 'undefined') return session
            window.dispatchEvent(new CustomEvent(TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT, {
                detail: {session},
            }))
            return session
        })
        .catch(() => null)
}

export function notifyRemoteLivePaused(session: ClassSession) {
    if (typeof window === 'undefined') {
        return
    }
    window.dispatchEvent(new CustomEvent(CLASSROOM_LIVE_REMOTE_PAUSED_EVENT, {
        detail: {
            session: {
                ...session,
                liveStatus: ClassLiveStatus.PAUSED,
            },
        },
    }))
}

export function isTeacherLivePageLifecycleLeaving() {
    return pageLifecycleLeaving
}

function registerPageLifecycleListeners() {
    if (pageLifecycleListenersRegistered || typeof window === 'undefined') {
        return
    }
    pageLifecycleListenersRegistered = true
    window.addEventListener('pagehide', handlePageLifecycleExit)
    window.addEventListener('beforeunload', handlePageLifecycleExit)
    window.addEventListener('pageshow', handlePageLifecycleReturn)
}

function handlePageLifecycleExit() {
    pageLifecycleLeaving = true
    if (!guardedSession || !isActiveLiveStatus(guardedSession.liveStatus)) {
        return
    }
    heartbeatClassLiveKeepalive(guardedSession.sessionId)
}

function handlePageLifecycleReturn() {
    pageLifecycleLeaving = false
}

function isActiveLiveStatus(status: number) {
    return status === ClassLiveStatus.LIVE || status === ClassLiveStatus.PAUSED
}
