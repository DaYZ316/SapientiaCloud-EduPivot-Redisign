import {fetchEventSource} from '@microsoft/fetch-event-source'
import type {AxiosResponse} from 'axios'

import {ACCESS_TOKEN_KEY, http, refreshSession, request} from '@/shared/api/request'
import type {
    AgentSearchEvent,
    AiChatContextInfo,
    ChatMessage,
    ChatRequest,
    Conversation,
    GenerationResultEvent,
    GenerationStageEvent,
    IngestDocumentRequest,
    KnowledgeDoc,
    LiveSummaryAudioToken,
    LiveSummaryErrorEvent,
    LiveSummaryRecord,
    LiveSummarySession,
    LiveSummarySnapshot,
    LiveTranscriptSegment,
    UpdateConversationRequest,
} from '@/features/ai/types/ai'
import {type SseSubscription, sseUrl, subscribeSse} from '@/shared/api/sseManager'
import type {PageResponse} from '@/shared/types/common'

export type GeneratedArtifactExportFormat = 'pdf' | 'docx'

const UNAUTHORIZED_CODE = 40100

export interface GeneratedArtifactExportOptions {
    format: GeneratedArtifactExportFormat
    includeAnswers?: boolean
}

export interface GeneratedArtifactExportResult {
    blob: Blob
    filename: string
}

class UnauthorizedSseError extends Error {
    constructor() {
        super('SSE connection unauthorized')
        this.name = 'UnauthorizedSseError'
    }
}

export function listConversations(page = 1, size = 20) {
    return request<Conversation[]>({
        method: 'GET',
        url: '/api/ai/conversations',
        params: {page, size},
    })
}

export function listConversationMessages(conversationId: string) {
    return request<ChatMessage[]>({
        method: 'GET',
        url: `/api/ai/conversations/${conversationId}/messages`,
    })
}

export function updateConversation(conversationId: string, data: UpdateConversationRequest) {
    return request<void>({
        method: 'PATCH',
        url: `/api/ai/conversations/${conversationId}`,
        data,
    })
}

export function deleteConversation(conversationId: string) {
    return request<void>({
        method: 'DELETE',
        url: `/api/ai/conversations/${conversationId}`,
    })
}

export function terminateGeneration(conversationId: string, messageId: string) {
    return request<void>({
        method: 'POST',
        url: `/api/ai/conversations/${conversationId}/messages/${messageId}/terminate`,
        silent: true,
    })
}

export async function exportGeneratedArtifact(
    conversationId: string,
    messageId: string,
    options: GeneratedArtifactExportOptions,
): Promise<GeneratedArtifactExportResult> {
    const config = {
        method: 'GET',
        url: `/api/ai/conversations/${conversationId}/messages/${messageId}/export`,
        params: {
            format: options.format,
            includeAnswers: options.includeAnswers ?? false,
        },
        responseType: 'blob',
    } as const

    let response: AxiosResponse<Blob>
    try {
        response = await http.request<Blob>(config)
    } catch (error) {
        if (!isUnauthorizedResponse(error) || !await refreshSession()) {
            throw await normalizeExportError(error)
        }
        try {
            response = await http.request<Blob>(config)
        } catch (retryError) {
            throw await normalizeExportError(retryError)
        }
    }

    return {
        blob: response.data,
        filename: filenameFromContentDisposition(
            headerValue(response.headers, 'content-disposition'),
        ) || `generated-artifact.${options.format}`,
    }
}

export function ingestKnowledgeDoc(data: IngestDocumentRequest) {
    return request<string>({
        method: 'POST',
        url: '/api/ai/knowledge-docs',
        data,
    })
}

export function listKnowledgeDocs(page = 1, size = 20) {
    return request<KnowledgeDoc[]>({
        method: 'GET',
        url: '/api/ai/knowledge-docs',
        params: {page, size},
    })
}

export function deleteKnowledgeDoc(docId: string) {
    return request<void>({
        method: 'DELETE',
        url: `/api/ai/knowledge-docs/${docId}`,
    })
}

