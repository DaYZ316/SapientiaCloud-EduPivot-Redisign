import {fetchEventSource} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY, refreshSession, request} from '@/shared/api/request'
import type {
  AiChatContextInfo,
  ChatRequest,
  Conversation,
  IngestDocumentRequest,
  ChatMessage,
  KnowledgeDoc,
  UpdateConversationRequest,
} from '@/features/ai/types/ai'

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

  await fetchEventSource('/api/ai/chat', {
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
