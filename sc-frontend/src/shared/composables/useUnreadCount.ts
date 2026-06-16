/**
 * 全局未读通知计数，供布局与通知页面共享。
 * <p>
 * 策略：SSE 直推 count（单播）+ 负载兜底查询（广播）+ 首次/重连同步。
 * 不再使用定时轮询。
 * <p>
 * 优化：in-flight 去重防止并发重复请求；广播场景 500ms debounce 防止请求风暴。
 *
 * @author DaYZ
 * @since 2026-06-11
 */

import {ref} from 'vue'
import type {NotificationSubscription, SsePayload} from '@/features/notification/api/notification'
import {getUnreadCount, subscribeNotifications} from '@/features/notification/api/notification'

const unreadCount = ref(0)
let started = false
let eventSource: NotificationSubscription | null = null
let inflightPromise: Promise<void> | null = null
let broadcastDebounceTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 请求去重：如果有正在进行的请求，直接复用其结果。
 */
async function load() {
    if (inflightPromise) return inflightPromise
    inflightPromise = doLoad()
    try {
        await inflightPromise
    } finally {
        inflightPromise = null
    }
}

async function doLoad() {
    try {
        const result = await getUnreadCount()
        unreadCount.value = result.total
    } catch {
        // Silently fail
    }
}

/**
 * 广播场景 debounce load：500ms 内多次广播只触发一次请求。
 */
function scheduleBroadcastLoad() {
    if (broadcastDebounceTimer) clearTimeout(broadcastDebounceTimer)
    broadcastDebounceTimer = setTimeout(() => {
        broadcastDebounceTimer = null
        load()
    }, 500)
}

/** 通知页面已读单条时调用，直接递减避免等待 SSE */
function decrement(n: number = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - n)
}

/** 标记全部已读后调用 */
function reset() {
    unreadCount.value = 0
}

/** SSE 推送回调：优先用 payload 中的精确 count，广播时兜底查询 */
function handleSsePayload(payload: SsePayload) {
    if (payload.unreadCount >= 0) {
        unreadCount.value = payload.unreadCount
    } else {
        // 广播场景，count 未知 → debounce 兜底查询
        scheduleBroadcastLoad()
    }
}

/**
 * 启动未读计数（SSE 直推）。幂等，多次调用只生效一次。
 * 在 MainLayout 的 onMounted 中调用。
 */
function start() {
    if (started) return
    started = true
    load()
    eventSource = subscribeNotifications(handleSsePayload)
}

/** 停止监听。在 MainLayout 的 onUnmounted 中调用。 */
function stop() {
    eventSource?.close()
    eventSource = null
    started = false
    if (broadcastDebounceTimer) {
        clearTimeout(broadcastDebounceTimer)
        broadcastDebounceTimer = null
    }
}

export function useUnreadCount() {
    return {unreadCount, start, stop, load, decrement, reset}
}
