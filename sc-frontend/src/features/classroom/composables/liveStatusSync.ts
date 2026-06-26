import type {SeatSyncMessage} from '@/features/classroom/types/classroom'
import type {ClassSession} from '@/features/course/types/classSession'

type LiveTimestampField = 'liveStartedAt' | 'livePausedAt' | 'liveEndedAt'

export function mergeSeatSyncLiveStatus(session: ClassSession, message: SeatSyncMessage): ClassSession {
    if (message.liveStatus == null) {
        return session
    }
    return {
        ...session,
        liveStatus: message.liveStatus,
        liveStatusText: message.liveStatusText ?? session.liveStatusText,
        liveStartedAt: liveTimestamp(message, 'liveStartedAt', session.liveStartedAt),
        livePausedAt: liveTimestamp(message, 'livePausedAt', session.livePausedAt),
        liveEndedAt: liveTimestamp(message, 'liveEndedAt', session.liveEndedAt),
    }
}

function liveTimestamp(message: SeatSyncMessage, field: LiveTimestampField, fallback: string | null) {
    return Object.prototype.hasOwnProperty.call(message, field) ? message[field] ?? null : fallback
}
