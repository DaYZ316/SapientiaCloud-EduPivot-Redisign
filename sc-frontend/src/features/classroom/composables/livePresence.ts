import type {ClassParticipant} from '@/features/course/types/classSession'

export interface LiveParticipantLike {
    identity: string
    name?: string
}

export interface LiveOnlineParticipant {
    identity: string
    userId: string
    displayName: string
    avatarUrl: string | null
    role: number | null
    isLocal: boolean
    connectionQuality: string
}

export interface LiveNetworkStats {
    rttMs: number | null
    jitterMs: number | null
    packetLossPercent: number | null
}

export interface LiveTrackStatsLike {
    type?: string
    roundTripTime?: number
    currentRoundTripTime?: number
    totalRoundTripTime?: number
    responsesReceived?: number
    jitter?: number
    packetsLost?: number
    packetsReceived?: number
    packetsSent?: number
    nominated?: boolean
    selected?: boolean
    state?: string
}

export const EMPTY_LIVE_NETWORK_STATS: LiveNetworkStats = {
    rttMs: null,
    jitterMs: null,
    packetLossPercent: null,
}

export function mergeOnlineParticipants(
    localParticipant: LiveParticipantLike | null,
    remoteParticipants: Iterable<LiveParticipantLike>,
    sessionParticipants: ClassParticipant[],
    connectionQualityByIdentity: Readonly<Record<string, string>>,
): LiveOnlineParticipant[] {
    const participantInfoByUserId = new Map(sessionParticipants.map(participant => [participant.userId, participant]))
    const participants: LiveOnlineParticipant[] = []
    const seen = new Set<string>()

    if (localParticipant) {
        participants.push(toOnlineParticipant(localParticipant, participantInfoByUserId, connectionQualityByIdentity, true))
        seen.add(localParticipant.identity)
    }
    for (const participant of remoteParticipants) {
        if (seen.has(participant.identity)) {
            continue
        }
        participants.push(toOnlineParticipant(participant, participantInfoByUserId, connectionQualityByIdentity, false))
        seen.add(participant.identity)
    }
    return participants
}

export function normalizeTrackStats(stats: unknown): LiveNetworkStats {
    const items = toStatsItems(stats)
    const rttItem = findRttStats(items)
    const mediaItem = items.find(hasMediaPacketStats) ?? items[0]
    if (!rttItem && !mediaItem) {
        return {...EMPTY_LIVE_NETWORK_STATS}
    }
    return {
        rttMs: secondsToMs(rttSeconds(rttItem)),
        jitterMs: secondsToMs(mediaItem?.jitter),
        packetLossPercent: mediaItem ? packetLossPercent(mediaItem) : null,
    }
}

export function formatLatencyLabel(stats: LiveNetworkStats, fullscreen: boolean) {
    if (stats.rttMs != null) {
        return fullscreen ? `RTT: ${stats.rttMs}ms` : `${stats.rttMs}ms`
    }
    return fullscreen ? 'Latency: --' : '--ms'
}

export function shortIdentity(identity: string) {
    return identity.length > 8 ? identity.slice(0, 8) : identity
}

function toOnlineParticipant(
    participant: LiveParticipantLike,
    participantInfoByUserId: Map<string, ClassParticipant>,
    connectionQualityByIdentity: Readonly<Record<string, string>>,
    isLocal: boolean,
): LiveOnlineParticipant {
    const userInfo = participantInfoByUserId.get(participant.identity)
    return {
        identity: participant.identity,
        userId: participant.identity,
        displayName: userInfo?.displayName || participant.name || shortIdentity(participant.identity),
        avatarUrl: userInfo?.avatarUrl ?? null,
        role: userInfo?.role ?? null,
        isLocal,
        connectionQuality: connectionQualityByIdentity[participant.identity] || 'unknown',
    }
}

function secondsToMs(value: number | undefined) {
    if (value == null || !Number.isFinite(value)) {
        return null
    }
    return Math.max(0, Math.round(value * 1000))
}

function toStatsItems(stats: unknown): LiveTrackStatsLike[] {
    if (!stats) {
        return []
    }
    if (Array.isArray(stats)) {
        return stats.reduce<LiveTrackStatsLike[]>((items, item) => items.concat(toStatsItems(item)), [])
    }
    if (isStatsReportLike(stats)) {
        const items: LiveTrackStatsLike[] = []
        stats.forEach((item) => {
            if (isStatsLike(item)) {
                items.push(item)
            }
        })
        return items
    }
    return isStatsLike(stats) ? [stats] : []
}

function isStatsReportLike(value: unknown): value is { forEach(callback: (item: unknown) => void): void } {
    return typeof value === 'object'
        && value != null
        && typeof (value as { forEach?: unknown }).forEach === 'function'
}

function isStatsLike(value: unknown): value is LiveTrackStatsLike {
    return typeof value === 'object' && value != null
}

function findRttStats(items: LiveTrackStatsLike[]) {
    return items.find(item => item.roundTripTime != null)
        ?? items.find(item => item.currentRoundTripTime != null && (item.nominated || item.selected))
        ?? items.find(item => item.currentRoundTripTime != null && item.state === 'succeeded')
        ?? items.find(item => item.currentRoundTripTime != null)
        ?? items.find(item => item.totalRoundTripTime != null && item.responsesReceived != null)
}

function rttSeconds(item?: LiveTrackStatsLike) {
    if (!item) {
        return undefined
    }
    if (item.roundTripTime != null) {
        return item.roundTripTime
    }
    if (item.currentRoundTripTime != null) {
        return item.currentRoundTripTime
    }
    if (item.totalRoundTripTime != null && item.responsesReceived != null && item.responsesReceived > 0) {
        return item.totalRoundTripTime / item.responsesReceived
    }
    return undefined
}

function hasMediaPacketStats(stats: LiveTrackStatsLike) {
    return stats.packetsReceived != null || stats.packetsSent != null
}

function packetLossPercent(stats: LiveTrackStatsLike) {
    const lost = stats.packetsLost ?? 0
    const total = lost + (stats.packetsReceived ?? stats.packetsSent ?? 0)
    if (total <= 0) {
        return null
    }
    return Math.max(0, Math.round((lost / total) * 1000) / 10)
}
