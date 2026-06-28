import {onUnmounted, watch, type Ref} from 'vue'

import {heartbeatClassLive, heartbeatClassLiveKeepalive, pauseClassLive} from '@/features/course/api/classSession'
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
const TEACHER_LIVE_HEARTBEAT_INTERVAL_MS = 15000

const guardSessions = new Map<symbol, GuardedLiveSession>()
let guardedSession: GuardedLiveSession | null = null
let pageLifecycleListenersRegistered = false
let teacherLiveHeartbeatTimer: number | null = null

export function useTeacherLiveSessionGuard(options: TeacherLiveSessionGuardOptions) {
    const guardId = Symbol('teacher-live-session-guard')
    const stopWatch = watch(
        () => ({
            sessionId: options.session.value.id,
            liveStatus: options.session.value.liveStatus,
            isTeacher: options.isTeacher.value,
        }),
        ({sessionId, liveStatus, isTeacher}) => {
            if (isTeacher && isLiveStatus(liveStatus)) {
                guardSessions.set(guardId, {sessionId, liveStatus})
                registerPageLifecycleListeners()
            } else {
                guardSessions.delete(guardId)
            }
            refreshGuardedSession()
        },
        {immediate: true},
    )

    onUnmounted(() => {
        stopWatch()
        guardSessions.delete(guardId)
        refreshGuardedSession()
    })
}

export function pauseTeacherLiveBecauseOfUnexpectedDisconnect(sessionId: string) {
    if (guardedSession?.sessionId !== sessionId || !isLiveStatus(guardedSession.liveStatus)) {
        return Promise.resolve(null)
    }
    markGuardedSessionPaused(sessionId)
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

function registerPageLifecycleListeners() {
    if (pageLifecycleListenersRegistered || typeof window === 'undefined') {
        return
    }
    pageLifecycleListenersRegistered = true
    window.addEventListener('pagehide', handlePageLifecycleExit)
    window.addEventListener('beforeunload', handlePageLifecycleExit)
}

function handlePageLifecycleExit() {
    if (!guardedSession || !isLiveStatus(guardedSession.liveStatus)) {
        return
    }
    heartbeatClassLiveKeepalive(guardedSession.sessionId)
}

function refreshGuardedSession() {
    const activeSessions = Array.from(guardSessions.values())
    guardedSession = activeSessions.length > 0 ? activeSessions[activeSessions.length - 1] : null
    syncTeacherLiveHeartbeat()
}

function syncTeacherLiveHeartbeat() {
    if (!guardedSession || !isLiveStatus(guardedSession.liveStatus)) {
        stopTeacherLiveHeartbeat()
        return
    }
    void sendTeacherLiveHeartbeat()
    if (teacherLiveHeartbeatTimer != null || typeof window === 'undefined') {
        return
    }
    teacherLiveHeartbeatTimer = window.setInterval(() => {
        void sendTeacherLiveHeartbeat()
    }, TEACHER_LIVE_HEARTBEAT_INTERVAL_MS)
}

function stopTeacherLiveHeartbeat() {
    if (teacherLiveHeartbeatTimer == null || typeof window === 'undefined') {
        teacherLiveHeartbeatTimer = null
        return
    }
    window.clearInterval(teacherLiveHeartbeatTimer)
    teacherLiveHeartbeatTimer = null
}

async function sendTeacherLiveHeartbeat() {
    const session = guardedSession
    if (!session || !isLiveStatus(session.liveStatus)) {
        stopTeacherLiveHeartbeat()
        return
    }
    try {
        await heartbeatClassLive(session.sessionId)
    } catch {
        // Heartbeat failures are retried by the next interval.
    }
}

function markGuardedSessionPaused(sessionId: string) {
    for (const [guardId, session] of guardSessions) {
        if (session.sessionId === sessionId) {
            guardSessions.set(guardId, {
                ...session,
                liveStatus: ClassLiveStatus.PAUSED,
            })
        }
    }
    refreshGuardedSession()
}

function isLiveStatus(status: number) {
    return status === ClassLiveStatus.LIVE
}
