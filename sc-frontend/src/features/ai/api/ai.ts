import {fetchEventSource} from '@microsoft/fetch-event-source'

import {ACCESS_TOKEN_KEY, request} from '@/shared/api/request'
import type {
  ChatRequest,
  Conversation,
  CreateConversationRequest,
  IngestDocumentRequest,
  ChatMessage,
  KnowledgeDoc,
  UpdateConversationRequest,
} from '@/features/ai/types/ai'

export function createConversation(data: CreateConversationRequest) {
  return request<string>({
    method: 'POST',
    url: '/api/ai/conversations',
    data,
  })
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
    onError?: (error: Error) => void
    signal?: AbortSignal
  },
) {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)

  await fetchEventSource('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
    },
    body: JSON.stringify(data),
    signal: handlers.signal,
    openWhenHidden: true,
    onmessage(event) {
      if (event.data) {
        handlers.onChunk(event.data)
      }
    },
    onerror(error) {
      const normalizedError = error instanceof Error ? error : new Error(String(error))
      handlers.onError?.(normalizedError)
      throw normalizedError
    },
  })
}
