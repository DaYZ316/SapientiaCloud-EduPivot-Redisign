import {describe, expect, it} from 'vitest'

import {
    formatLatencyLabel,
    mergeOnlineParticipants,
    normalizeTrackStats,
    shortIdentity,
} from '@/features/classroom/composables/livePresence'
import type {ClassParticipant} from '@/features/course/types/classSession'

describe('livePresence', () => {
    it('merges local and remote participants with profile data', () => {
        const participants = mergeOnlineParticipants(
            {identity: 'teacher-id', name: 'Teacher Token Name'},
            [{identity: 'student-id'}, {identity: 'teacher-id'}],
            [
                participant('teacher-id', 'Teacher Li', 0),
                participant('student-id', 'Student Chen', 1),
            ],
            {
                'teacher-id': 'excellent',
                'student-id': 'good',
            },
        )

        expect(participants).toHaveLength(2)
        expect(participants[0]).toMatchObject({
            userId: 'teacher-id',
            displayName: 'Teacher Li',
            role: 0,
            isLocal: true,
            connectionQuality: 'excellent',
        })
        expect(participants[1]).toMatchObject({
            userId: 'student-id',
            displayName: 'Student Chen',
            role: 1,
            isLocal: false,
            connectionQuality: 'good',
        })
    })

    it('falls back to a short identity for unknown users', () => {
        expect(shortIdentity('1234567890abcdef')).toBe('12345678')
        expect(mergeOnlineParticipants(
            null,
            [{identity: '1234567890abcdef'}],
            [],
            {},
        )[0]).toMatchObject({
            displayName: '12345678',
            connectionQuality: 'unknown',
        })
    })

    it('normalizes RTT and packet loss stats', () => {
        const stats = normalizeTrackStats({
            roundTripTime: 0.0244,
            jitter: 0.006,
            packetsLost: 2,
            packetsReceived: 98,
        })

        expect(stats).toEqual({
            rttMs: 24,
            jitterMs: 6,
            packetLossPercent: 2,
        })
        expect(formatLatencyLabel(stats, true)).toBe('RTT: 24ms')
        expect(formatLatencyLabel(stats, false)).toBe('24ms')
    })

    it('normalizes RTT from RTCStatsReport candidate pairs', () => {
        const statsReport = new Map<string, unknown>([
            ['inbound-video', {
                type: 'inbound-rtp',
                jitter: 0.004,
                packetsLost: 1,
                packetsReceived: 99,
            }],
            ['candidate-pair', {
                type: 'candidate-pair',
                nominated: true,
                state: 'succeeded',
                currentRoundTripTime: 0.037,
            }],
        ])

        expect(normalizeTrackStats(statsReport)).toEqual({
            rttMs: 37,
            jitterMs: 4,
            packetLossPercent: 1,
        })
    })

    it('combines receiver media stats with peer connection RTT stats', () => {
        const stats = normalizeTrackStats([
            {
                type: 'video',
                jitter: 0.003,
                packetsLost: 2,
                packetsReceived: 198,
            },
            new Map<string, unknown>([
                ['selected-pair', {
                    type: 'candidate-pair',
                    selected: true,
                    currentRoundTripTime: 0.041,
                }],
            ]),
        ])

        expect(stats).toEqual({
            rttMs: 41,
            jitterMs: 3,
            packetLossPercent: 1,
        })
    })

    it('formats missing latency without inventing RTT', () => {
        const stats = normalizeTrackStats(undefined)

        expect(stats.rttMs).toBeNull()
        expect(formatLatencyLabel(stats, true)).toBe('Latency: --')
        expect(formatLatencyLabel(stats, false)).toBe('--ms')
    })
})

function participant(userId: string, displayName: string, role: number): ClassParticipant {
    return {
        id: `${userId}-participant`,
        sessionId: 'session-id',
        userId,
        role,
        seatIndex: role === 1 ? 1 : null,
        x: 0,
        y: 0,
        z: 0,
        displayName,
        avatarUrl: `${userId}.png`,
        joinedAt: '2026-06-24T10:00:00Z',
    }
}