export function startLiveSummary(classSessionId: string) {
    return request<LiveSummarySession>({
        method: 'POST',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/start`,
        silent: true,
    })
}

export function resumeLiveSummary(classSessionId: string, summarySessionId: string) {
    return request<LiveSummarySession>({
        method: 'POST',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/resume/${summarySessionId}`,
        silent: true,
    })
}

export function stopLiveSummary(classSessionId: string) {
    return request<LiveSummarySession>({
        method: 'POST',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/stop`,
        silent: true,
    })
}

export function getLiveSummary(classSessionId: string) {
    return request<LiveSummarySession>({
        method: 'GET',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}`,
        silent: true,
    })
}

export function listLiveSummaryRecords(page = 1, size = 20) {
    return request<PageResponse<LiveSummaryRecord>>({
        method: 'GET',
        url: '/api/ai/live-summaries',
        params: {page, size},
        silent: true,
    })
}

export function listLiveSummarySnapshots(classSessionId: string) {
    return request<LiveSummarySnapshot[]>({
        method: 'GET',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/snapshots`,
        silent: true,
    })
}

export function deleteLiveSummarySnapshot(classSessionId: string, snapshotId: string) {
    return request<LiveSummarySnapshot[]>({
        method: 'DELETE',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/snapshots/${snapshotId}`,
        silent: true,
    })
}

export function deleteLiveSummaryHistoryRecord(classSessionId: string, summarySessionId: string) {
    return request<LiveSummarySnapshot[]>({
        method: 'DELETE',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/history-records/${summarySessionId}`,
        silent: true,
    })
}

export function clearLiveSummary(classSessionId: string) {
    return request<LiveSummarySession>({
        method: 'DELETE',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}`,
        silent: true,
    })
}

export function issueLiveSummaryAudioToken(classSessionId: string) {
    return request<LiveSummaryAudioToken>({
        method: 'POST',
        url: `/api/ai/live-summaries/class-sessions/${classSessionId}/audio-token`,
        silent: true,
    })
}

export function subscribeLiveSummary(
    classSessionId: string,
    handlers: {
        onStatus?: (session: LiveSummarySession) => void
        onTranscript?: (segment: LiveTranscriptSegment) => void
        onSnapshot?: (snapshot: LiveSummarySnapshot) => void
        onError?: (event: LiveSummaryErrorEvent) => void
        replayOnReconnect?: () => void | Promise<void>
    },
): SseSubscription {
    type EventPayload = LiveSummarySession | LiveTranscriptSegment | LiveSummarySnapshot | LiveSummaryErrorEvent
    return subscribeSse<EventPayload>({
        key: `ai-live-summary:${classSessionId}`,
        url: sseUrl(`/api/ai/live-summaries/class-sessions/${classSessionId}/stream`),
        eventNames: ['status', 'transcript', 'summary_snapshot', 'error'],
        idleTimeoutMs: 30000,
        replayOnReconnect: handlers.replayOnReconnect,
        onMessage(payload) {
            if (isLiveSummarySession(payload)) {
                handlers.onStatus?.(payload)
                return
            }
            if (isLiveSummarySnapshot(payload)) {
                handlers.onSnapshot?.(payload)
                return
            }
            if (isLiveTranscriptSegment(payload)) {
                handlers.onTranscript?.(payload)
                return
            }
            handlers.onError?.(payload as LiveSummaryErrorEvent)
        },
    })
}

export function buildLiveSummaryAudioSocketUrl(classSessionId: string, token: string) {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

    let url: URL
    if (baseUrl) {
        url = new URL(baseUrl)
    } else {
        url = new URL(window.location.origin)
    }
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'

    url.pathname = `/api/ai/live-summaries/class-sessions/${classSessionId}/audio`
    url.search = new URLSearchParams({sessionId: classSessionId, token}).toString()
    return url.toString()
}

