import {readonly, ref} from 'vue'

import {subscribeLivePractices} from '@/features/live-practice/api/livePractice'
import type {LivePracticeEvent, LivePracticeSubscription} from '@/features/live-practice/types/livePractice'

const events = ref<LivePracticeEvent[]>([])
let started = false
let subscription: LivePracticeSubscription | null = null

function start() {
    if (started) return
    started = true
    subscription = subscribeLivePractices((event) => {
        if (events.value.some(item => item.groupId === event.groupId)) {
            return
        }
        events.value = [event, ...events.value].slice(0, 3)
    })
}

function stop() {
    subscription?.close()
    subscription = null
    started = false
}

function dismiss(groupId: string) {
    events.value = events.value.filter(event => event.groupId !== groupId)
}

export function useLivePracticeEvents() {
    return {
        events: readonly(events),
        start,
        stop,
        dismiss,
    }
}
