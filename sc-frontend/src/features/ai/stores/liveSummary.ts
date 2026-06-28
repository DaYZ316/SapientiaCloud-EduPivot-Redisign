import {ref} from 'vue'
import {defineStore} from 'pinia'

import type {LiveSummarySession, LiveSummarySnapshot, LiveTranscriptSegment} from '@/features/ai/types/ai'

type LiveSummaryEntry = {
    session: LiveSummarySession | null
    latestSnapshot: LiveSummarySnapshot | null
    snapshotHistory: LiveSummarySnapshot[]
    recentTranscripts: LiveTranscriptSegment[]
    partialTranscript: LiveTranscriptSegment | null
}

const DEFAULT_TRANSCRIPT_LIMIT = 80
const DEFAULT_SNAPSHOT_LIMIT = 40

export const useLiveSummaryStore = defineStore('liveSummary', () => {
    const entries = ref<Record<string, LiveSummaryEntry>>({})

    function entryFor(classSessionId: string) {
        return entries.value[classSessionId] ?? null
    }

    function applySession(next: LiveSummarySession, options: { transcriptLimit?: number } = {}) {
        const entry = ensureEntry(next.classSessionId)
        entry.session = next
        if (!next.id) {
            entry.latestSnapshot = null
            entry.snapshotHistory = []
        } else if (next.latestSnapshot) {
            applySnapshotToEntry(entry, next.latestSnapshot)
        }
        entry.recentTranscripts = sortTranscripts(next.recentTranscripts || [])
            .slice(-(options.transcriptLimit ?? DEFAULT_TRANSCRIPT_LIMIT))
        if (next.status !== 'RUNNING' || !next.id) {
            entry.partialTranscript = null
        }
    }

    function applySnapshot(snapshot: LiveSummarySnapshot) {
        const entry = ensureEntry(snapshot.classSessionId)
        applySnapshotToEntry(entry, snapshot)
    }

    function applySnapshots(classSessionId: string, snapshots: LiveSummarySnapshot[]) {
        const entry = ensureEntry(classSessionId)
        entry.snapshotHistory = mergeSnapshotHistory(entry.snapshotHistory, snapshots)
    }

    function replaceSnapshots(classSessionId: string, snapshots: LiveSummarySnapshot[]) {
        const entry = ensureEntry(classSessionId)
        entry.snapshotHistory = mergeSnapshotHistory([], snapshots)
        if (entry.latestSnapshot && !entry.snapshotHistory.some(snapshot => snapshot.id === entry.latestSnapshot?.id)) {
            entry.latestSnapshot = entry.snapshotHistory[0] ?? null
        }
    }

    function applyTranscript(segment: LiveTranscriptSegment, options: { transcriptLimit?: number } = {}) {
        const entry = ensureEntry(segment.classSessionId)
        if (segment.final === false) {
            entry.partialTranscript = segment
            return
        }
        entry.partialTranscript = null
        if (!segment.text) return

        const next = entry.recentTranscripts.filter(item => item.id !== segment.id)
        next.push(segment)
        entry.recentTranscripts = sortTranscripts(next).slice(-(options.transcriptLimit ?? DEFAULT_TRANSCRIPT_LIMIT))
    }

    function clearSession(classSessionId: string) {
        entries.value[classSessionId] = createEntry()
    }

    function ensureEntry(classSessionId: string) {
        entries.value[classSessionId] ??= createEntry()
        return entries.value[classSessionId]
    }

    return {
        entryFor,
        applySession,
        applySnapshot,
        applySnapshots,
        replaceSnapshots,
        applyTranscript,
        clearSession,
    }
})

function createEntry(): LiveSummaryEntry {
    return {
        session: null,
        latestSnapshot: null,
        snapshotHistory: [],
        recentTranscripts: [],
        partialTranscript: null,
    }
}

function applySnapshotToEntry(entry: LiveSummaryEntry, snapshot: LiveSummarySnapshot) {
    entry.latestSnapshot = snapshot
    entry.snapshotHistory = mergeSnapshotHistory(entry.snapshotHistory, [snapshot])
}

function mergeSnapshotHistory(current: LiveSummarySnapshot[], incoming: LiveSummarySnapshot[]) {
    return [...incoming, ...current]
        .filter((snapshot, index, snapshots) => snapshots.findIndex(item => item.id === snapshot.id) === index)
        .sort((left, right) => right.sequenceNo - left.sequenceNo)
        .slice(0, DEFAULT_SNAPSHOT_LIMIT)
}

function sortTranscripts(items: LiveTranscriptSegment[]) {
    return [...items].sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))
}
