import {socketUrl} from '@/shared/platform/runtime'

export function buildSeatSyncSocketUrl(sessionId: string, token: string) {
    return socketUrl('/api/class-sessions/seats/ws', new URLSearchParams({
        sessionId,
        token,
    }))
}