export async function streamChat(
    data: ChatRequest,
    handlers: {
        onChunk: (chunk: string) => void
        onAgentSearch?: (event: AgentSearchEvent) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
        onGenerationResult?: (event: GenerationResultEvent) => void
        onContext?: (context: AiChatContextInfo) => void
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
        onError?: (error: Error) => void
        signal?: AbortSignal
    },
) {
    try {
        await connectChatStream(data, handlers)
    } catch (error) {
        if (error instanceof UnauthorizedSseError && await refreshSession()) {
            await connectChatStream(data, handlers)
            return
        }
        throw error
    }
}

export async function subscribeGenerationProgress(
    conversationId: string,
    messageId: string,
    handlers: {
        onSnapshot?: (message: ChatMessage) => void
        onAgentSearch?: (event: AgentSearchEvent) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
        onError?: (error: Error) => void
        signal?: AbortSignal
    },
) {
    try {
        await connectGenerationProgress(conversationId, messageId, handlers)
    } catch (error) {
        if (error instanceof UnauthorizedSseError && await refreshSession()) {
            await connectGenerationProgress(conversationId, messageId, handlers)
            return
        }
        throw error
    }
}

async function connectChatStream(
    data: ChatRequest,
    handlers: {
        onChunk: (chunk: string) => void
        onAgentSearch?: (event: AgentSearchEvent) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
        onGenerationResult?: (event: GenerationResultEvent) => void
        onContext?: (context: AiChatContextInfo) => void
        onConversation?: (conversation: Pick<Conversation, 'id' | 'title'>) => void
        onError?: (error: Error) => void
        signal?: AbortSignal
    },
) {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (!token) {
        throw new UnauthorizedSseError()
    }

    await fetchEventSource(apiUrl('/api/ai/chat'), {
        method: 'POST',
        headers: {
            Accept: 'text/event-stream',
            'Content-Type': 'application/json',
            'Cache-Control': 'no-cache',
            ...(token ? {Authorization: `Bearer ${token}`} : {}),
        },
        body: JSON.stringify(data),
        signal: handlers.signal,
        openWhenHidden: true,
        onmessage(event) {
            if (!event.data) {
                return
            }
            if (event.event === 'conversation') {
                handlers.onConversation?.(JSON.parse(event.data) as Pick<Conversation, 'id' | 'title'>)
            } else if (event.event === 'context') {
                handlers.onContext?.(JSON.parse(event.data) as AiChatContextInfo)
            } else if (event.event === 'agent_search') {
                handlers.onAgentSearch?.(JSON.parse(event.data) as AgentSearchEvent)
            } else if (event.event === 'generation_stage') {
                handlers.onGenerationStage?.(JSON.parse(event.data) as GenerationStageEvent)
            } else if (event.event === 'generation_result') {
                handlers.onGenerationResult?.(JSON.parse(event.data) as GenerationResultEvent)
            } else if (event.event === 'error') {
                throw new Error(event.data)
            } else if (!event.event || event.event === 'chunk') {
                handlers.onChunk(event.data)
            }
        },
        async onopen(response) {
            const contentType = response.headers.get('content-type') || ''
            if (response.ok && contentType.includes('text/event-stream')) {
                return
            }

            if (response.status === 401) {
                throw new UnauthorizedSseError()
            }

            throw new Error(await readStreamError(response))
        },
        onerror(error) {
            if (error instanceof UnauthorizedSseError) {
                throw error
            }
            const normalizedError = error instanceof Error ? error : new Error(String(error))
            handlers.onError?.(normalizedError)
            throw normalizedError
        },
    })
}

