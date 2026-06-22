import {fetchEventSource} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY, refreshSession} from '@/shared/api/request'

export type SseConnectionKey = string

export interface SseSubscription {
    close: () => void
}

export interface SseConnectionOptions<T> {
    key: SseConnectionKey
    url: string
    eventNames?: string[]
    idleTimeoutMs?: number
    replayOnReconnect?: () => void | Promise<void>
    onMessage: (payload: T) => void
}

const DEFAULT_RETRY_MS = 3000
const MAX_RETRY_MS = 30000
const NO_SUBSCRIBER_IDLE_MS = 0

const connections = new Map<SseConnectionKey, SseConnection<unknown>>()

class UnauthorizedSseError extends Error {
    constructor() {
        super('SSE connection unauthorized')
        this.name = 'UnauthorizedSseError'
    }
}

interface SseHandler<T> {
    eventNames?: Set<string>
    onMessage: (payload: T) => void
    replayOnReconnect?: () => void | Promise<void>
}

interface SseConnection<T> {
    key: SseConnectionKey
    url: string
    controller: AbortController | null
    handlers: Map<number, SseHandler<T>>
    retryMs: number
    idleTimeoutMs: number
    idleTimer: ReturnType<typeof setTimeout> | null
    running: boolean
    stopped: boolean
    openedOnce: boolean
    nextHandlerId: number
}

export function subscribeSse<T>(options: SseConnectionOptions<T>): SseSubscription {
    const connection = getConnection<T>(options)
    const handlerId = connection.nextHandlerId++
    connection.handlers.set(handlerId, {
        eventNames: options.eventNames?.length ? new Set(options.eventNames) : undefined,
        onMessage: options.onMessage,
        replayOnReconnect: options.replayOnReconnect,
    })
    connection.stopped = false
    clearIdleTimer(connection)
    ensureRunning(connection)

    return {
        close: () => {
            connection.handlers.delete(handlerId)
            scheduleIdleClose(connection)
        },
    }
}

export function closeSseConnection(key: SseConnectionKey) {
    const connection = connections.get(key)
    if (!connection) return

    closeConnection(connection)
}

export function closeAllSseConnections() {
    Array.from(connections.values()).forEach(closeConnection)
}

export function sseUrl(path: string) {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
    return `${baseUrl}${path}`
}

function getConnection<T>(options: SseConnectionOptions<T>): SseConnection<T> {
    const existing = connections.get(options.key) as SseConnection<T> | undefined
    if (existing) {
        existing.url = options.url
        existing.idleTimeoutMs = options.idleTimeoutMs ?? existing.idleTimeoutMs
        return existing
    }

    const connection: SseConnection<T> = {
        key: options.key,
        url: options.url,
        controller: null,
        handlers: new Map(),
        retryMs: DEFAULT_RETRY_MS,
        idleTimeoutMs: options.idleTimeoutMs ?? NO_SUBSCRIBER_IDLE_MS,
        idleTimer: null,
        running: false,
        stopped: false,
        openedOnce: false,
        nextHandlerId: 1,
    }
    connections.set(options.key, connection as SseConnection<unknown>)
    return connection
}

function ensureRunning<T>(connection: SseConnection<T>) {
    if (connection.running) return

    connection.running = true
    void connectLoop(connection)
}

async function connectLoop<T>(connection: SseConnection<T>) {
    let retriedAfterRefresh = false

    while (!connection.stopped) {
        if (connection.handlers.size === 0
            && (connection.idleTimeoutMs === NO_SUBSCRIBER_IDLE_MS || connection.idleTimer)) {
            break
        }

        const token = localStorage.getItem(ACCESS_TOKEN_KEY)
        if (!token) {
            await delay(5000)
            continue
        }

        connection.controller = new AbortController()
        try {
            await fetchEventSource(connection.url, {
                headers: {
                    accept: 'text/event-stream',
                    Authorization: `Bearer ${token}`,
                },
                openWhenHidden: true,
                signal: connection.controller.signal,
                async onopen(response) {
                    if (response.ok) {
                        const shouldReplay = connection.openedOnce
                        retriedAfterRefresh = false
                        connection.retryMs = DEFAULT_RETRY_MS
                        connection.openedOnce = true
                        if (shouldReplay) {
                            void replayState(connection)
                        }
                        return
                    }
                    if (response.status === 401) {
                        throw new UnauthorizedSseError()
                    }
                    throw new Error(`SSE connection failed: ${response.status}`)
                },
                onmessage(event) {
                    dispatchMessage(connection, event.event, event.data)
                },
                onerror(error) {
                    if (error instanceof UnauthorizedSseError) {
                        throw error
                    }
                    throw error instanceof Error ? error : new Error(String(error))
                },
            })
            if (connection.handlers.size === 0 && connection.idleTimeoutMs !== NO_SUBSCRIBER_IDLE_MS) {
                break
            }
            await delay(1000)
        } catch (error) {
            if (connection.stopped || connection.controller.signal.aborted) {
                break
            }
            if (connection.handlers.size === 0 && connection.idleTimeoutMs !== NO_SUBSCRIBER_IDLE_MS) {
                break
            }
            if (error instanceof UnauthorizedSseError && !retriedAfterRefresh) {
                if (await refreshSession()) {
                    retriedAfterRefresh = true
                    continue
                }
                closeAllSseConnections()
                break
            }
            await delay(connection.retryMs)
            connection.retryMs = Math.min(connection.retryMs * 2, MAX_RETRY_MS)
        } finally {
            connection.controller = null
        }
    }

    connection.running = false
    if (connection.stopped || (connection.handlers.size === 0 && !connection.idleTimer)) {
        connections.delete(connection.key)
    }
}

function dispatchMessage<T>(connection: SseConnection<T>, eventName: string, data: string) {
    if (!data) return

    let payload: T
    try {
        payload = JSON.parse(data) as T
    } catch {
        return
    }
    connection.handlers.forEach((handler) => {
        if (handler.eventNames && !handler.eventNames.has(eventName)) return
        try {
            handler.onMessage(payload)
        } catch {
            // One subscriber must not break fanout for the rest of the app.
        }
    })
}

async function replayState<T>(connection: SseConnection<T>) {
    await Promise.all(Array.from(connection.handlers.values()).map(async (handler) => {
        try {
            await handler.replayOnReconnect?.()
        } catch {
            // State recovery is best effort; the SSE connection should stay alive.
        }
    }))
}

function scheduleIdleClose<T>(connection: SseConnection<T>) {
    if (connection.handlers.size > 0) return
    if (connection.idleTimeoutMs === NO_SUBSCRIBER_IDLE_MS) {
        closeConnection(connection)
        return
    }

    clearIdleTimer(connection)
    connection.idleTimer = setTimeout(() => {
        if (connection.handlers.size === 0) {
            closeConnection(connection)
        }
    }, connection.idleTimeoutMs)
}

function closeConnection<T>(connection: SseConnection<T>) {
    connection.stopped = true
    connection.handlers.clear()
    clearIdleTimer(connection)
    connection.controller?.abort()
    connections.delete(connection.key)
}

function clearIdleTimer<T>(connection: SseConnection<T>) {
    if (!connection.idleTimer) return

    clearTimeout(connection.idleTimer)
    connection.idleTimer = null
}

function delay(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms))
}
