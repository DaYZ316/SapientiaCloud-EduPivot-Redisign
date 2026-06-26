import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {ClassLiveStatus, type ClassSession} from '@/features/course/types/classSession'

const MINI_WINDOW_STORAGE_KEY = 'edupivot.classroomLiveMini'

type StoredMiniWindow = {
    sessionId: string
    isTeacher: boolean
    canParticipate: boolean
}

function readStoredMiniWindow() {
    if (typeof sessionStorage === 'undefined') {
        return null
    }
    const raw = sessionStorage.getItem(MINI_WINDOW_STORAGE_KEY)
    if (!raw) {
        return null
    }
    try {
        return JSON.parse(raw) as StoredMiniWindow
    } catch {
        sessionStorage.removeItem(MINI_WINDOW_STORAGE_KEY)
        return null
    }
}

function writeStoredMiniWindow(payload: StoredMiniWindow) {
    if (typeof sessionStorage === 'undefined') {
        return
    }
    sessionStorage.setItem(MINI_WINDOW_STORAGE_KEY, JSON.stringify(payload))
}

function clearStoredMiniWindow() {
    if (typeof sessionStorage === 'undefined') {
        return
    }
    sessionStorage.removeItem(MINI_WINDOW_STORAGE_KEY)
}

export const useClassroomLiveMiniStore = defineStore('classroomLiveMini', () => {
    const session = ref<ClassSession | null>(null)
    const isTeacher = ref(false)
    const canParticipate = ref(false)
    const storedMiniWindow = ref<StoredMiniWindow | null>(readStoredMiniWindow())

    const active = computed(() => Boolean(session.value
        && isTeacher.value
        && canParticipate.value
        && session.value.liveStatus === ClassLiveStatus.LIVE))
    const restoreSessionId = computed(() => session.value?.id ?? storedMiniWindow.value?.sessionId ?? null)

    function show(payload: {session: ClassSession; isTeacher: boolean; canParticipate: boolean}) {
        session.value = payload.session
        isTeacher.value = payload.isTeacher
        canParticipate.value = payload.canParticipate
        storedMiniWindow.value = {
            sessionId: payload.session.id,
            isTeacher: payload.isTeacher,
            canParticipate: payload.canParticipate,
        }
        writeStoredMiniWindow(storedMiniWindow.value)
    }

    function updateSession(nextSession: ClassSession) {
        if (session.value?.id !== nextSession.id) {
            return
        }
        session.value = nextSession
        if (nextSession.liveStatus !== ClassLiveStatus.LIVE) {
            clear()
        }
    }

    function clearSession(sessionId: string) {
        if (session.value?.id === sessionId || storedMiniWindow.value?.sessionId === sessionId) {
            clear()
        }
    }

    function clear() {
        session.value = null
        isTeacher.value = false
        canParticipate.value = false
        storedMiniWindow.value = null
        clearStoredMiniWindow()
    }

    return {
        session,
        isTeacher,
        canParticipate,
        active,
        restoreSessionId,
        show,
        updateSession,
        clearSession,
        clear,
    }
})