async function connectGenerationProgress(
    conversationId: string,
    messageId: string,
    handlers: {
        onSnapshot?: (message: ChatMessage) => void
        onAgentSearch?: (event: AgentSearchEvent) => void
        onGenerationStage?: (event: GenerationStageEvent) => void
        onError?: (error: Error) => void
        signal?: AbortSignal
    },
) {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (!token) {
        throw new UnauthorizedSseError()
    }

    await fetchEventSource(apiUrl(`/api/ai/conversations/${conversationId}/messages/${messageId}/generation-progress`), {
        method: 'GET',
        headers: {
            Accept: 'text/event-stream',
            'Cache-Control': 'no-cache',
            ...(token ? {Authorization: `Bearer ${token}`} : {}),
        },
        signal: handlers.signal,
        openWhenHidden: true,
        onmessage(event) {
            if (!event.data) {
                return
            }
            if (event.event === 'generation_snapshot') {
                handlers.onSnapshot?.(JSON.parse(event.data) as ChatMessage)
            } else if (event.event === 'agent_search') {
                handlers.onAgentSearch?.(JSON.parse(event.data) as AgentSearchEvent)
            } else if (event.event === 'generation_stage') {
                handlers.onGenerationStage?.(JSON.parse(event.data) as GenerationStageEvent)
            } else if (event.event === 'error') {
                throw new Error(event.data)
            }
        },
        async onopen(response) {
            const contentType = response.headers.get('content-type') || ''
            if (response.ok && contentType.includes('text/event-stream')) {
                return
            }

            if (response.status === 401) {
                throw new UnauthorizedSseError()
            }

            throw new Error(await readStreamError(response))
        },
        onerror(error) {
            if (error instanceof UnauthorizedSseError) {
                throw error
            }
            const normalizedError = error instanceof Error ? error : new Error(String(error))
            handlers.onError?.(normalizedError)
            throw normalizedError
        },
    })
}

function apiUrl(path: string) {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
    return `${baseUrl}${path}`
}

function isLiveSummarySession(payload: unknown): payload is LiveSummarySession {
    return Boolean(payload && typeof payload === 'object' && 'status' in payload && 'recentTranscripts' in payload)
}

function isLiveSummarySnapshot(payload: unknown): payload is LiveSummarySnapshot {
    return Boolean(payload && typeof payload === 'object' && 'transcriptUntilSequenceNo' in payload && 'payload' in payload)
}

function isLiveTranscriptSegment(payload: unknown): payload is LiveTranscriptSegment {
    return Boolean(payload && typeof payload === 'object' && 'text' in payload && 'final' in payload)
}

function headerValue(headers: unknown, name: string) {
    const getter = (headers as { get?: (key: string) => unknown } | null)?.get
    if (typeof getter === 'function') {
        return String(getter.call(headers, name) ?? '')
    }

    const values = headers as Record<string, unknown> | null
    return String(values?.[name] ?? values?.[name.toLowerCase()] ?? '')
}

function filenameFromContentDisposition(value: string) {
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(value)?.[1]
    if (encoded) {
        try {
            return decodeURIComponent(encoded.replace(/^"|"$/g, ''))
        } catch {
            return encoded
        }
    }

    return /filename="?([^";]+)"?/i.exec(value)?.[1] || ''
}

function isUnauthorizedResponse(error: unknown) {
    const response = (error as {
        response?: {
            status?: number
            data?: {
                code?: number
            }
        }
    }).response

    return response?.status === 401 || response?.data?.code === UNAUTHORIZED_CODE
}

async function normalizeExportError(error: unknown) {
    const message = await readExportErrorMessage(error)
    return message ? new Error(message) : error
}

async function readExportErrorMessage(error: unknown) {
    const data = (error as { response?: { data?: unknown } }).response?.data
    if (!data) return ''

    if (data instanceof Blob) {
        try {
            return errorMessageFromText(await data.text())
        } catch {
            return ''
        }
    }

    if (typeof data === 'string') {
        return errorMessageFromText(data)
    }

    if (typeof data === 'object') {
        return textValue((data as { message?: unknown }).message)
    }

    return ''
}

function errorMessageFromText(text: string) {
    const value = text.trim()
    if (!value) return ''

    try {
        const payload = JSON.parse(value) as { message?: unknown }
        return textValue(payload.message) || value
    } catch {
        return value
    }
}

function textValue(value: unknown) {
    return typeof value === 'string' && value.trim() ? value.trim() : ''
}

async function readStreamError(response: Response) {
    try {
        const payload = await response.clone().json() as { message?: string }
        if (payload.message) return payload.message
    } catch {
        // Fall back to text below.
    }

    try {
        const text = await response.text()
        if (text) return text
    } catch {
        // Fall through to generic status message.
    }

    return response.statusText || 'AI response failed'
}
