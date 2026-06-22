import {fetchEventSource} from '@microsoft/fetch-event-source'
import type {AxiosResponse} from 'axios'

import {ACCESS_TOKEN_KEY, http, refreshSession, request} from '@/shared/api/request'
import type {
  AgentSearchEvent,
  AiChatContextInfo,
  ChatRequest,
  Conversation,
  GenerationResultEvent,
  GenerationStageEvent,
  IngestDocumentRequest,
  ChatMessage,
  KnowledgeDoc,
  UpdateConversationRequest,
} from '@/features/ai/types/ai'

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
      throw error
    }
    response = await http.request<Blob>(config)
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

function apiUrl(path: string) {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  return `${baseUrl}${path}`
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

async function readStreamError(response: Response) {
  try {
    const payload = await response.clone().json() as {message?: string}
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
