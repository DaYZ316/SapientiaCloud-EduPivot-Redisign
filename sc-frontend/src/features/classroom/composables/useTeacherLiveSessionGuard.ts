import {onUnmounted, watch, type Ref} from 'vue'

import {pauseClassLive, pauseClassLiveKeepalive} from '@/features/course/api/classSession'
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

let guardedSession: GuardedLiveSession | null = null
let pageLifecycleListenersRegistered = false

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
        return
    }
    guardedSession = {
        ...guardedSession,
        liveStatus: ClassLiveStatus.PAUSED,
    }
    void pauseClassLive(sessionId)
        .then((session) => {
            if (typeof window === 'undefined') return
            window.dispatchEvent(new CustomEvent(TEACHER_LIVE_UNEXPECTED_PAUSED_EVENT, {
                detail: {session},
            }))
        })
        .catch(() => undefined)
}

function registerPageLifecycleListeners() {
    if (pageLifecycleListenersRegistered || typeof window === 'undefined') {
        return
    }
    pageLifecycleListenersRegistered = true
    window.addEventListener('pagehide', pauseGuardedLiveWithKeepalive)
    window.addEventListener('beforeunload', pauseGuardedLiveWithKeepalive)
}

function pauseGuardedLiveWithKeepalive() {
    if (!guardedSession || !isActiveLiveStatus(guardedSession.liveStatus)) {
        return
    }
    pauseClassLiveKeepalive(guardedSession.sessionId)
    guardedSession = {
        ...guardedSession,
        liveStatus: ClassLiveStatus.PAUSED,
    }
}

function isActiveLiveStatus(status: number) {
    return status === ClassLiveStatus.LIVE || status === ClassLiveStatus.PAUSED
}
