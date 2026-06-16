import {readonly, ref} from 'vue'

export type GlobalNotificationType = 'success' | 'warn' | 'info' | 'error'

export interface GlobalNotificationItem {
    id: string
    type: GlobalNotificationType
    title?: string
    message: string
    duration: number
}

export interface GlobalNotificationOptions {
    title?: string
    message: string
    duration?: number
}

type GlobalNotificationInput = string | Error | GlobalNotificationOptions

const DEFAULT_DURATION = 3600
const MAX_VISIBLE = 5

const notificationItems = ref<GlobalNotificationItem[]>([])
const timers = new Map<string, ReturnType<typeof setTimeout>>()
let notificationCounter = 0

function normalizeInput(input: GlobalNotificationInput, options?: Omit<GlobalNotificationOptions, 'message'>) {
    if (typeof input === 'string') {
        return {
            title: options?.title,
            message: input,
            duration: options?.duration,
        }
    }

    if (input instanceof Error) {
        return {
            title: options?.title,
            message: input.message,
            duration: options?.duration,
        }
    }

    return {
        title: input.title ?? options?.title,
        message: input.message,
        duration: input.duration ?? options?.duration,
    }
}

function clearTimer(id: string) {
    const timer = timers.get(id)
    if (timer) {
        clearTimeout(timer)
        timers.delete(id)
    }
}

function dismiss(id: string) {
    clearTimer(id)
    notificationItems.value = notificationItems.value.filter((item) => item.id !== id)
}

function show(
    type: GlobalNotificationType,
    input: GlobalNotificationInput,
    options?: Omit<GlobalNotificationOptions, 'message'>,
) {
    const normalized = normalizeInput(input, options)
    const duration = normalized.duration ?? DEFAULT_DURATION
    const id = `notification-${Date.now()}-${notificationCounter++}`
    const item: GlobalNotificationItem = {
        id,
        type,
        title: normalized.title,
        message: normalized.message,
        duration,
    }
    const nextItems = [item, ...notificationItems.value]
    const removedItems = nextItems.slice(MAX_VISIBLE)

    removedItems.forEach((removed) => clearTimer(removed.id))
    notificationItems.value = nextItems.slice(0, MAX_VISIBLE)

    if (duration > 0) {
        timers.set(id, setTimeout(() => dismiss(id), duration))
    }

    return id
}

function clear() {
    timers.forEach((timer) => clearTimeout(timer))
    timers.clear()
    notificationItems.value = []
}

export const notify = {
    show,
    success: (input: GlobalNotificationInput, options?: Omit<GlobalNotificationOptions, 'message'>) =>
        show('success', input, options),
    warn: (input: GlobalNotificationInput, options?: Omit<GlobalNotificationOptions, 'message'>) =>
        show('warn', input, options),
    info: (input: GlobalNotificationInput, options?: Omit<GlobalNotificationOptions, 'message'>) =>
        show('info', input, options),
    error: (input: GlobalNotificationInput, options?: Omit<GlobalNotificationOptions, 'message'>) =>
        show('error', input, options),
    dismiss,
    clear,
}

export function useGlobalNotification() {
    return {
        notifications: readonly(notificationItems),
        ...notify,
    }
}
