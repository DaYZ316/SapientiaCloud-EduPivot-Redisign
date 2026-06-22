import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {EventSourceMessage, FetchEventSourceInit} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY} from '@/shared/api/request'
import {closeAllSseConnections, subscribeSse, sseUrl} from '@/shared/api/sseManager'

const mocks = vi.hoisted(() => ({
    fetchEventSource: vi.fn(),
    refreshSession: vi.fn(),
}))

vi.mock('@microsoft/fetch-event-source', () => ({
    fetchEventSource: mocks.fetchEventSource,
}))

vi.mock('@/shared/api/request', async () => {
    const actual = await vi.importActual<typeof import('@/shared/api/request')>('@/shared/api/request')
    return {
        ...actual,
        refreshSession: mocks.refreshSession,
    }
})

describe('sseManager', () => {
    beforeEach(() => {
        vi.unstubAllEnvs()
        vi.useRealTimers()
        closeAllSseConnections()
        mocks.fetchEventSource.mockReset()
        mocks.refreshSession.mockReset()
        localStorage.clear()
        localStorage.setItem(ACCESS_TOKEN_KEY, 'token')
    })

    it('shares one physical connection for multiple subscribers with the same key', async () => {
        const inits: FetchEventSourceInit[] = []
        mocks.fetchEventSource.mockImplementation(async (_url: string, options: FetchEventSourceInit) => {
            inits.push(options)
        })
        const first = vi.fn()
        const second = vi.fn()

        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: first,
        })
        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: second,
        })
        await Promise.resolve()
        inits[0].onmessage?.(message('notification', {value: 1}))

        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(1)
        expect(first).toHaveBeenCalledWith({value: 1})
        expect(second).toHaveBeenCalledWith({value: 1})
    })

    it('removes only the closed subscriber while keeping the shared stream alive', async () => {
        const inits: FetchEventSourceInit[] = []
        mocks.fetchEventSource.mockImplementation(async (_url: string, options: FetchEventSourceInit) => {
            inits.push(options)
        })
        const first = vi.fn()
        const second = vi.fn()
        const firstSubscription = subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: first,
        })
        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: second,
        })
        await Promise.resolve()

        firstSubscription.close()
        inits[0].onmessage?.(message('notification', {value: 2}))

        expect(first).not.toHaveBeenCalled()
        expect(second).toHaveBeenCalledWith({value: 2})
    })

    it('keeps idle resource streams open until the idle timeout expires', async () => {
        vi.useFakeTimers()
        let signal: AbortSignal | null | undefined
        mocks.fetchEventSource.mockImplementation(async (_url: string, options: FetchEventSourceInit) => {
            signal = options.signal
        })
        const subscription = subscribeSse<{value: number}>({
            key: 'class-barrage:1',
            url: '/api/class-sessions/1/barrages/stream',
            eventNames: ['barrage'],
            idleTimeoutMs: 30000,
            onMessage: vi.fn(),
        })
        await Promise.resolve()

        subscription.close()
        vi.advanceTimersByTime(29999)
        expect(signal?.aborted).toBe(false)
        vi.advanceTimersByTime(1)
        expect(signal?.aborted).toBe(true)
    })

    it('does not reconnect idle resource streams without subscribers', async () => {
        vi.useFakeTimers()
        mocks.fetchEventSource.mockResolvedValue(undefined)
        const subscription = subscribeSse<{value: number}>({
            key: 'class-barrage:1',
            url: '/api/class-sessions/1/barrages/stream',
            eventNames: ['barrage'],
            idleTimeoutMs: 30000,
            onMessage: vi.fn(),
        })
        await Promise.resolve()

        subscription.close()
        await vi.advanceTimersByTimeAsync(1000)

        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(1)
    })

    it('reconnects an idle resource stream when a subscriber returns before timeout', async () => {
        vi.useFakeTimers()
        mocks.fetchEventSource.mockResolvedValue(undefined)
        const subscription = subscribeSse<{value: number}>({
            key: 'class-barrage:1',
            url: '/api/class-sessions/1/barrages/stream',
            eventNames: ['barrage'],
            idleTimeoutMs: 30000,
            onMessage: vi.fn(),
        })
        await Promise.resolve()

        subscription.close()
        await vi.advanceTimersByTimeAsync(1000)
        subscribeSse<{value: number}>({
            key: 'class-barrage:1',
            url: '/api/class-sessions/1/barrages/stream',
            eventNames: ['barrage'],
            idleTimeoutMs: 30000,
            onMessage: vi.fn(),
        })
        await Promise.resolve()

        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(2)
    })

    it('refreshes the session once after a 401 response', async () => {
        mocks.refreshSession.mockResolvedValue(true)
        mocks.fetchEventSource
            .mockImplementationOnce(async (_url: string, options: FetchEventSourceInit) => {
                await options.onopen?.(new Response(null, {status: 401}))
            })
            .mockImplementationOnce(async () => undefined)

        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: vi.fn(),
        })
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()

        expect(mocks.refreshSession).toHaveBeenCalledTimes(1)
        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(2)
    })

    it('closes streams when refreshing the session after a 401 fails', async () => {
        mocks.refreshSession.mockResolvedValue(false)
        mocks.fetchEventSource.mockImplementationOnce(async (_url: string, options: FetchEventSourceInit) => {
            await options.onopen?.(new Response(null, {status: 401}))
        })

        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            onMessage: vi.fn(),
        })
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()

        expect(mocks.refreshSession).toHaveBeenCalledTimes(1)
        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(1)
    })

    it('replays state through existing query callbacks after reconnecting', async () => {
        vi.useFakeTimers()
        const replays = vi.fn()
        mocks.fetchEventSource
            .mockImplementationOnce(async (_url: string, options: FetchEventSourceInit) => {
                await options.onopen?.(new Response(null, {
                    status: 200,
                    headers: {'content-type': 'text/event-stream'},
                }))
            })
            .mockImplementationOnce(async (_url: string, options: FetchEventSourceInit) => {
                await options.onopen?.(new Response(null, {
                    status: 200,
                    headers: {'content-type': 'text/event-stream'},
                }))
            })

        subscribeSse<{value: number}>({
            key: 'notifications',
            url: '/api/notifications/subscribe',
            eventNames: ['notification'],
            replayOnReconnect: replays,
            onMessage: vi.fn(),
        })
        await Promise.resolve()
        await vi.advanceTimersByTimeAsync(1000)
        await Promise.resolve()

        expect(mocks.fetchEventSource).toHaveBeenCalledTimes(2)
        expect(replays).toHaveBeenCalledTimes(1)
    })

    it('uses VITE_API_BASE_URL when building SSE URLs', () => {
        vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:39080/')

        expect(sseUrl('/api/live-practices/subscribe')).toBe('http://localhost:39080/api/live-practices/subscribe')
    })
})

function message(event: string, payload: unknown): EventSourceMessage {
    return {
        event,
        data: JSON.stringify(payload),
        id: '',
        retry: undefined,
    }
}
