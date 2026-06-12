/**
 * 全局未读通知计数，供布局与通知页面共享。
 *
 * @author DaYZ
 * @since 2026-06-11
 */

import {ref} from 'vue'
import {getUnreadCount, subscribeNotifications} from '@/features/notification/api/notification'
import type {NotificationSubscription} from '@/features/notification/api/notification'

const unreadCount = ref(0)
let started = false
let intervalId: ReturnType<typeof setInterval> | undefined
let eventSource: NotificationSubscription | null = null

async function load() {
  try {
    const result = await getUnreadCount()
    unreadCount.value = result.total
  } catch {
    // Silently fail
  }
}

/** 通知页面已读单条时调用，直接递减避免等待轮询 */
function decrement(n: number = 1) {
  unreadCount.value = Math.max(0, unreadCount.value - n)
}

/** 标记全部已读后调用 */
function reset() {
  unreadCount.value = 0
}

/**
 * 启动未读计数（SSE + 轮询兜底）。幂等，多次调用只生效一次。
 * 在 MainLayout 的 onMounted 中调用。
 */
function start() {
  if (started) return
  started = true
  load()
  eventSource = subscribeNotifications(load)
  intervalId = setInterval(load, 30000)
}

/** 停止监听。在 MainLayout 的 onUnmounted 中调用。 */
function stop() {
  if (intervalId) {
    clearInterval(intervalId)
    intervalId = undefined
  }
  eventSource?.close()
  eventSource = null
  started = false
}

export function useUnreadCount() {
  return {unreadCount, start, stop, load, decrement, reset}
}
